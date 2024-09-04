namespace ESExpr.Runtime;

public abstract record ESExprTag {
	public sealed record Constructor(string constructor) : ESExprTag;
	public sealed record Bool() : ESExprTag;
	public sealed record Int() : ESExprTag;
	public sealed record Str() : ESExprTag;
	public sealed record Binary() : ESExprTag;
	public sealed record Float32() : ESExprTag;
	public sealed record Float64() : ESExprTag;
	public sealed record Null() : ESExprTag;
}
