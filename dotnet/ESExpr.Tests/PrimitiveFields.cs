using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record PrimitiveFields {
	public required bool A { get; init; }
	public required sbyte B { get; init; }
	public required byte B2 { get; init; }
	public required short C { get; init; }
	public required ushort C2 { get; init; }
	public required int D { get; init; }
	public required uint D2 { get; init; }
	public required long E { get; init; }
	public required ulong E2 { get; init; }
	public required float F { get; init; }
	public required double G { get; init; }
}
