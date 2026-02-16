using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class PositionalDefaultValuesTests : TestBase {

	[Test]
	public void DefaultValues() {
		AssertCodecMatch(
			PositionalDefaultValues.Codec,
			new Expr.Constructor(
				"positional-default-values",
				[],
				ImmutableDictionary<string, Expr>.Empty
			),
			new PositionalDefaultValues()
		);
		
		AssertCodecMatch(
			PositionalDefaultValues.Codec,
			new Expr.Constructor(
				"positional-default-values",
				[ new Expr.Int(4), ],
				ImmutableDictionary<string, Expr>.Empty
			),
			new PositionalDefaultValues { A = 4 }
		);
		
	}
	
}
