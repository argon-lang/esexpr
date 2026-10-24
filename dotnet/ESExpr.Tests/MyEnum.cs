using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public closed partial record MyEnum {

	public sealed record MyCaseA : MyEnum {
		public required int A { get; init; }
	}

	public sealed record MyCaseB : MyEnum {
		public required float B { get; init; }
	}

	[TypeClassInstance]
	public static partial IESExprCodec<MyEnum> Codec { get; }
}
