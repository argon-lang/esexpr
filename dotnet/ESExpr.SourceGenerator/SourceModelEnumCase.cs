using System.Collections.Immutable;
using Microsoft.CodeAnalysis;

namespace ESExpr.SourceGenerator;

record SourceModelEnumCase {
	public required string Name { get; init; }
	public required Location Location { get; init; }
	public required string ConstructorName { get; init; }
	public required bool IsInlineValue { get; init; }
	public required ImmutableList<SourceModelField> Fields { get; init; }
}
