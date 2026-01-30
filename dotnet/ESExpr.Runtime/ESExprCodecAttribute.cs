using System;

namespace ESExpr.Runtime;

public sealed class ESExprCodecAttribute : Attribute {
	public bool Flags { get; init; } = false;
}
