using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
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
				var (typeModel, overrideHandler) = valuesTuple;
				try {
					typeModel?.Generator(context, overrideHandler).Generate();
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
			case EnumDeclarationSyntax _:
				return null;

			case RecordDeclarationSyntax recordDecl when recordDecl.Modifiers.Any(SyntaxKind.SealedKeyword):
				if((recordDecl.ParameterList?.Parameters.Count ?? 0) != 0) {
					return new InvalidTypeSourceModel {
						Descriptor = Errors.InvalidESExprEnumDeclaration,
						Location = decl.Identifier.GetLocation(),
						MessageArgs = [decl.Identifier.ToString()],
					};
				}

				return new RecordSourceModel {
					Usings = GetUsings(recordDecl),
					Namespace = GetNamespaceFromNamespaceNodes(recordDecl.Parent),
					TypeName = recordDecl.Identifier.ToString(),
					Location = recordDecl.Identifier.GetLocation(),
					ConstructorName = GetConstructorName(recordDecl, context.SemanticModel),
					TypeParameters = GetTypeParameters(recordDecl),
					Fields = GetFields(recordDecl, context.SemanticModel),
				};

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

	private static ImmutableList<SourceModelSyntax<UsingDirectiveSyntax>> GetUsings(TypeDeclarationSyntax decl) {
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
			args.Count == 1 &&
			args[0].Expression is LiteralExpressionSyntax value
		) {
			return value.Token.ValueText;
		}
		else {
			return NameToKebabCase(decl.Identifier.Text);
		}
	}

	private static bool IsInlineValue(RecordDeclarationSyntax decl, SemanticModel semanticModel) =>
		HasAttribute(decl, "ESExpr.Runtime.InlineValueAttribute", semanticModel);
	


}

