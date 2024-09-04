using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public abstract partial record InlineValueEnum {
	private InlineValueEnum() {}

	[InlineValue]
	public sealed record A : InlineValueEnum {
		public required int AValue { get; init; }
	}
	
	public sealed record B : InlineValueEnum {
		public required float BValue { get; init; }
	}

}
