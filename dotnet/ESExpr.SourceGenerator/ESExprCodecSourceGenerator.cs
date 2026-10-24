using System;
using ESExpr.SourceGenerator.Model;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

[Generator]
public class ESExprCodecSourceGenerator : IIncrementalGenerator {
	public void Initialize(IncrementalGeneratorInitializationContext context) {
		var models = context.SyntaxProvider.ForAttributeWithMetadataName(
			"ESExpr.Runtime.ESExprCodecAttribute",
			static (node, _) => node is BaseTypeDeclarationSyntax,
			static (ctx, _) => ctx)
			.Combine(context.CompilationProvider)
			.Select(static (pair, cancellationToken) => {
				cancellationToken.ThrowIfCancellationRequested();
				var decl = (BaseTypeDeclarationSyntax)pair.Left.TargetNode;
				var diagnostics = new GenerationDiagnostics { Location = decl.Identifier.GetLocation() };
				var model = diagnostics.Recover<ITypeSourceModel?>(decl.Identifier.GetLocation(), () => {
					var builder = new ModelBuilder(pair.Right, pair.Left, diagnostics);
					SourceModelValidation.ValidateSyntax(decl, pair.Left.SemanticModel, diagnostics);
					var result = builder.CreateSourceModel(decl);
					SourceModelValidation.Validate(result, diagnostics);
					return result;
				}, null);
				return new TypeBuildResult(diagnostics.HasErrors ? null : model, diagnostics.Diagnostics, decl.Identifier.GetLocation());
			});

		context.RegisterSourceOutput(models, static (context, result) => {
			foreach(var diagnostic in result.Diagnostics) context.ReportDiagnostic(diagnostic);
			if(result.Model is null) return;
			context.CancellationToken.ThrowIfCancellationRequested();
			var diagnostics = new GenerationDiagnostics { Location = result.Location };
			diagnostics.Recover(result.Location, () => { result.Model.Generator(context).Generate(); return true; }, false);
			foreach(var diagnostic in diagnostics.Diagnostics) context.ReportDiagnostic(diagnostic);
		});
	}
}
