using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record MyGenericRecord<T> {
	public required T Value { get; init; }
}
