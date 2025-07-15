using ESExpr.Runtime;
using NUnit.Framework.Constraints;

namespace ESExpr.Tests;

public abstract class TestBase {

	public class EncodedEqualToConstraint<T> : Constraint {
		public EncodedEqualToConstraint(IESExprCodec<T> codec, T expected) {
			this.codec = codec;
			this.expected = expected;
		}
		
		private readonly IESExprCodec<T> codec;
		private readonly T expected;

		public override ConstraintResult ApplyTo<TActual>(TActual actual) {
			if(actual is not T actualValue) return new ConstraintResult(this, actual, false);
			bool isEqual = codec.IsEncodedEqual(expected, actualValue);
            return new ConstraintResult(this, actual, isEqual);
		}

		public override string Description => $"encoded equal to {expected}";
	}
	
	protected Constraint IsEncodedEqualTo<T>(IESExprCodec<T> codec, T value) =>
		new EncodedEqualToConstraint<T>(codec, value);
	
	protected void AssertCodecMatch<T>(IESExprCodec<T> codec, Expr expr, T value)
		where T : notnull {
		Assert.That(codec.Encode(value), Is.EqualTo(expr));
		Assert.That(codec.Decode(expr), IsEncodedEqualTo(codec, value));
	}
}
