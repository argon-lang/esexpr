using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class KeywordArgTests : TestBase {
	[Test]
	public void OptionalFieldsPresent() {
		AssertCodecMatch(
			new KeywordArguments.Codec(),
			new Expr.Constructor(
				"keyword-arguments",
				[],
				ImmutableDictionary<string, Expr>.Empty
					.Add("a", new Expr.Bool(false))
					.Add("b2", new Expr.Bool(false))
					.Add("c2", new Expr.Bool(false))
					.Add("d", new Expr.Bool(false))
					.Add("e", new Expr.Bool(false))
					.Add("f", new Expr.Bool(false))
					.Add("g", new Expr.Bool(false))
			),
			new KeywordArguments {
				A = false,
				B = false,
				C = new Option<bool>(false),
				D = new Option<bool>(false),
				E = false,
				F = false,
				G = new Option<bool>(false),
			}
		);
	}
}
