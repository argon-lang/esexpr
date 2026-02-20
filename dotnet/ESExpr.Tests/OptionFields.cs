using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record OptionFields {
	public Option<int> A { get; init; }
	
	[TypeClassInstance]
	public static partial IESExprCodec<OptionFields> Codec { get; }
}
