using System;

namespace ESExpr.Runtime;

[AttributeUsage(AttributeTargets.Property)]
public class KeywordAttribute : Attribute {
	public KeywordAttribute() {
		Name = null;
	}
	
	public KeywordAttribute(string name) {
		Name = name;
	}
	
	public string? Name { get; }
}
