using System.Numerics;

namespace ESExpr.SourceGenerator;

internal sealed record SourceModelFlagsEnumCase {
	public required string Name { get; init; }
	public required BigInteger Value { get; init; }
}
