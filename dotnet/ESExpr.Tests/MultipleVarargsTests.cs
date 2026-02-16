using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class MultipleVarargsTests : TestBase {

	[Test]
	public void MultipleVarargsTest() {
		AssertCodecMatch(
			MultipleVarargs.Codec,
			new Expr.Constructor(
				"multiple-varargs",
				[ new Expr.Str("A"), new Expr.Int(1) ],
				ImmutableDictionary<string, Expr>.Empty
			),
			new MultipleVarargs() {
				A = [ "A" ],
				B = [ 1 ],
			}
		);
		
		AssertCodecMatch(
			MultipleVarargs.Codec,
			new Expr.Constructor(
				"multiple-varargs",
				[ new Expr.Str("A") ],
				ImmutableDictionary<string, Expr>.Empty
			),
			new MultipleVarargs() {
				A = [ "A" ],
				B = [],
			}
		);
		
		AssertCodecMatch(
			MultipleVarargs.Codec,
			new Expr.Constructor(
				"multiple-varargs",
				[ new Expr.Int(1) ],
				ImmutableDictionary<string, Expr>.Empty
			),
			new MultipleVarargs() {
				A = [],
				B = [ 1 ],
			}
		);
		
		AssertCodecMatch(
			MultipleVarargs.Codec,
			new Expr.Constructor(
				"multiple-varargs",
				[],
				ImmutableDictionary<string, Expr>.Empty
			),
			new MultipleVarargs() {
				A = [],
				B = [],
			}
		);
		
	}
	
}
