using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record ConstructorName123Conversion {
	public required int A { get; init; }
}
