using System.Collections.Immutable;
using System.Numerics;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal sealed record SourceModelFlagsFieldEnum : SourceModelFlagsField {
	public required SourceModelType Type { get; init; }
	public required ImmutableList<SourceModelFlagsEnumCase> Cases { get; init; }
}
