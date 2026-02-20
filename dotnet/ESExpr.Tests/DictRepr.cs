namespace ESExpr.Tests;


[global::ESExpr.Runtime.ESExprCodec, global::ESExpr.Runtime.Constructor("dict")]
public sealed record DictRepr<A>
{
	[global::ESExpr.Runtime.Dict]
	public required global::System.Collections.Immutable.ImmutableDictionary<global::System.String, A> Values { get; init; }
}

public static partial class DictRepr
{
	[global::ESExpr.Runtime.TypeClassInstance]
	public static partial global::ESExpr.Runtime.IESExprCodec<DictRepr<A>> Codec<A>(A aCodec);
}
