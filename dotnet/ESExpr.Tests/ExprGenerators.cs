using System.Collections.Immutable;
using System.Numerics;
using CsCheck;
using ESExpr.Runtime;

namespace ESExpr.Tests;

internal static class ExprGenerators {
	// UTF-8 preserves Unicode scalar values, but not unpaired UTF-16 surrogates.
	private static readonly Gen<string> Scalar = Gen.OneOf(Gen.Int[0, 0xD7FF], Gen.Int[0xE000, 0x10FFFF])
		.Select(char.ConvertFromUtf32);
	private static readonly Gen<string> Text = Scalar.Array[0, 64].Select(parts => string.Concat(parts));
	internal static readonly Gen<string> Name = Gen.OneOf(
		Gen.OneOfConst("list", "map", "set", "string-table", "custom", "key", ""), Text);

	private static readonly Gen<BigInteger> Integer = Gen.OneOf(
		Gen.Byte.Array[0, 64].Select(bytes => new BigInteger(bytes)),
		Gen.OneOfConst<BigInteger>(0, 1, -1, 15, 16, 127, 128, 2047, 2048, BigInteger.One << 256));
	private static readonly Gen<BigInteger> NullLevel = Gen.OneOf(
		Gen.Byte.Array[0, 64].Select(bytes => new BigInteger(bytes, isUnsigned: true)),
		Gen.OneOfConst<BigInteger>(0, 1, 2, 3, 15, 16, 127, 128, BigInteger.One << 256));
	private static readonly Gen<UInt128> UInt128 = Gen.Select(Gen.ULong, Gen.ULong,
		(high, low) => ((UInt128)high << 64) | low);

	internal static readonly (string Name, Gen<Expr> Generator)[] Primitives = [
		("Bool", Gen.Bool.Select(value => (Expr)new Expr.Bool(value))),
		("Int", Integer.Select(value => (Expr)new Expr.Int(value))),
		("Str", Text.Select(value => (Expr)new Expr.Str(value))),
		("Float16", Gen.OneOf(Gen.UShort, Gen.OneOfConst<ushort>(0, 1, 0x8000, 0x03FF, 0x0400, 0x7BFF, 0x7C00, 0xFC00, 0x7C01, 0xFFFF)).Select(Float16)),
		("Float32", Gen.OneOf(Gen.UInt, Gen.OneOfConst<uint>(0, 1, 0x80000000, 0x007FFFFF, 0x00800000, 0x7F7FFFFF, 0x7F800000, 0xFF800000, 0x7F800001, uint.MaxValue)).Select(Float32)),
		("Float64", Gen.OneOf(Gen.ULong, Gen.OneOfConst<ulong>(0, 1, 0x8000000000000000, 0x000FFFFFFFFFFFFF, 0x0010000000000000, 0x7FEFFFFFFFFFFFFF, 0x7FF0000000000000, 0xFFF0000000000000, 0x7FF0000000000001, ulong.MaxValue)).Select(Float64)),
		("Array8", Gen.Byte.Array[0, 32].Select(values => (Expr)new Expr.Array8(values.ToImmutableArray()))),
		("Array16", Gen.UShort.Array[0, 32].Select(values => (Expr)new Expr.Array16(values.ToImmutableArray()))),
		("Array32", Gen.UInt.Array[0, 32].Select(values => (Expr)new Expr.Array32(values.ToImmutableArray()))),
		("Array64", Gen.ULong.Array[0, 32].Select(values => (Expr)new Expr.Array64(values.ToImmutableArray()))),
		("Array128", UInt128.Array[0, 32].Select(values => (Expr)new Expr.Array128(values.ToImmutableArray()))),
		("Null", NullLevel.Select(level => (Expr)new Expr.Null(level))),
	];

	private static readonly Gen<Expr> Primitive = Gen.OneOf(Primitives.Select(item => item.Generator).ToArray());
	internal static readonly Gen<Expr> Expression = Gen.Recursive<Expr>((depth, child) => {
		if(depth == 3) return Primitive;
		return Gen.OneOf(Primitive, Gen.Select(
			Name, child.Array[0, 4], Gen.Dictionary(Name, child)[0, 4],
			(name, args, kwargs) => (Expr)new Expr.Constructor(name, args.ToImmutableList(), kwargs.ToImmutableDictionary())));
	});

	internal static Expr Float16(ushort bits) {
		var value = BitConverter.UInt16BitsToHalf(bits);
		return Half.IsNaN(value) ? new Expr.Float16NaN(bits) : new Expr.Float16(value);
	}

	internal static Expr Float32(uint bits) {
		var value = BitConverter.UInt32BitsToSingle(bits);
		return float.IsNaN(value) ? new Expr.Float32NaN(bits) : new Expr.Float32(value);
	}

	internal static Expr Float64(ulong bits) {
		var value = BitConverter.UInt64BitsToDouble(bits);
		return double.IsNaN(value) ? new Expr.Float64NaN(bits) : new Expr.Float64(value);
	}
}
