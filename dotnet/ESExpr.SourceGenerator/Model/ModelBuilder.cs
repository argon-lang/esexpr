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

	public ModelBuilder(Compilation compilation, GeneratorAttributeSyntaxContext context) {
		this.compilation = compilation;
		this.context = context;
		resolver = new TypeClassResolver(compilation, BuildLocalScope(context));
	}
	
	private readonly Compilation compilation;
	private readonly GeneratorAttributeSyntaxContext context;
	private readonly TypeClassResolver resolver;
	
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


			case RecordDeclarationSyntax recordDecl when recordDecl.Modifiers.Any(SyntaxKind.AbstractKeyword):
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

	private ITypeSourceModel? CreateUnionRecordSourceModel(RecordDeclarationSyntax recordDecl) {
		return new UnionRecordSourceModel {
			Usings = GetUsings(recordDecl),
			Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
			TypeName = recordDecl.Identifier.ToString(),
			Location = recordDecl.Identifier.GetLocation(),
			TypeParameters = GetTypeParameters(recordDecl),
			Tags = GetTagsFor(context.SemanticModel.GetDeclaredSymbol(recordDecl) ?? throw new Exception("Could not find symbol for union record")),
			TypeClassInstanceAccessor = BuildInstanceAccessor(recordDecl),
			Cases = recordDecl.Members
				.OfType<RecordDeclarationSyntax>()
				.Where(caseDecl => caseDecl.Modifiers.Any(SyntaxKind.PublicKeyword))
				.Select(caseDecl => new SourceModelEnumCase {
					Name = caseDecl.Identifier.ToString(),
					Location = caseDecl.Identifier.GetLocation(),
					ConstructorName = GetConstructorName(caseDecl, context.SemanticModel),
					IsInlineValue = IsInlineValue(caseDecl),
					Fields = GetFields(caseDecl),
				})
				.ToImmutableList(),
		};
	}

	private ITypeSourceModel? CreateRecordSourceModel(RecordDeclarationSyntax recordDecl) {
		return new RecordSourceModel {
			Usings = GetUsings(recordDecl),
			Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
			TypeName = recordDecl.Identifier.ToString(),
			Location = recordDecl.Identifier.GetLocation(),
			ConstructorName = GetConstructorName(recordDecl, context.SemanticModel),
			TypeParameters = GetTypeParameters(recordDecl),
			Tags = GetTagsFor(context.SemanticModel.GetDeclaredSymbol(recordDecl) ?? throw new Exception("Could not find symbol for field")),
			TypeClassInstanceAccessor = BuildInstanceAccessor(recordDecl),
			Fields = GetFields(recordDecl),
		};
	}

	private ITypeSourceModel? CreateFlagsSourceModel(RecordDeclarationSyntax recordDecl) {
		return new FlagsSourceModel {
			Usings = GetUsings(recordDecl),
			Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
			TypeName = recordDecl.Identifier.ToString(),
			Location = recordDecl.Identifier.GetLocation(),
			TypeParameters = GetTypeParameters(recordDecl),
			Fields = GetFlagsFields(recordDecl, context.SemanticModel),
			TypeClassInstanceAccessor = BuildInstanceAccessor(recordDecl),
		};
	}

	private ITypeSourceModel? CreateSimpleEnumSourceModel(EnumDeclarationSyntax enumDecl) {
		return new SimpleEnumSourceModel {
			Usings = GetUsings(enumDecl),
			Namespace = GetNamespaceFromNamespaceNodes(enumDecl.Parent),
			TypeName = enumDecl.Identifier.ToString(),
			Location = enumDecl.Identifier.GetLocation(),
			Cases = enumDecl.Members
				.Select(m => new SourceModelSimpleEnumCase {
					Name = m.Identifier.ToString(),
					Location = m.Identifier.GetLocation(),
					ConstructorName = GetConstructorName(m, context.SemanticModel),
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
		throw new Exception("Unable to find TypeClassInstance method or property for " + recordDecl.Identifier.ToString());
	}

	private bool IsValidEnumRecord(RecordDeclarationSyntax decl) {
		var constructors = decl.Members
			.OfType<ConstructorDeclarationSyntax>()
			.Take(2)
			.ToList();

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
				var t = context.SemanticModel.GetTypeInfo(prop.Type).Type;
				if(t is null) {
					throw new Exception("Could not get type of field");
				}

				SourceModelField.FieldMode mode;
				if(IsOptional(prop)) {
					var optionalValueCodecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IOptionalValueCodec`2");
					if(optionalValueCodecType is null) {
						throw new Exception("Could not find IOptionalValueCodec<T, TValue> type");
					}

					var codecTypeLookup = optionalValueCodecType.Construct(t, compilation.DynamicType);
					var res = resolver.Resolve(codecTypeLookup);
					if(res is null) {
						throw new Exception($"Could not resolve optional value codec for type {t}");
					}

					var codecType = resolver.GetTypeClassType(res);
					var elementType = ((INamedTypeSymbol)codecType).TypeArguments[1];
					
					mode = new SourceModelField.FieldMode.Optional {
						OptionalValueCodecInstance = BuildTypeClassInstanceModel(res),
						ElementType = SourceModelType.FromSymbol(elementType),
						KeywordMode = IsKeyword(prop) is {} keyword
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
						throw new Exception("Could not find IDictCodec<T, TValue> type");
					}

					var codecTypeLookup = dictCodecType.Construct(t, compilation.DynamicType);
					var res = resolver.Resolve(codecTypeLookup);
					if(res is null) {
						throw new Exception($"Could not resolve dict codec for type {t}");
					}

					var codecType = resolver.GetTypeClassType(res);
					var elementType = ((INamedTypeSymbol)codecType).TypeArguments[1];
					
					mode = new SourceModelField.FieldMode.Dict {
						DictCodecInstance = BuildTypeClassInstanceModel(res),
						ElementType = SourceModelType.FromSymbol(elementType),
					};
				}
				else if(IsVararg(prop)) {
					var varargCodecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IVarargCodec`2");
					if(varargCodecType is null) {
						throw new Exception("Could not find IVarargCodec<T> type");
					}

					var codecTypeLookup = varargCodecType.Construct(t, compilation.DynamicType);
					var res = resolver.Resolve(codecTypeLookup);
					if(res is null) {
						throw new Exception($"Could not resolve vararg codec for type {t}");
					}

					var codecType = resolver.GetTypeClassType(res);
					var elementType = ((INamedTypeSymbol)codecType).TypeArguments[1];
					
					mode = new SourceModelField.FieldMode.Vararg {
						ElementTags = GetTagsFor(elementType),
						ElementType = SourceModelType.FromSymbol(elementType),
						VarargCodecInstance = BuildTypeClassInstanceModel(res),
					};
				}
				else {
					var codecType = compilation.GetTypeByMetadataName("ESExpr.Runtime.IESExprCodec`1");
					if(codecType is null) {
						throw new Exception("Could not find IESExprCodec<T> type");
					}
		
					var lookupType = codecType.Construct(t);
		
					var res = resolver.Resolve(lookupType);
					if(res is null) {
						throw new Exception($"Could not resolve codec for type {t}. Looking for {lookupType}");
					}
					
					

					mode = new SourceModelField.FieldMode.Normal {
						CodecInstance = BuildTypeClassInstanceModel(res),
						
						DefaultValue = IsDefaultValue(prop) is {} defaultValue
							? new SourceModelSyntax<ExpressionSyntax>(defaultValue)
							: null,
						
						KeywordMode = IsKeyword(prop) is {} keyword
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
					Type = SourceModelType.FromSymbol(t),
					Mode = mode,
				};
				
			})
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
			_ => throw new ArgumentOutOfRangeException(nameof(res))
		};
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
			throw new Exception("Could not find IESExprCodec<T> type");
		}
		
		var lookupType = codecType.Construct(type);
		
		var res = resolver.Resolve(lookupType);
		if(res is null) {
			throw new Exception($"Could not resolve codec for type {type}. Looking for {lookupType}");
		}
		
		return GetTagsForCodec(type, res, seenTypes);
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
			_ => throw new ArgumentOutOfRangeException(nameof(res))
		};
	}

	internal record TypeTags(ESExprTagSet tags, ImmutableList<ITypeSymbol> unionWithTypes);
	
	private ESExprTagSet GetTagsForMember(ITypeSymbol type, ISymbol symbol, Dictionary<ITypeParameterSymbol, ITypeSymbol> typeParams, ImmutableHashSet<ITypeSymbol> seenTypes) {
		var typeTags = LoadESExprTagsFromAttr(symbol, typeParams) ?? LoadIntrinsicTags(type, typeParams);
		if(typeTags is null) {
			throw new Exception($"Could not load type tags for {type}");
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
		
		if(GetAttributeField("Scalar") is {} scalars) {
			foreach(var scalar in scalars.Values) {
				if(scalar.Value is not int intValue) {
					throw new Exception("Expected int enum value for scalar tag");
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
					_ => throw new Exception("Unknown enum value: " + (int)enumValue),
				});
			}
		}

		if(GetAttributeField("Constructors") is {} constructors) {
			foreach(var constructor in constructors.Values) {
				if(constructor.Value is not string s) {
					throw new Exception("Expected string value for constructor tag");
				}

				tags = tags.Add(new ESExprTag.Constructor(s));
			}
		}

		if(GetAttributeField("All") is {} all) {
			if(all.Value is not bool b) {
				throw new Exception("Expected bool value for all tag");
			}

			if(b) {
				tags = ESExprTagSet.All;
			}
		}

		if(GetAttributeField("UnionWithTypeParameters") is {} tps) {
			unionWithTypes = tps.Values
				.Select(tp => {
					if(tp.Value is not string tpName) {
						throw new Exception("Expected string value for constructor tag");
					}

					var typeArg = typeParams.Where(kvp => kvp.Key.Name == tpName)
						.Select(kvp => kvp.Value)
						.FirstOrDefault();
					
					if(typeArg is null) {
						throw new Exception("Unknown type parameter " + tpName);
					}

					return typeArg;
				})
				.ToImmutableList();
		}
		
		return new TypeTags(tags, unionWithTypes);
	}

	private TypeTags? LoadIntrinsicTags(ITypeSymbol t, Dictionary<ITypeParameterSymbol, ITypeSymbol> typeParams) {
		if(!HasAttribute(t, "ESExpr.Runtime.ESExprCodecAttribute")) {
			return null;
		}

		if(t.TypeKind == TypeKind.Enum) {
			return new TypeTags(ESExprTagSet.Create([ new ESExprTag.Str() ]), []);
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
		else if(t.IsRecord && t.IsAbstract) {
			var tags = new HashSet<ESExprTag>();
			var unionWithTypes = ImmutableList.CreateBuilder<ITypeSymbol>();

			var cases = t.GetTypeMembers()
				.Where(caseRec =>
					caseRec.IsRecord &&
					caseRec.DeclaredAccessibility == Accessibility.Public
				);

			foreach(var c in cases) {
				if(HasAttribute(c, "ESExpr.Runtime.InlineValueAttribute")) {
					var fields = c.GetMembers().OfType<IFieldSymbol>().ToList();
					
					if(fields.Count != 1) {
						throw new AbortGenerationException(
							Diagnostic.Create(
								Errors.InvalidInlineValue,
								null,
								new object[] { }
							)
						);
					}
					
					var field = fields[0];
					
					var substType = resolver.SubstituteTypes(field.Type, typeParams);
					if(substType is null) {
						throw new Exception($"Unable to substitute type parameters in {field.Type}");
					}
					
					unionWithTypes.Add(substType);
				}
				else {
					tags.Add(new ESExprTag.Constructor(GetConstructorName(c)));
				}
			}

			return new TypeTags(
				ESExprTagSet.Create(tags),
				unionWithTypes.ToImmutable()
			);
		}
		else {
			return null;
		}
	}
	
	

	private static ImmutableList<SourceModelFlagsField> GetFlagsFields(TypeDeclarationSyntax decl, SemanticModel semanticModel) =>
		decl.Members
			.OfType<PropertyDeclarationSyntax>()
			.Where(prop => !prop.Modifiers.Any(SyntaxKind.StaticKeyword))
			.Select(prop => {
				var t = ModelExtensions.GetTypeInfo(semanticModel, prop.Type).Type;
				if(t is null) {
					throw new Exception("Could not get type of field");
				}
				
				var declType = ModelExtensions.GetDeclaredSymbol(semanticModel, decl);
				if(declType is null) {
					throw new Exception("Could not get declared symbol for flags type declaration");
				}

				SourceModelFlagsField field;

				if(t.SpecialType == SpecialType.System_Boolean) {
					var flagBitsAttr = GetAttribute(prop, "ESExpr.Runtime.FlagBitsAttribute", semanticModel);
					if(flagBitsAttr is not { ArgumentList.Arguments: var args } || args.Count != 1) {
						throw new Exception("Flag must specify FlagBitsAttribute with exactly one constructor argument");
					}
					
					var maskExpr = args[0].Expression;
					var maskValue = semanticModel.GetConstantValue(maskExpr);
					if(!maskValue.HasValue) {
						throw new Exception("FlagBitsAttribute mask must be a constant value");
					}
					
					BigInteger mask = maskValue.Value switch {
						string s => BigInteger.Parse(s, CultureInfo.InvariantCulture),
						byte b => b,
						sbyte sb => sb,
						short s2 => s2,
						ushort us => us,
						int i => i,
						uint ui => ui,
						long l => l,
						ulong ul => ul,
						_ => throw new Exception("FlagBitsAttribute mask must be a ulong or string"),
					};

					if(mask < 0) {
						throw new Exception("FlagBitsAttribute mask must be non-negative");
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
							.Select(f => {
								var attr = GetAttribute(f, "ESExpr.Runtime.FlagBitsAttribute");

								if(attr is null) {
									throw new Exception("Enum field must be marked with FlagBitsAttribute");
								}
								
								if(attr.ConstructorArguments.Length != 1) {
									throw new Exception("FlagBitsAttribute must have exactly one constructor argument");
								}

								BigInteger value = attr.ConstructorArguments[0].Value switch {
									string s => BigInteger.Parse(s, CultureInfo.InvariantCulture),
									byte b => b,
									sbyte sb => sb,
									short s2 => s2,
									ushort us => us,
									int i => i,
									uint ui => ui,
									long l => l,
									ulong ul => ul,
									_ => throw new Exception("FlagBitsAttribute value must be a ulong or string"),
								};
								
								if(value < 0) {
									throw new Exception("FlagBitsAttribute value must be non-negative");
								}
								
								return new SourceModelFlagsEnumCase {
									Name = f.Name,
									Value = value,
								};
							})
							.ToImmutableList(),
					};
				}
				else {
					throw new Exception("Flag field type must be a bool or enum defined as a nested type.");
				}
				
				return field;
			})
			.ToImmutableList();

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
			if(args[0].Expression is not LiteralExpressionSyntax value) {
				throw new Exception("KeywordAttribute must have a string literal argument");
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

		throw new Exception("Unable to find TypeClassInstance method or property for " + context.TargetSymbol.Name);
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
