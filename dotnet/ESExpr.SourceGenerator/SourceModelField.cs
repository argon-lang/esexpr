using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

public record SourceModelField {
	public required string Name { get; init; }
	public required Location Location { get; init; }
	public required SourceModelType Type { get; init; }

	public required bool IsDict { get; init; }
	public required bool IsVararg { get; init; }
	public required bool IsOptional { get; init; }
	public required SourceModelSyntax<ExpressionSyntax>? DefaultValue { get; init; }
	public required string? IsKeyword { get; init; }
}
