using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record RepeatedArguments {
	[Vararg]
	public required ImmutableList<string> Args { get; init; }

	[Dict]
	public required ImmutableDictionary<string, string> Kwargs { get; init; }
	
	[TypeClassInstance]
	public static partial IESExprCodec<RepeatedArguments> Codec { get; }
}
