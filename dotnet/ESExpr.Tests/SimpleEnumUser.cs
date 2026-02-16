using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record SimpleEnumUser {
	public required MySimpleEnum SimpleEnumValue { get; init; }
	
	[TypeClassInstance]
	public static partial IESExprCodec<SimpleEnumUser> Codec { get; }
}
