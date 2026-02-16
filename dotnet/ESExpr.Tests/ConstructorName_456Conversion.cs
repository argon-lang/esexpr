using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record ConstructorName_456Conversion {
	public required int A { get; init; }
	
	[TypeClassInstance]
	public static partial IESExprCodec<ConstructorName_456Conversion> Codec { get; }
}
