using System.Collections.Immutable;
using System.Numerics;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class RuntimePrimitiveCodecTests : TestBase {
	private static void CheckInteger<T>(IESExprCodec<T> codec) where T : IBinaryInteger<T>, IMinMaxValue<T> {
		foreach(var value in new[] { T.MinValue, T.Zero, T.MaxValue }) {
			var expr = new Expr.Int(BigInteger.CreateChecked(value));
			Assert.That(codec.Encode(value), Is.EqualTo(expr));
			Assert.That(codec.Decode(expr), Is.EqualTo(value));
			Assert.That(codec.IsEncodedEqual(value, value), Is.True);
		}
		Assert.That(codec.Tags.Contains(new ESExprTag.Int()), Is.True);
		Assert.That(codec.IsEncodedEqual(T.Zero, T.One), Is.False);
		var path = new DecodeFailurePath.Constructor("number");
		foreach(var expr in new Expr[] { new Expr.Str("1"), new Expr.Int(BigInteger.CreateChecked(T.MinValue) - 1), new Expr.Int(BigInteger.CreateChecked(T.MaxValue) + 1) }) {
			Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(expr, path))!.Path, Is.EqualTo(path));
		}
	}

	[Test]
	public void IntegerCodecsRespectBounds() {
		CheckInteger(IESExprCodec.SByteCodec); CheckInteger(IESExprCodec.ByteCodec);
		CheckInteger(IESExprCodec.ShortCodec); CheckInteger(IESExprCodec.UShortCodec);
		CheckInteger(IESExprCodec.IntCodec); CheckInteger(IESExprCodec.UIntCodec);
		CheckInteger(IESExprCodec.LongCodec); CheckInteger(IESExprCodec.ULongCodec);
		CheckInteger(IESExprCodec.Int128Codec); CheckInteger(IESExprCodec.UInt128Codec);
	}

	[Test]
	public void BigIntegerHasNoFixedWidthLimit() {
		var codec = IESExprCodec.BigIntegerCodec;
		foreach(var value in new[] { BigInteger.Zero, BigInteger.One << 1024, -(BigInteger.One << 1024) })
			AssertCodecMatch(codec, new Expr.Int(value), value);
		Assert.That(codec.IsEncodedEqual(0, 1), Is.False);
		Assert.That(codec.Tags.Contains(new ESExprTag.Int()), Is.True);
		Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Str("1")));
	}

	[Test]
	public void FloatingCodecsPreserveNaNPayloadsAndSignedZero() {
		CheckFloat(IESExprCodec.HalfCodec, (Half)0, BitConverter.UInt16BitsToHalf(0x8000), BitConverter.UInt16BitsToHalf(0x7E01), new Expr.Float16NaN(0x7E01), new ESExprTag.Float16());
		CheckFloat(IESExprCodec.FloatCodec, 0f, BitConverter.UInt32BitsToSingle(0x80000000), BitConverter.UInt32BitsToSingle(0x7FC00001), new Expr.Float32NaN(0x7FC00001), new ESExprTag.Float32());
		CheckFloat(IESExprCodec.DoubleCodec, 0d, BitConverter.UInt64BitsToDouble(0x8000000000000000), BitConverter.UInt64BitsToDouble(0x7FF8000000000001), new Expr.Float64NaN(0x7FF8000000000001), new ESExprTag.Float64());
	}

	private static void CheckFloat<T>(IESExprCodec<T> codec, T zero, T negativeZero, T nan, Expr nanExpr, ESExprTag tag) {
		Assert.That(codec.Tags.Contains(tag), Is.True);
		Assert.That(codec.IsEncodedEqual(zero, negativeZero), Is.False);
		foreach(var value in new[] { zero, negativeZero, nan })
			Assert.That(codec.IsEncodedEqual(value, codec.Decode(codec.Encode(value))), Is.True);
		Assert.That(codec.IsEncodedEqual(nan, codec.Decode(nanExpr)), Is.True);
		Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Bool(false)));
	}

	[Test]
	public void ScalarCodecsRejectWrongTypes() {
		Assert.That(IESExprCodec.BoolCodec.Tags.Contains(new ESExprTag.Bool()), Is.True);
		AssertCodecMatch(IESExprCodec.BoolCodec, new Expr.Bool(false), false);
		Assert.That(IESExprCodec.BoolCodec.IsEncodedEqual(false, true), Is.False);
		Assert.That(IESExprCodec.StrCodec.IsEncodedEqual("a", "b"), Is.False);
		Assert.Throws<DecodeException>(() => IESExprCodec.BoolCodec.Decode(new Expr.Int(0)));
		Assert.Throws<DecodeException>(() => IESExprCodec.StrCodec.Decode(new Expr.Bool(false)));
	}

	[Test]
	public void FixedArrayCodecsUseTheCorrectWidth() {
		CheckArray(IESExprCodec.ImmutableArrayByteCodec, (byte)1, byte.MaxValue, x => new Expr.Array8(x));
		CheckArray(IESExprCodec.ImmutableArrayUInt16Codec, (ushort)1, ushort.MaxValue, x => new Expr.Array16(x));
		CheckArray(IESExprCodec.ImmutableArrayUInt32Codec, 1u, uint.MaxValue, x => new Expr.Array32(x));
		CheckArray(IESExprCodec.ImmutableArrayUInt64Codec, 1ul, ulong.MaxValue, x => new Expr.Array64(x));
		CheckArray(IESExprCodec.ImmutableArrayUInt128Codec, (UInt128)1, UInt128.MaxValue, x => new Expr.Array128(x));
	}

	private void CheckArray<T>(IESExprCodec<ImmutableArray<T>> codec, T first, T last, Func<ImmutableArray<T>, Expr> makeExpr) {
		foreach(var array in new[] { ImmutableArray<T>.Empty, ImmutableArray.Create(first, last) }) {
			var expr = makeExpr(array);
			AssertCodecMatch(codec, expr, array);
			Assert.That(codec.Tags.Contains(expr.Tag), Is.True);
			var copy = makeExpr(array.ToArray().ToImmutableArray());
			Assert.That(copy.GetHashCode(), Is.EqualTo(expr.GetHashCode()));
			Assert.That(copy.ToString(), Is.EqualTo(expr.ToString()));
			Assert.That(expr.Equals(null), Is.False);
		}
		Assert.That(codec.IsEncodedEqual(ImmutableArray.Create(first), ImmutableArray.Create(last)), Is.False);
		Assert.That(makeExpr(ImmutableArray.Create(first)), Is.Not.EqualTo(makeExpr(ImmutableArray.Create(last))));
		Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Bool(false)));
	}
}
