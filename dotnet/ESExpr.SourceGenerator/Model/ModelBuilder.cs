// Closed hierarchy APIs are experimental in the .NET 11 RC2 Roslyn release.
#pragma warning disable RSEXPERIMENTAL006

using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Globalization;
using System.Linq;
using System.Numerics;
using ESExpr.SourceGenerator.TypeClass;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static ESExpr.SourceGenerator.GenUtils;
using static ESExpr.SourceGenerator.NameUtils;

namespace ESExpr.SourceGenerator.Model;

internal class ModelBuilder {

	public ModelBuilder(Compilation compilation, GeneratorAttributeSyntaxContext context, GenerationDiagnostics diagnostics) {
		this.compilation = compilation;
		this.diagnostics = diagnostics;
		this.context = context;
	}

	private readonly GenerationDiagnostics diagnostics;
	private readonly Compilation compilation;
	private readonly GeneratorAttributeSyntaxContext context;
	private TypeClassResolver? resolverValue;
	private TypeClassResolver resolver => resolverValue ??= new TypeClassResolver(compilation,
		diagnostics.Recover(((BaseTypeDeclarationSyntax)context.TargetNode).Identifier.GetLocation(), () => BuildLocalScope(context), new List<LocalInfo>()));

	public ITypeSourceModel? CreateSourceModel(BaseTypeDeclarationSyntax decl) {
		if(decl.Parent is not (BaseNamespaceDeclarationSyntax or CompilationUnitSyntax)) {
			return new InvalidTypeSourceModel {
				Descriptor = Errors.InvalidNestedESExprType,
				Location = decl.Identifier.GetLocation(),
				MessageArgs = [decl.Identifier.ToString()],
			};
		}

		switch(decl) {
			case EnumDeclarationSyntax enumDecl:
				return CreateSimpleEnumSourceModel(enumDecl);

			case RecordDeclarationSyntax recordDecl when recordDecl.Modifiers.Any(SyntaxKind.SealedKeyword):
				if((recordDecl.ParameterList?.Parameters.Count ?? 0) != 0) {
					return new InvalidTypeSourceModel {
						Descriptor = Errors.InvalidESExprRecordDeclaration,
						Location = decl.Identifier.GetLocation(),
						MessageArgs = [decl.Identifier.ToString()],
					};
				}

				var codecAttr = GetAttribute(recordDecl, "ESExpr.Runtime.ESExprCodecAttribute", context.SemanticModel);

				if(
					codecAttr?.ArgumentList?.Arguments
						.Any(arg =>
							arg.NameEquals?.Name.ToString() == "Flags" &&
							arg.Expression is LiteralExpressionSyntax { Token.Value: true }
						)
					?? false
				) {
					return CreateFlagsSourceModel(recordDecl);
				}
				else {
					return CreateRecordSourceModel(recordDecl);
				}


			case RecordDeclarationSyntax recordDecl when recordDecl.Modifiers.Any(SyntaxKind.ClosedKeyword):
				if(!IsValidEnumRecord(recordDecl)) {
					return new InvalidTypeSourceModel {
						Descriptor = Errors.InvalidESExprEnumDeclaration,
						Location = decl.Identifier.GetLocation(),
						MessageArgs = [decl.Identifier.ToString()],
					};
				}

				return CreateUnionRecordSourceModel(recordDecl);

			default:
				return new InvalidTypeSourceModel {
					Descriptor = Errors.InvalidESExprTypeDeclaration,
					Location = decl.Identifier.GetLocation(),
					MessageArgs = [decl.Identifier.ToString()],
				};
		}
	}

	private UnionRecordSourceModel CreateUnionRecordSourceModel(RecordDeclarationSyntax recordDecl) {
		return new UnionRecordSourceModel {
			Usings = GetUsings(recordDecl),
			Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
			TypeName = recordDecl.Identifier.ToString(),
			Location = recordDecl.Identifier.GetLocation(),
			TypeParameters = GetTypeParameters(recordDecl),
			TypeClassInstanceAccessor = diagnostics.Recover(recordDecl.Identifier.GetLocation(), () => BuildInstanceAccessor(recordDecl), new SourceModelTypeClassInstanceAccessor { Name = "Codec", IsProperty = true, Parameters = [] }),
			Tags = GetDeclarationTags(recordDecl),
			Cases = recordDecl.Members
				.OfType<RecordDeclarationSyntax>()
				.Where(caseDecl => caseDecl.Modifiers.Any(SyntaxKind.PublicKeyword))
				.Select(caseDecl => new SourceModelEnumCase {
					Name = caseDecl.Identifier.ToString(),
					Location = caseDecl.Identifier.GetLocation(),
					ConstructorName = ConstructorName(caseDecl),
					IsInlineValue = IsInlineValue(caseDecl),
					Fields = GetFields(caseDecl),
				})
				.ToImmutableList(),
		};
	}

	private RecordSourceModel CreateRecordSourceModel(RecordDeclarationSyntax recordDecl) {
		return new RecordSourceModel {
			Usings = GetUsings(recordDecl),
			Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
			TypeName = recordDecl.Identifier.ToString(),
			Location = recordDecl.Identifier.GetLocation(),
			ConstructorName = ConstructorName(recordDecl),
			TypeParameters = GetTypeParameters(recordDecl),
			TypeClassInstanceAccessor = diagnostics.Recover(recordDecl.Identifier.GetLocation(), () => BuildInstanceAccessor(recordDecl), new SourceModelTypeClassInstanceAccessor { Name = "Codec", IsProperty = true, Parameters = [] }),
			Tags = GetDeclarationTags(recordDecl),
			Fields = GetFields(recordDecl),
		};
	}

	private FlagsSourceModel CreateFlagsSourceModel(RecordDeclarationSyntax recordDecl) {
		return new FlagsSourceModel {
			Usings = GetUsings(recordDecl),
			Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
			TypeName = recordDecl.Identifier.ToString(),
			Location = recordDecl.Identifier.GetLocation(),
			TypeParameters = GetTypeParameters(recordDecl),
			Fields = GetFlagsFields(recordDecl, context.SemanticModel),
			TypeClassInstanceAccessor = diagnostics.Recover(recordDecl.Identifier.GetLocation(), () => BuildInstanceAccessor(recordDecl), new SourceModelTypeClassInstanceAccessor { Name = "Codec", IsProperty = true, Parameters = [] }),
		};
	}

	private SimpleEnumSourceModel CreateSimpleEnumSourceModel(EnumDeclarationSyntax enumDecl) {
		return new SimpleEnumSourceModel {
			Usings = GetUsings(enumDecl),
			Namespace = GetNamespaceFromNamespaceNodes(enumDecl.Parent),
			TypeName = enumDecl.Identifier.ToString(),
			Location = enumDecl.Identifier.GetLocation(),
			Cases = enumDecl.Members
				.Select(m => new SourceModelSimpleEnumCase {
					Name = m.Identifier.ToString(),
					Location = m.Identifier.GetLocation(),
					ConstructorName = ConstructorName(m),
				})
				.ToImmutableList(),
		};
	}

	private SourceModelTypeClassInstanceAccessor BuildInstanceAccessor(RecordDeclarationSyntax recordDecl) {
		var accessor = context.SemanticModel.GetDeclaredSymbol(recordDecl);
		if(accessor is null) {
			goto notFound;
		}

		var member = GetTypeClassInstanceMember(accessor);
		if(member is null) {
			goto notFound;
		}

		return new SourceModelTypeClassInstanceAccessor {
			Name = member.Name,
			IsProperty = member is IPropertySymbol,
			Parameters = member is IMethodSymbol method
				? method.Parameters.Select(p => new SourceModelTypeClassInstanceAccessorParameter {
					Name = p.Name,
					Type = SourceModelType.FromSymbol(p.Type),
				}).ToImmutableList()
				: [],
		};

	notFound:
		throw new InputDiagnosticException(Errors.MissingRuntimeMember, "Unable to find TypeClassInstance method or property for " + recordDecl.Identifier.ToString());
	}

	private static bool IsValidEnumRecord(RecordDeclarationSyntax decl) {
		if((decl.ParameterList?.Parameters.Count ?? 0) != 0) {
			return false;
		}

		var constructors = decl.Members
			.OfType<ConstructorDeclarationSyntax>()
			.Take(2)
			.ToList();

		if(constructors.Count == 0) {
			return true;
		}

		if(constructors.Count != 1) {
			return false;
		}

		return constructors.Any(ctor =>
			ctor.Modifiers.Any(SyntaxKind.PrivateKeyword) &&
				ctor.ParameterList.Parameters.Count == 0
		);
	}



	private static ImmutableList<string> GetNamespaceFromNamespaceNodes(SyntaxNode? syntax) {
		var ns = ImmutableList.CreateBuilder<string>();

		void AddFromNode(SyntaxNode? syntax) {
			if(syntax is not BaseNamespaceDeclarationSyntax ns) {
				return;
			}

			AddFromNode(ns.Parent);
			AddNames(ns.Name);
		}

		void AddNames(NameSyntax name) {
			switch(name) {
				case QualifiedNameSyntax qn:
					AddNames(qn.Left);
					ns.Add(qn.Right.ToString());
					break;

				case SimpleNameSyntax sn:
					ns.Add(sn.ToString());
					break;

				default:
					throw new ArgumentException(
						"The NameSyntax must be a SimpleNameSyntax or QualifiedNameSyntax.",
						nameof(name)
					);
			}
		}

		AddFromNode(syntax);
		return ns.ToImmutable();
	}

	private static ImmutableList<SourceModelSyntax<UsingDirectiveSyntax>> GetUsings(BaseTypeDeclarationSyntax decl) {
		return decl.SyntaxTree.GetCompilationUnitRoot().Usings
			.Select(u => new SourceModelSyntax<UsingDirectiveSyntax>(u))
			.ToImmutableList();
	}

	private static ImmutableList<SourceModelSyntax<TypeParameterSyntax>> GetTypeParameters(TypeDeclarationSyntax decl) {
		var typeParams = decl.TypeParameterList;
		if(typeParams is null) {
			return [];
		}

		return typeParams.Parameters
			.Select(tp => new SourceModelSyntax<TypeParameterSyntax>(tp))
			.ToImmutableList();
	}

	private ImmutableList<SourceModelField> GetFields(TypeDeclarationSyntax decl) =>
		decl.Members
			.OfType<PropertyDeclarationSyntax>()
			.Where(p => !p.Modifiers.Any(SyntaxKind.StaticKeyword))
			.Select(prop => {
				var field = diagnostics.Recover<SourceModelField?>(prop.Identifier.GetLocation(), () => {
					var t = context.SemanticModel.GetTypeInfo(prop.Type).Type;
					if(t is null) {
						throw new InvalidOperationException("Could not get type of field");
					}

					// Validate representability before asking the resolver for a codec.
					var sourceType = SourceModelType.FromSymbol(t);
					SourceModelField.FieldMode mode;
					if(IsOptional(prop)) {
						var optionalValueCodecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IOptionalValueCodec`2");
						if(optionalValueCodecType is null) {
							throw new InputDiagnosticException(Errors.MissingRuntimeMember, "Could not find IOptionalValueCodec<T, TValue> type");
						}

						var codecTypeLookup = optionalValueCodecType.Construct(t, compilation.DynamicType);
						var res = resolver.Resolve(codecTypeLookup);
						if(res is null) {
							throw new InputDiagnosticException(Errors.CouldNotDetermineCodec, "optional value codec", t.ToDisplayString());
						}

						var codecType = resolver.GetTypeClassType(res);
						if(codecType is not INamedTypeSymbol { TypeArguments.Length: 2 } namedCodec || namedCodec.TypeArguments[1].TypeKind is TypeKind.Dynamic or TypeKind.Error)
							throw new InputDiagnosticException(Errors.ElementCodec, codecType.ToDisplayString());
						var elementType = namedCodec.TypeArguments[1];

						mode = new SourceModelField.FieldMode.Optional {
							OptionalValueCodecInstance = BuildTypeClassInstanceModel(res),
							ElementType = SourceModelType.FromSymbol(elementType),
							KeywordMode = IsKeyword(prop) is { } keyword
								? new SourceModelField.KeywordMode.Keyword {
									KeywordName = keyword,
								}
								: new SourceModelField.KeywordMode.Positional {
									Tags = GetTagsFor(elementType),
								},
						};
					}
					else if(IsDict(prop)) {
						var dictCodecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IDictCodec`2");
						if(dictCodecType is null) {
							throw new InputDiagnosticException(Errors.MissingRuntimeMember, "Could not find IDictCodec<T, TValue> type");
						}

						var codecTypeLookup = dictCodecType.Construct(t, compilation.DynamicType);
						var res = resolver.Resolve(codecTypeLookup);
						if(res is null) {
							throw new InputDiagnosticException(Errors.CouldNotDetermineCodec, "dict codec", t.ToDisplayString());
						}

						var codecType = resolver.GetTypeClassType(res);
						if(codecType is not INamedTypeSymbol { TypeArguments.Length: 2 } namedCodec || namedCodec.TypeArguments[1].TypeKind is TypeKind.Dynamic or TypeKind.Error)
							throw new InputDiagnosticException(Errors.ElementCodec, codecType.ToDisplayString());
						var elementType = namedCodec.TypeArguments[1];

						mode = new SourceModelField.FieldMode.Dict {
							DictCodecInstance = BuildTypeClassInstanceModel(res),
							ElementType = SourceModelType.FromSymbol(elementType),
						};
					}
					else if(IsVararg(prop)) {
						var varargCodecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IVarargCodec`2");
						if(varargCodecType is null) {
							throw new InputDiagnosticException(Errors.MissingRuntimeMember, "Could not find IVarargCodec<T> type");
						}

						var codecTypeLookup = varargCodecType.Construct(t, compilation.DynamicType);
						var res = resolver.Resolve(codecTypeLookup);
						if(res is null) {
							throw new InputDiagnosticException(Errors.CouldNotDetermineCodec, "vararg codec", t.ToDisplayString());
						}

						var codecType = resolver.GetTypeClassType(res);
						if(codecType is not INamedTypeSymbol { TypeArguments.Length: 2 } namedCodec || namedCodec.TypeArguments[1].TypeKind is TypeKind.Dynamic or TypeKind.Error)
							throw new InputDiagnosticException(Errors.ElementCodec, codecType.ToDisplayString());
						var elementType = namedCodec.TypeArguments[1];

						mode = new SourceModelField.FieldMode.Vararg {
							ElementTags = GetTagsFor(elementType),
							ElementType = SourceModelType.FromSymbol(elementType),
							VarargCodecInstance = BuildTypeClassInstanceModel(res),
						};
					}
					else {
						var codecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IESExprCodec`1");
						if(codecType is null) {
							throw new InputDiagnosticException(Errors.MissingRuntimeMember, "Could not find IESExprCodec<T> type");
						}

						var lookupType = codecType.Construct(t);

						var res = resolver.Resolve(lookupType);
						if(res is null) {
							throw new InputDiagnosticException(Errors.CouldNotDetermineCodec, "codec", t.ToDisplayString());
						}



						mode = new SourceModelField.FieldMode.Normal {
							CodecInstance = BuildTypeClassInstanceModel(res),

							DefaultValue = IsDefaultValue(prop) is { } defaultValue
								? new SourceModelSyntax<ExpressionSyntax>(defaultValue)
								: null,

							KeywordMode = IsKeyword(prop) is { } keyword
								? new SourceModelField.KeywordMode.Keyword {
									KeywordName = keyword,
								}
								: new SourceModelField.KeywordMode.Positional {
									Tags = GetTagsForCodec(t, res, ImmutableHashSet.Create<ITypeSymbol>(SymbolEqualityComparer.Default, t)),
								},

						};
					}

					return new SourceModelField {
						Name = prop.Identifier.ToString(),
						Location = prop.Identifier.GetLocation(),
						Type = sourceType,
						Mode = mode,
					};

				}, null);
				if(field is null) diagnostics.FailedFields.Add(prop.Identifier.GetLocation());
				return field;
			})
			.OfType<SourceModelField>()
			.ToImmutableList();

	private SourceModelTypeClassInstance BuildTypeClassInstanceModel(TypeClassResult res) {
		return res switch {
			TypeClassResult.Local { LocalInfo: var local } => new SourceModelTypeClassInstance.Local {
				Name = local.Name,
			},
			TypeClassResult.Method method => new SourceModelTypeClassInstance.Method {
				TypeArguments = method.TypeArguments.Select(SourceModelType.FromSymbol).ToImmutableList(),
				Arguments = method.Arguments.Select(BuildTypeClassInstanceModel).ToImmutableList(),
				DeclaringType = SourceModelType.FromSymbol(method.MethodSymbol.ContainingType),
				Name = method.MethodSymbol.Name,
			},
			TypeClassResult.Property { PropertySymbol: var property } => new SourceModelTypeClassInstance.Property {
				DeclaringType = SourceModelType.FromSymbol(property.ContainingType),
				Name = property.Name,
			},
		};
	}

	private ESExprTagSet GetDeclarationTags(RecordDeclarationSyntax decl) {
		var location = decl.Identifier.GetLocation();
		if(diagnostics.Diagnostics.Any(d => d.Descriptor == Errors.MissingRuntimeMember && d.Location.Equals(location))) return ESExprTagSet.All;
		return diagnostics.Recover(location, () => GetTagsFor(context.SemanticModel.GetDeclaredSymbol(decl)
			?? throw new InvalidOperationException("Could not find declaration symbol")), ESExprTagSet.All);
	}

	private ESExprTagSet GetTagsFor(ITypeSymbol type) {
		return GetTagsFor(type, ImmutableHashSet.Create<ITypeSymbol>(SymbolEqualityComparer.Default));
	}

	private ESExprTagSet GetTagsFor(ITypeSymbol type, ImmutableHashSet<ITypeSymbol> seenTypes) {
		if(seenTypes.Contains(type)) {
			return ESExprTagSet.All;
		}

		var codecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IESExprCodec`1");
		if(codecType is null) {
			throw new InputDiagnosticException(Errors.MissingRuntimeMember, "Could not find IESExprCodec<T> type");
		}

		var lookupType = codecType.Construct(type);

		var res = resolver.Resolve(lookupType);
		if(res is null) {
			throw new InputDiagnosticException(Errors.CouldNotDetermineCodec, "codec", type.ToDisplayString());
		}

		return GetTagsForCodec(type, res, seenTypes.Add(type));
	}

	private ESExprTagSet GetTagsForCodec(ITypeSymbol type, TypeClassResult res, ImmutableHashSet<ITypeSymbol> seenTypes) {
		return res switch {
			TypeClassResult.Local => ESExprTagSet.All,
			TypeClassResult.Method method => GetTagsForMember(
				type,
				method.MethodSymbol,
				method.MethodSymbol.TypeParameters.Zip(method.TypeArguments, (tp, arg) => (tp, arg))
					.ToDictionary<(ITypeParameterSymbol tp, ITypeSymbol arg), ITypeParameterSymbol, ITypeSymbol>(
						pair => pair.tp,
						pair => pair.arg,
						SymbolEqualityComparer.Default
					),
				seenTypes
			),
			TypeClassResult.Property property => GetTagsForMember(type, property.PropertySymbol, new(SymbolEqualityComparer.Default), seenTypes),
		};
	}

	internal record TypeTags(ESExprTagSet tags, ImmutableList<ITypeSymbol> unionWithTypes);

	private ESExprTagSet GetTagsForMember(ITypeSymbol type, ISymbol symbol, Dictionary<ITypeParameterSymbol, ITypeSymbol> typeParams, ImmutableHashSet<ITypeSymbol> seenTypes) {
		TypeTags? typeTags;
		try { typeTags = LoadESExprTagsFromAttr(symbol, typeParams) ?? LoadIntrinsicTags(type, typeParams, seenTypes); }
		catch(InputDiagnosticException ex) {
			throw new InputDiagnosticException(ex.Descriptor, ex.Arguments.Select(a => (object)$"{a} (provider: {symbol.ToDisplayString()})").ToArray()) {
				SourceLocation = ex.SourceLocation ?? GetAttribute(symbol, "ESExpr.Runtime.ESExprTagsAttribute")?.ApplicationSyntaxReference?.GetSyntax().GetLocation() ?? diagnostics.Location,
			};
		}
		if(typeTags is null) {
			throw new InputDiagnosticException(Errors.MissingTagsAttribute, symbol.ToDisplayString(), type.ToDisplayString());
		}

		var tags = typeTags.tags;
		foreach(var unionWithType in typeTags.unionWithTypes) {
			tags = tags.Union(GetTagsFor(unionWithType, seenTypes));
		}

		return tags;
	}



	private static TypeTags? LoadESExprTagsFromAttr(ISymbol symbol, Dictionary<ITypeParameterSymbol, ITypeSymbol> typeParams) {
		ESExprTagSet tags = ESExprTagSet.Empty;
		ImmutableList<ITypeSymbol> unionWithTypes = [];

		var tagsAttr = GetAttribute(symbol, "ESExpr.Runtime.ESExprTagsAttribute");
		if(tagsAttr is null) {
			return null;
		}

		TypedConstant? GetAttributeField(string name) =>
			tagsAttr.NamedArguments
				.Where(kvp => kvp.Key == name)
				.Select(kvp => new TypedConstant?(kvp.Value))
				.FirstOrDefault();

		if(GetAttributeField("Scalar") is { } scalars) {
			ValidateTagArray(scalars);
			foreach(var scalar in scalars.Values) {
				if(scalar.Value is not int intValue) {
					throw new InputDiagnosticException(Errors.MalformedMetadata, "Expected int enum value for scalar tag");
				}

				var enumValue = (ESExprTag.ScalarType)intValue;

				tags = tags.Add(enumValue switch {
					ESExprTag.ScalarType.Bool => new ESExprTag.Bool(),
					ESExprTag.ScalarType.Int => new ESExprTag.Int(),
					ESExprTag.ScalarType.Str => new ESExprTag.Str(),
					ESExprTag.ScalarType.Float16 => new ESExprTag.Float16(),
					ESExprTag.ScalarType.Float32 => new ESExprTag.Float32(),
					ESExprTag.ScalarType.Float64 => new ESExprTag.Float64(),
					ESExprTag.ScalarType.Array8 => new ESExprTag.Array8(),
					ESExprTag.ScalarType.Array16 => new ESExprTag.Array16(),
					ESExprTag.ScalarType.Array32 => new ESExprTag.Array32(),
					ESExprTag.ScalarType.Array64 => new ESExprTag.Array64(),
					ESExprTag.ScalarType.Array128 => new ESExprTag.Array128(),
					ESExprTag.ScalarType.Null => new ESExprTag.Null(),
					_ => throw new InputDiagnosticException(Errors.MalformedMetadata, "Unknown enum value: " + (int)enumValue),
				});
			}
		}

		if(GetAttributeField("Constructors") is { } constructors) {
			ValidateTagArray(constructors);
			foreach(var constructor in constructors.Values) {
				if(constructor.Value is not string s) {
					throw new InputDiagnosticException(Errors.MalformedMetadata, "Expected string value for constructor tag");
				}

				tags = tags.Add(new ESExprTag.Constructor(s));
			}
		}

		if(GetAttributeField("All") is { } all) {
			if(all.Value is not bool b) {
				throw new InputDiagnosticException(Errors.MalformedMetadata, "Expected bool value for all tag");
			}

			if(b) {
				tags = ESExprTagSet.All;
			}
		}

		if(GetAttributeField("UnionWithTypeParameters") is { } tps) {
			ValidateTagArray(tps);
			unionWithTypes = tps.Values
				.Select(tp => {
					if(tp.Value is not string tpName) {
						throw new InputDiagnosticException(Errors.MalformedMetadata, "Expected string value for constructor tag");
					}

					var typeArg = typeParams.Where(kvp => kvp.Key.Name == tpName)
						.Select(kvp => kvp.Value)
						.FirstOrDefault();

					if(typeArg is null) {
						throw new InputDiagnosticException(Errors.MalformedMetadata, "Unknown type parameter " + tpName);
					}

					return typeArg;
				})
				.ToImmutableList();
		}

		return new TypeTags(tags, unionWithTypes);
	}

	private TypeTags? LoadIntrinsicTags(ITypeSymbol t, Dictionary<ITypeParameterSymbol, ITypeSymbol> typeParams, ImmutableHashSet<ITypeSymbol> seenTypes) {
		if(!HasAttribute(t, "ESExpr.Runtime.ESExprCodecAttribute")) {
			return null;
		}

		if(t.TypeKind == TypeKind.Enum) {
			return new TypeTags(ESExprTagSet.Create([new ESExprTag.Str()]), []);
		}
		else if(t.IsRecord && t.IsSealed) {
			if(
				GetAttribute(t, "ESExpr.Runtime.ESExprCodecAttribute") is { } codecAttr &&
				codecAttr.NamedArguments.FirstOrDefault(kvp => kvp.Key == "Flags") is { Value: { Value: true } }
			) {
				return new TypeTags(ESExprTagSet.Create([new ESExprTag.Int()]), []);
			}
			else {
				var constructorName = GetConstructorName((INamedTypeSymbol)t);
				return new TypeTags(ESExprTagSet.Create([new ESExprTag.Constructor(constructorName)]), []);
			}
		}
		else if(t.IsRecord && t.IsClosed) {
			var tags = ESExprTagSet.Empty;
			var unionWithTypes = ImmutableList.CreateBuilder<ITypeSymbol>();
			var substitutions = new Dictionary<ITypeParameterSymbol, ITypeSymbol>(typeParams, SymbolEqualityComparer.Default);
			if(t is INamedTypeSymbol unionType) {
				foreach(var (parameter, argument) in unionType.ConstructedFrom.TypeParameters.Zip(unionType.TypeArguments, (parameter, argument) => (parameter, argument))) {
					substitutions[parameter] = argument;
				}
			}

			var cases = t.GetTypeMembers()
				.Where(caseRec =>
					caseRec.IsRecord &&
					caseRec.DeclaredAccessibility == Accessibility.Public
				);

			foreach(var c in cases) {
				if(HasAttribute(c, "ESExpr.Runtime.InlineValueAttribute")) {
					var fields = c.GetMembers().OfType<IPropertySymbol>().Where(p => !p.IsStatic && !p.IsImplicitlyDeclared).ToList();

					if(fields.Count != 1) {
						diagnostics.Report(Diagnostic.Create(Errors.InvalidInlineValue, c.Locations.FirstOrDefault(l => l.IsInSource) ?? diagnostics.Location));
						continue;
					}

					var field = fields[0];

					var substType = resolver.SubstituteTypes(field.Type, substitutions);
					if(substType is null) {
						throw new InvalidOperationException($"Unable to substitute type parameters in {field.Type}");
					}

					tags = tags.Union(diagnostics.Recover(field.Locations.FirstOrDefault(l => l.IsInSource) ?? diagnostics.Location,
						() => GetTagsFor(substType, seenTypes), ESExprTagSet.All));
				}
				else {
					tags = tags.Add(new ESExprTag.Constructor(GetConstructorName(c)));
				}
			}

			return new TypeTags(
				tags,
				unionWithTypes.ToImmutable()
			);
		}
		else {
			return null;
		}
	}



	private ImmutableList<SourceModelFlagsField> GetFlagsFields(TypeDeclarationSyntax decl, SemanticModel semanticModel) =>
		decl.Members
			.OfType<PropertyDeclarationSyntax>()
			.Where(prop => !prop.Modifiers.Any(SyntaxKind.StaticKeyword))
			.Select(prop => diagnostics.Recover<SourceModelFlagsField?>(prop.Identifier.GetLocation(), () => {
				var t = ModelExtensions.GetTypeInfo(semanticModel, prop.Type).Type;
				if(t is null) {
					throw new InvalidOperationException("Could not get type of field");
				}

				var declType = ModelExtensions.GetDeclaredSymbol(semanticModel, decl);
				if(declType is null) {
					throw new InvalidOperationException("Could not get declared symbol for flags type declaration");
				}

				SourceModelFlagsField field;

				if(t.SpecialType == SpecialType.System_Boolean) {
					var flagBitsAttr = GetAttribute(prop, "ESExpr.Runtime.FlagBitsAttribute", semanticModel);
					if(flagBitsAttr is not { ArgumentList.Arguments: var args } || args.Count != 1) {
						throw new InputDiagnosticException(Errors.InvalidFlag, "Flag must specify FlagBitsAttribute with exactly one constructor argument");
					}

					var maskExpr = args[0].Expression;
					var maskValue = semanticModel.GetConstantValue(maskExpr);
					if(!maskValue.HasValue) {
						throw new InputDiagnosticException(Errors.InvalidFlag, "FlagBitsAttribute mask must be a constant value");
					}

					BigInteger mask = maskValue.Value switch {
						string s => ParseMask(s),
						byte b => b,
						sbyte sb => sb,
						short s2 => s2,
						ushort us => us,
						int i => i,
						uint ui => ui,
						long l => l,
						ulong ul => ul,
						_ => throw new InputDiagnosticException(Errors.InvalidFlag, "FlagBitsAttribute mask must be a ulong or string"),
					};

					if(mask < 0) {
						throw new InputDiagnosticException(Errors.InvalidFlag, "FlagBitsAttribute mask must be non-negative");
					}

					field = new SourceModelFlagsFieldFlag {
						Name = prop.Identifier.ToString(),
						Location = prop.Identifier.GetLocation(),
						Mask = mask,
					};
				}
				else if(t is INamedTypeSymbol { EnumUnderlyingType: not null } enumType) {
					field = new SourceModelFlagsFieldEnum {
						Name = prop.Identifier.ToString(),
						Location = prop.Identifier.GetLocation(),
						Type = SourceModelType.FromSymbol(t),
						Cases = enumType.GetMembers()
							.OfType<IFieldSymbol>()
							.Where(f => f.IsConst)
							.Select(f => diagnostics.Recover<SourceModelFlagsEnumCase?>(f.Locations.FirstOrDefault(l => l.IsInSource) ?? prop.Identifier.GetLocation(), () => {
								var attr = GetAttribute(f, "ESExpr.Runtime.FlagBitsAttribute");

								if(attr is null) {
									throw new InputDiagnosticException(Errors.InvalidFlag, $"Enum field must be marked with FlagBitsAttribute (enum member: {f.ToDisplayString()})");
								}

								if(attr.ConstructorArguments.Length != 1) {
									throw new InputDiagnosticException(Errors.InvalidFlag, $"FlagBitsAttribute must have exactly one constructor argument (enum member: {f.ToDisplayString()})");
								}

								BigInteger value = attr.ConstructorArguments[0].Value switch {
									string s => ParseMask(s, f.ToDisplayString()),
									byte b => b,
									sbyte sb => sb,
									short s2 => s2,
									ushort us => us,
									int i => i,
									uint ui => ui,
									long l => l,
									ulong ul => ul,
									_ => throw new InputDiagnosticException(Errors.InvalidFlag, $"FlagBitsAttribute value must be a ulong or string (enum member: {f.ToDisplayString()})"),
								};

								if(value < 0) {
									throw new InputDiagnosticException(Errors.InvalidFlag, $"FlagBitsAttribute value must be non-negative (enum member: {f.ToDisplayString()})");
								}

								return new SourceModelFlagsEnumCase {
									Name = f.Name,
									Value = value,
								};
							}, null))
							.OfType<SourceModelFlagsEnumCase>()
							.ToImmutableList(),
					};
				}
				else {
					throw new InputDiagnosticException(Errors.InvalidFlag, "Flag field type must be a bool or enum defined as a nested type.");
				}

				return field;
			}, null))
			.OfType<SourceModelFlagsField>()
			.ToImmutableList();

	private string ConstructorName(MemberDeclarationSyntax decl) => diagnostics.Recover(decl.GetLocation(), () => {
		var attr = GetAttribute(decl, "ESExpr.Runtime.ConstructorAttribute", context.SemanticModel);
		if(attr is { ArgumentList.Arguments.Count: 1 } && attr.ArgumentList.Arguments[0].Expression is not LiteralExpressionSyntax { Token.Value: string })
			throw new InputDiagnosticException(Errors.MalformedMetadata, "ConstructorAttribute must have a string literal argument");
		return decl switch {
			TypeDeclarationSyntax t => GetConstructorName(t, context.SemanticModel),
			EnumMemberDeclarationSyntax m => GetConstructorName(m, context.SemanticModel),
			_ => throw new InvalidOperationException(),
		};
	}, null!);

	private static BigInteger ParseMask(string text, string? provider = null) {
		if(!BigInteger.TryParse(text, NumberStyles.Integer, CultureInfo.InvariantCulture, out var mask))
			throw new InputDiagnosticException(Errors.InvalidFlag, $"FlagBitsAttribute mask must be a valid integer string{(provider is null ? "" : " (enum member: " + provider + ")")}");
		return mask;
	}

	private static void ValidateTagArray(TypedConstant value) {
		if(value.Kind != TypedConstantKind.Array || value.IsNull)
			throw new InputDiagnosticException(Errors.MalformedMetadata, "Tag metadata must be a non-null array");
	}

	private bool IsVararg(PropertyDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.VarargAttribute", context.SemanticModel);

	private bool IsDict(PropertyDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.DictAttribute", context.SemanticModel);

	private bool IsOptional(PropertyDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.OptionalAttribute", context.SemanticModel);

	private ExpressionSyntax? IsDefaultValue(PropertyDeclarationSyntax decl) {
		var attr = GetAttribute(decl, "ESExpr.Runtime.DefaultValueAttribute", context.SemanticModel);
		if(
			attr is { ArgumentList.Arguments: var args } &&
			args.Count == 1 &&
			args[0].Expression is LiteralExpressionSyntax value
		) {
			return SyntaxFactory.ParseExpression(value.Token.ValueText);
		}
		else if(decl.Initializer is { Value: var initValue }) {
			return initValue;
		}
		else {
			return null;
		}
	}

	private string? IsKeyword(PropertyDeclarationSyntax decl) {
		var attr = GetAttribute(decl, "ESExpr.Runtime.KeywordAttribute", context.SemanticModel);
		if(attr == null) {
			return null;
		}

		if(
			attr is { ArgumentList.Arguments: var args } &&
			args.Count == 1
		) {
			if(args[0].Expression is not LiteralExpressionSyntax { Token.Value: string } value) {
				throw new InputDiagnosticException(Errors.MalformedMetadata, "KeywordAttribute must have a string literal argument");
			}

			return value.Token.ValueText;
		}
		else {
			return NameToKebabCase(decl.Identifier.Text);
		}
	}

	private bool IsInlineValue(RecordDeclarationSyntax decl) =>
		HasAttribute(decl, "ESExpr.Runtime.InlineValueAttribute", context.SemanticModel);




	private static List<LocalInfo> BuildLocalScope(GeneratorAttributeSyntaxContext context) {
		if(context.TargetSymbol is INamedTypeSymbol t) {
			if(t.TypeKind == TypeKind.Enum) {
				return [];
			}

			var member = GetTypeClassInstanceMember(t);
			switch(member) {
				case IMethodSymbol method:
					return method.Parameters.Select(p => new LocalInfo(p.Name, p.Type)).ToList();

				case IPropertySymbol:
					return [];
			}
		}

		throw new InputDiagnosticException(Errors.MissingRuntimeMember, "Unable to find TypeClassInstance method or property for " + context.TargetSymbol.Name);
	}

	private static ISymbol? GetTypeClassInstanceMember(INamedTypeSymbol t) {
		if(t.TypeKind == TypeKind.Enum) {
			return null;
		}

		INamedTypeSymbol instanceOwner = t;
		if(EnsureNonGenericType(ref instanceOwner)) {
			foreach(var member in instanceOwner.GetMembers()) {
				if(!HasAttribute(member, "ESExpr.Runtime.TypeClassInstanceAttribute")) {
					continue;
				}

				switch(member) {
					case IMethodSymbol:
					case IPropertySymbol:
						return member;
				}
			}
		}

		return null;
	}


}
