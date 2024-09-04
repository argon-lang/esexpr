using ESExpr.Runtime;

namespace ESExpr.Tests;

[ESExprCodec]
public sealed partial record KeywordArguments {
	[Keyword]
	public required bool A { get; init; }
	
	[Keyword("b2")]
	public required bool B { get; init; }
	
	[Keyword("c2")]
	[Optional]
	public required Option<bool> C { get; init; }
	
	[Keyword]
	[Optional]
	public required Option<bool> D { get; init; }
	
	[Keyword]
	[DefaultValue("true")]
	public required bool E { get; init; }

	[Keyword]
	public bool F { get; init; } = true;
	
	[Keyword]
	public required Option<bool> G { get; init; }
}
