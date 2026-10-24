using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal closed record SourceModelFlagsField {
	public required string Name { get; init; }
	public required Location Location { get; init; }
}
