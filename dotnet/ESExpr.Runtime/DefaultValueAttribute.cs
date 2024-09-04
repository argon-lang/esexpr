using System;

namespace ESExpr.Runtime;

[AttributeUsage(AttributeTargets.Property)]
public class DefaultValueAttribute : Attribute {
	public DefaultValueAttribute(string valueExpression) {
		ValueExpression = valueExpression;
	}
	
	public string ValueExpression { get; }
}
