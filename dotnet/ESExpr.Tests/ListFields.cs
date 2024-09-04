using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record ListFields {
	public required VList<string> MyList { get; init; }
}
