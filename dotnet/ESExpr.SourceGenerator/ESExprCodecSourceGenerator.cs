using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Globalization;
using System.Linq;
using System.Numerics;
using System.Text.RegularExpressions;
using ESExpr.SourceGenerator.Model;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using static ESExpr.SourceGenerator.GenUtils;
using static ESExpr.SourceGenerator.NameUtils;

namespace ESExpr.SourceGenerator;

[Generator]
public class ESExprCodecSourceGenerator : IIncrementalGenerator {



	public void Initialize(IncrementalGeneratorInitializationContext context) {


		IncrementalValuesProvider<GeneratorAttributeSyntaxContext> typesToGenerate = context.SyntaxProvider
			.ForAttributeWithMetadataName(
				"ESExpr.Runtime.ESExprCodecAttribute",
				predicate: static (node, _) => node is BaseTypeDeclarationSyntax,
				transform: static (ctx, _) => ctx);

		var modelsProvider =
			typesToGenerate.Combine(context.CompilationProvider)
				.Select(static ( pair, _) => {
					var context = pair.Left;
					var compilation = pair.Right;

					if(context.TargetNode is not BaseTypeDeclarationSyntax typeDecl) {
						return null;
					}
				
					var builder = new ModelBuilder(compilation, context);


					return builder.CreateSourceModel(typeDecl);
				})
				.Where(static pair => pair is not null);

		context.RegisterSourceOutput(
			modelsProvider,
			static (context, model) => {
				if(model is null) {
					return;
				}

				try {
					model.Generator(context).Generate();
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
	
}
