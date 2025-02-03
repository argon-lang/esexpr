using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

public record SourceModelEnumCase {
	public required string Name { get; init; }
	public required Location Location { get; init; }
	public required string ConstructorName { get; init; }
	public required bool IsInlineValue { get; init; }
	public required VList<SourceModelField> Fields { get; init; }
}
