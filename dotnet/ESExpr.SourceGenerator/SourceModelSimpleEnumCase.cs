using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

internal record SourceModelSimpleEnumCase {
	public required string Name { get; init; }
	public required Location Location { get; init; }
	public required string ConstructorName { get; init; }
}
