using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public abstract partial record MyEnum {
	private MyEnum() {}

	public sealed record MyCaseA : MyEnum {
		public required int A { get; init; }
	}

	public sealed record MyCaseB : MyEnum {
		public required float B { get; init; }
	}
}
