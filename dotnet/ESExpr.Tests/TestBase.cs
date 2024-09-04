using ESExpr.Runtime;

namespace ESExpr.Tests;

public abstract class TestBase {
	protected void AssertCodecMatch<T>(IESExprCodec<T> codec, Expr expr, T value)
		where T : notnull
	{
		Assert.That(codec.Encode(value), Is.EqualTo(expr));
		Assert.That(codec.Decode(expr), Is.EqualTo(value));
	}
}
