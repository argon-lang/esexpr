using System.Collections.Immutable;
using System.Numerics;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class InlineValueTests : TestBase {
	[Test]
	public void TestInlineValue() {
		Assert.That(new InlineValueEnum.Codec().Tags, Is.EqualTo((HashSet<ESExprTag>)[ new ESExprTag.Int(), new ESExprTag.Constructor("b") ]));
		
		AssertCodecMatch(
			new InlineValueEnum.Codec(),
			new Expr.Int(BigInteger.Zero),
			new InlineValueEnum.A {
				AValue = 0,
			}
		);
		AssertCodecMatch(
			new InlineValueEnum.Codec(),
			new Expr.Constructor(
				"b",
				[ new Expr.Float32(0.0f) ],
				ImmutableDictionary<string, Expr>.Empty
			),
			new InlineValueEnum.B {
				BValue = 0.0f,
			}
		);
	}
}
