using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class OptionalDefaultValuesTests : TestBase {

	[Test]
	public void OptionalValues() {
		AssertCodecMatch(
			OptionalDefaultValues.Codec,
			new Expr.Constructor(
				"optional-default-values",
				[],
				ImmutableDictionary<string, Expr>.Empty
			),
			new OptionalDefaultValues { A = Option<int>.Empty }
		);
		
		AssertCodecMatch(
			OptionalDefaultValues.Codec,
			new Expr.Constructor(
				"optional-default-values",
				[ new Expr.Int(4), ],
				ImmutableDictionary<string, Expr>.Empty
			),
			new OptionalDefaultValues { A = new Option<int>(4) }
		);
		
	}
	
}
