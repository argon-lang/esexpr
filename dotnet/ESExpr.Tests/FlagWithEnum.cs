using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec(Flags = true)]
public sealed partial record FlagWithEnum {
	[FlagBits(0x1)]
	public required bool A { get; init; }
	
	public required MyEnum B { get; init; }

	public enum MyEnum {
		[FlagBits(0x2)]
		X,
		[FlagBits(0x4)]
		Y,
		[FlagBits(0x6)]
		Z,
	}
	
	
	[TypeClassInstance]
	public static partial IESExprCodec<FlagWithEnum> Codec { get; }
}
