using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static ESExpr.SourceGenerator.GenUtils;

namespace ESExpr.SourceGenerator;

[Generator]
public class ESExprCodecSourceGenerator : ISourceGenerator {
	public void Initialize(GeneratorInitializationContext context) {
	}

	public void Execute(GeneratorExecutionContext context) {
		try {
			var typeMapper = CodecOverrideHandler.Load(context);

			foreach(var tree in context.Compilation.SyntaxTrees) {
				var semanticModel = context.Compilation.GetSemanticModel(tree);

				foreach(var decl in tree.GetRoot().DescendantNodes().OfType<BaseTypeDeclarationSyntax>()) {
					if(!HasAttribute(decl, "ESExpr.Runtime.ESExprCodecAttribute", semanticModel)) {
						continue;
					}

					GenerateCodec(context, semanticModel, typeMapper, decl);
				}
			}
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

	private void GenerateCodec(
		GeneratorExecutionContext context,
		SemanticModel semanticModel,
		CodecOverrideHandler codecOverrideHandler,
		BaseTypeDeclarationSyntax decl
	) {
		if(decl.Parent is not (BaseNamespaceDeclarationSyntax or CompilationUnitSyntax)) {
			context.ReportDiagnostic(Diagnostic.Create(
				Errors.InvalidNestedESExprType,
				decl.Identifier.GetLocation(),
				decl.Identifier.ToString()
			));
		}

		switch(decl) {
			case RecordDeclarationSyntax recordDecl when recordDecl.Modifiers.Any(SyntaxKind.SealedKeyword):
				if((recordDecl.ParameterList?.Parameters.Count ?? 0) != 0) {
					context.ReportDiagnostic(Diagnostic.Create(
						Errors.InvalidESExprRecordDeclaration,
						recordDecl.Identifier.GetLocation(),
						recordDecl.Identifier.ToString()
					));
				}

				new RecordCodecGenerator {
					Context = context,
					SemanticModel = semanticModel,
					CodecOverrideHandler = codecOverrideHandler,
					Decl = recordDecl,
				}.GenerateCodecClass();
				break;
			
			case RecordDeclarationSyntax recordDecl when recordDecl.Modifiers.Any(SyntaxKind.AbstractKeyword):
				if(!IsValidEnumRecord(recordDecl)) {
					context.ReportDiagnostic(Diagnostic.Create(
						Errors.InvalidESExprEnumDeclaration,
						recordDecl.Identifier.GetLocation(),
						recordDecl.Identifier.ToString()
					));
				}
				
				new UnionRecordCodecGenerator {
					Context = context,
					SemanticModel = semanticModel,
					CodecOverrideHandler = codecOverrideHandler,
					Decl = recordDecl,
				}.GenerateCodecClass();
				break;
				
			case EnumDeclarationSyntax enumDecl:
				throw new NotImplementedException();
			
			default:
				context.ReportDiagnostic(Diagnostic.Create(
					Errors.InvalidESExprTypeDeclaration,
					decl.Identifier.GetLocation(),
					decl.Identifier.ToString()
				));
				break;
		}
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


	

}

