using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class RepeatedArgumentsTest : TestBase {
	[Test]
	public void ManyArgsTest() {
		AssertCodecMatch(
			new RepeatedArguments.Codec(),
			new Expr.Constructor(
				"repeated-arguments",
				[ new Expr.Str("A"), new Expr.Str("B"), new Expr.Str("Z") ],
				ImmutableDictionary<string, Expr>.Empty
					.Add("A", new Expr.Str("1"))
					.Add("B", new Expr.Str("2"))
					.Add("Z", new Expr.Str("3"))
			),
			new RepeatedArguments {
				Args = [ "A", "B", "Z" ],
				Kwargs = ImmutableDictionary<string, string>.Empty
					.Add("A", "1")
					.Add("B", "2")
					.Add("Z", "3"),
			}
		);
	}
}
