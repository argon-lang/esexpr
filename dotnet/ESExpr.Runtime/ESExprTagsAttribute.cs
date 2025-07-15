using System;

namespace ESExpr.Runtime;

public class ESExprTagsAttribute : Attribute {
	
	public ESExprTag.ScalarType[] Scalar { get; set; } = [];

	public string[] Constructors { get; set; } = [];
	
	public bool All { get; set; } = false;
	
	public string[] UnionWithTypeParameters { get; set; } = [];
	
}
