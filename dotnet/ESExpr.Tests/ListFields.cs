using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record ListFields {
	public required ImmutableList<string> MyList { get; init; }
}
