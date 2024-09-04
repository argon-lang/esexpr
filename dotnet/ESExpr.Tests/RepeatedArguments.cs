using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record RepeatedArguments {
	[Vararg]
	public required VList<string> Args { get; init; }
	
	[Dict]
	public required VDict<string> Kwargs { get; init; }
}
