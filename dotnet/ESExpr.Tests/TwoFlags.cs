using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec(Flags = true)]
public sealed partial record TwoFlags {
	[FlagBits(0x1)]
	public required bool A { get; init; }
	
	[FlagBits(0x2)]
	public required bool B { get; init; }
}
