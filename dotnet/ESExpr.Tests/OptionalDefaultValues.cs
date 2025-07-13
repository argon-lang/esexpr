using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record OptionalDefaultValues {
	[Optional]
	public Option<int> A { get; init; }
}
