using System.Numerics;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace ESExpr.SourceGenerator;

internal sealed record SourceModelFlagsFieldFlag : SourceModelFlagsField {
	public required BigInteger Mask { get; init; }
}
