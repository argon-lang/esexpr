using ESExpr.Runtime;

namespace ESExpr.Tests;

internal static class ExprAssertions {
	// Record equality considers +0 and -0 equal, so check float bits throughout
	// each constructor as well. NaNs use the explicit payload-preserving variants.
	internal static void AssertFloatBits(Expr expected, Expr actual) {
		switch(expected) {
			case Expr.Float16(var value):
				Assert.That(BitConverter.HalfToUInt16Bits(((Expr.Float16)actual).value), Is.EqualTo(BitConverter.HalfToUInt16Bits(value)));
				break;
			case Expr.Float32(var value):
				Assert.That(BitConverter.SingleToUInt32Bits(((Expr.Float32)actual).value), Is.EqualTo(BitConverter.SingleToUInt32Bits(value)));
				break;
			case Expr.Float64(var value):
				Assert.That(BitConverter.DoubleToUInt64Bits(((Expr.Float64)actual).value), Is.EqualTo(BitConverter.DoubleToUInt64Bits(value)));
				break;
			case Expr.Constructor(var name, var args, var kwargs):
				var constructor = (Expr.Constructor)actual;
				for(int i = 0; i < args.Count; ++i) AssertFloatBits(args[i], constructor.args[i]);
				foreach(var (key, value) in kwargs) AssertFloatBits(value, constructor.kwargs[key]);
				break;
		}
	}

}
