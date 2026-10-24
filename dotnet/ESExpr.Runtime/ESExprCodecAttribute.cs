using System;

namespace ESExpr.Runtime;

[AttributeUsage(AttributeTargets.Class)]
public sealed class ESExprCodecAttribute : Attribute {
	public bool Flags { get; init; } = false;
}
