using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record PositionalDefaultValues {
	public int A { get; init; } = 1;
}
