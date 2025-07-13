namespace ESExpr.Runtime;

public abstract record ESExprTag {
	public sealed record Constructor(string constructor) : ESExprTag;
	public sealed record Bool() : ESExprTag;
	public sealed record Int() : ESExprTag;
	public sealed record Str() : ESExprTag;
	public sealed record Float16() : ESExprTag;
	public sealed record Float32() : ESExprTag;
	public sealed record Float64() : ESExprTag;
	public sealed record Array8() : ESExprTag;
	public sealed record Array16() : ESExprTag;
	public sealed record Array32() : ESExprTag;
	public sealed record Array64() : ESExprTag;
	public sealed record Array128() : ESExprTag;
	public sealed record Null() : ESExprTag;
}
