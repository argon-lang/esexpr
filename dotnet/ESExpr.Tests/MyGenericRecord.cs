using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed record MyGenericRecord<T> {
	public required T Value { get; init; }

}

public static partial class MyGenericRecord {
	[TypeClassInstance]
	public static partial IESExprCodec<MyGenericRecord<T>> Codec<T>(IESExprCodec<T> tCodec);
}
