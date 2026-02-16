using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record MultipleVarargs {
	[Vararg]
	public required ImmutableList<string> A { get; init; }
	[Vararg]
	public required ImmutableList<int> B { get; init; }
	
	[TypeClassInstance]
	public static partial IESExprCodec<MultipleVarargs> Codec { get; }
}
