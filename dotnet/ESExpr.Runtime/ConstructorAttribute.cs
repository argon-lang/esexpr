using System;

namespace ESExpr.Runtime;

[AttributeUsage(AttributeTargets.Class | AttributeTargets.Field, AllowMultiple = false, Inherited = false)]
public sealed class ConstructorAttribute : Attribute {
	public ConstructorAttribute(string name) {
		Name = name;
	}
	
	public string Name { get; }
}
