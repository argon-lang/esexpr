using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Globalization;
using System.Linq;
using System.Numerics;
using System.Text.RegularExpressions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static ESExpr.SourceGenerator.GenUtils;
using static ESExpr.SourceGenerator.NameUtils;

namespace ESExpr.SourceGenerator;

[Generator]
public class ESExprCodecSourceGenerator : IIncrementalGenerator {



	public void Initialize(IncrementalGeneratorInitializationContext context) {


		IncrementalValuesProvider<ITypeSourceModel?> enumsToGenerate = context.SyntaxProvider
			.ForAttributeWithMetadataName(
				"ESExpr.Runtime.ESExprCodecAttribute",
				predicate: static (node, _) => node is BaseTypeDeclarationSyntax,
				transform: static (ctx, _) => CreateSourceModel(ctx))
			.Where(static m => m is not null);

		IncrementalValueProvider<TypeInfoHandler> overrideHandler = context.CompilationProvider
			.Select((compilation, _) => TypeInfoHandler.Load(compilation));

		var valuesProvider = enumsToGenerate.Combine(overrideHandler);

		context.RegisterSourceOutput(
			valuesProvider,
			static (context, valuesTuple) => {
				var (typeModel, typeInfoHandler) = valuesTuple;
				try {
					typeModel?.Generator(context, typeInfoHandler).Generate();
				}
				catch(AbortGenerationException ex) {
					context.ReportDiagnostic(ex.Diagnostic);
				}
				catch(Exception ex) {
					context.ReportDiagnostic(Diagnostic.Create(
						new DiagnosticDescriptor(
							id: "SG001",
							title: "Source Generator Exception",
							messageFormat: "{0}",
							category: "SourceGenerator",
							DiagnosticSeverity.Error,
							isEnabledByDefault: true),
						Location.None,
						ex.ToString().Replace("\n", " ")));
				}
			}
		);
	}

	private static ITypeSourceModel? CreateSourceModel(GeneratorAttributeSyntaxContext context) {
		var decl = (BaseTypeDeclarationSyntax)context.TargetNode;

		if(decl.Parent is not (BaseNamespaceDeclarationSyntax or CompilationUnitSyntax)) {
			return new InvalidTypeSourceModel {
				Descriptor = Errors.InvalidNestedESExprType,
				Location = decl.Identifier.GetLocation(),
				MessageArgs = [decl.Identifier.ToString()],
			};
		}

		switch(decl) {
			case EnumDeclarationSyntax enumDecl:
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
					return new FlagsSourceModel {
						Usings = GetUsings(recordDecl),
						Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
						TypeName = recordDecl.Identifier.ToString(),
						Location = recordDecl.Identifier.GetLocation(),
						TypeParameters = GetTypeParameters(recordDecl),
						Fields = GetFlagsFields(recordDecl, context.SemanticModel),
					};
				}
				else {
					return new RecordSourceModel {
						Usings = GetUsings(recordDecl),
						Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
						TypeName = recordDecl.Identifier.ToString(),
						Location = recordDecl.Identifier.GetLocation(),
						ConstructorName = GetConstructorName(recordDecl, context.SemanticModel),
						TypeParameters = GetTypeParameters(recordDecl),
						Fields = GetFields(recordDecl, context.SemanticModel),
					};
				}


			case RecordDeclarationSyntax recordDecl when recordDecl.Modifiers.Any(SyntaxKind.AbstractKeyword):
				if(!IsValidEnumRecord(recordDecl)) {
					return new InvalidTypeSourceModel {
						Descriptor = Errors.InvalidESExprEnumDeclaration,
						Location = decl.Identifier.GetLocation(),
						MessageArgs = [decl.Identifier.ToString()],
					};
				}

				return new UnionRecordSourceModel {
					Usings = GetUsings(recordDecl),
					Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
					TypeName = recordDecl.Identifier.ToString(),
					Location = recordDecl.Identifier.GetLocation(),
					TypeParameters = GetTypeParameters(recordDecl),
					Cases = recordDecl.Members
						.OfType<RecordDeclarationSyntax>()
						.Where(caseDecl => caseDecl.Modifiers.Any(SyntaxKind.PublicKeyword))
						.Select(caseDecl => new SourceModelEnumCase {
							Name = caseDecl.Identifier.ToString(),
							Location = caseDecl.Identifier.GetLocation(),
							ConstructorName = GetConstructorName(caseDecl, context.SemanticModel),
							IsInlineValue = IsInlineValue(caseDecl, context.SemanticModel),
							Fields = GetFields(caseDecl, context.SemanticModel),
						})
						.ToImmutableList(),
				};

			default:
				return new InvalidTypeSourceModel {
					Descriptor = Errors.InvalidESExprTypeDeclaration,
					Location = decl.Identifier.GetLocation(),
					MessageArgs = [decl.Identifier.ToString()],
				};
		}
	}

	private static bool IsValidEnumRecord(RecordDeclarationSyntax decl) {
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

	private static ImmutableList<SourceModelField> GetFields(TypeDeclarationSyntax decl, SemanticModel semanticModel) =>
		decl.Members
			.OfType<PropertyDeclarationSyntax>()
			.Select(prop => {
				var t = semanticModel.GetTypeInfo(prop.Type).Type;
				if(t is null) {
					throw new Exception("Could not get type of field");
				}

				return new SourceModelField {
					Name = prop.Identifier.ToString(),
					Location = prop.Identifier.GetLocation(),
					Type = SourceModelType.FromSymbol(t),
					IsDict = IsDict(prop, semanticModel),
					IsVararg = IsVararg(prop, semanticModel),
					IsOptional = IsOptional(prop, semanticModel),
					DefaultValue = IsDefaultValue(prop, semanticModel) is { } defaultValue
						? new SourceModelSyntax<ExpressionSyntax>(defaultValue) : null,
					IsKeyword = IsKeyword(prop, semanticModel),
				};
			})
			.ToImmutableList();

	private static ImmutableList<SourceModelFlagsField> GetFlagsFields(TypeDeclarationSyntax decl, SemanticModel semanticModel) =>
		decl.Members
			.OfType<PropertyDeclarationSyntax>()
			.Select(prop => {
				var t = semanticModel.GetTypeInfo(prop.Type).Type;
				if(t is null) {
					throw new Exception("Could not get type of field");
				}
				
				var declType = semanticModel.GetDeclaredSymbol(decl);
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
					throw new Exception("Enum field type must be a bool or enum defined as a nested type.");
				}
				
				return field;
			})
			.ToImmutableList();

	private static bool IsVararg(PropertyDeclarationSyntax decl, SemanticModel semanticModel) =>
		HasAttribute(decl, "ESExpr.Runtime.VarargAttribute", semanticModel);

	private static bool IsDict(PropertyDeclarationSyntax decl, SemanticModel semanticModel) =>
		HasAttribute(decl, "ESExpr.Runtime.DictAttribute", semanticModel);

	private static bool IsOptional(PropertyDeclarationSyntax decl, SemanticModel semanticModel) =>
		HasAttribute(decl, "ESExpr.Runtime.OptionalAttribute", semanticModel);

	private static ExpressionSyntax? IsDefaultValue(PropertyDeclarationSyntax decl, SemanticModel semanticModel) {
		var attr = GetAttribute(decl, "ESExpr.Runtime.DefaultValueAttribute", semanticModel);
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

	private static string? IsKeyword(PropertyDeclarationSyntax decl, SemanticModel semanticModel) {
		var attr = GetAttribute(decl, "ESExpr.Runtime.KeywordAttribute", semanticModel);
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

	private static bool IsInlineValue(RecordDeclarationSyntax decl, SemanticModel semanticModel) =>
		HasAttribute(decl, "ESExpr.Runtime.InlineValueAttribute", semanticModel);
	


}
