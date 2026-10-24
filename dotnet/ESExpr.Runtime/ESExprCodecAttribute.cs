using System;

namespace ESExpr.Runtime;

[AttributeUsage(AttributeTargets.Class | AttributeTargets.Enum | AttributeTargets.Struct)]
public sealed class ESExprCodecAttribute : Attribute {
	public bool Flags { get; init; } = false;
}
