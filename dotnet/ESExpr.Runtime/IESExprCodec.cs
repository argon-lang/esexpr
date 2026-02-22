using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Numerics;
using ESExpr.Runtime.Codecs;

namespace ESExpr.Runtime;

public interface IESExprCodec<T> {
	ESExprTagSet Tags { get; }
	bool IsEncodedEqual(T a, T b);
	Expr Encode(T value);
	T Decode(Expr expr, DecodeFailurePath path);

	sealed T Decode(Expr expr) => Decode(expr, new DecodeFailurePath.Current());
	
}

public static class IESExprCodec {
	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Bool ])]
	public static IESExprCodec<bool> BoolCodec { get; } = new BoolCodec();
	
	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Str ])]
	public static IESExprCodec<string> StrCodec { get; } = new StringCodec();
	
	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Float64 ])]
	public static IESExprCodec<double> DoubleCodec { get; } = new DoubleCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Float32 ])]
	public static IESExprCodec<float> FloatCodec { get; } = new FloatCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Float16 ])]
	public static IESExprCodec<Half> HalfCodec { get; } = new HalfCodec();
	
	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<sbyte> SByteCodec { get; } = new SByteCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<byte> ByteCodec { get; } = new ByteCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<short> ShortCodec { get; } = new ShortCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<ushort> UShortCodec { get; } = new UShortCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<int> IntCodec { get; } = new IntCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<uint> UIntCodec { get; } = new UIntCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<long> LongCodec { get; } = new LongCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<ulong> ULongCodec { get; } = new ULongCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<Int128> Int128Codec { get; } = new Int128Codec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<UInt128> UInt128Codec { get; } = new UInt128Codec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
	public static IESExprCodec<BigInteger> BigIntegerCodec { get; } = new BigIntegerCodec();
	
	[TypeClassInstance]
	[ESExprTags(Constructors = ["list"])]
	public static IESExprCodec<List<T>> ListCodec<T>(IESExprCodec<T> itemCodec) => new ListCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["list"])]
	public static IESExprCodec<ImmutableList<T>> ImmutableListCodec<T>(IESExprCodec<T> itemCodec) => new ImmutableListCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["list"])]
	public static IESExprCodec<IList<T>> InterfaceListCodec<T>(IESExprCodec<T> itemCodec) => new InterfaceListCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["list"])]
	public static IESExprCodec<IReadOnlyList<T>> ReadOnlyListCodec<T>(IESExprCodec<T> itemCodec) => new ReadOnlyListCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["list"])]
	public static IESExprCodec<IImmutableList<T>> InterfaceImmutableListCodec<T>(IESExprCodec<T> itemCodec) => new InterfaceImmutableListCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["list"])]
	public static IESExprCodec<VList<T>> VListCodec<T>(IESExprCodec<T> itemCodec) => new VListCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array8 ])]
	public static IESExprCodec<ImmutableArray<byte>> ImmutableArrayByteCodec { get; } = new ImmutableArrayByteCodec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array16 ])]
	public static IESExprCodec<ImmutableArray<ushort>> ImmutableArrayUInt16Codec { get; } = new ImmutableArrayUInt16Codec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array32 ])]
	public static IESExprCodec<ImmutableArray<uint>> ImmutableArrayUInt32Codec { get; } = new ImmutableArrayUInt32Codec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array64 ])]
	public static IESExprCodec<ImmutableArray<ulong>> ImmutableArrayUInt64Codec { get; } = new ImmutableArrayUInt64Codec();

	[TypeClassInstance]
	[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array128 ])]
	public static IESExprCodec<ImmutableArray<UInt128>> ImmutableArrayUInt128Codec { get; } = new ImmutableArrayUInt128Codec();

	[TypeClassInstance]
	[ESExprTags(Constructors = ["set"])]
	public static IESExprCodec<HashSet<T>> SetCodec<T>(IESExprCodec<T> itemCodec) => new SetCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["set"])]
	public static IESExprCodec<ImmutableHashSet<T>> ImmutableHashSetCodec<T>(IESExprCodec<T> itemCodec) => new ImmutableHashSetCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["set"])]
	public static IESExprCodec<IReadOnlySet<T>> ReadOnlySetCodec<T>(IESExprCodec<T> itemCodec) => new ReadOnlySetCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["set"])]
	public static IESExprCodec<IImmutableSet<T>> InterfaceImmutableSetCodec<T>(IESExprCodec<T> itemCodec) => new InterfaceImmutableSetCodec<T>(itemCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["map"])]
	public static IESExprCodec<Dictionary<K, V>> MapCodec<K, V>(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) where K : notnull => new MapCodec<K, V>(keyCodec, valueCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["map"])]
	public static IESExprCodec<ImmutableDictionary<K, V>> ImmutableMapCodec<K, V>(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) where K : notnull => new ImmutableMapCodec<K, V>(keyCodec, valueCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["map"])]
	public static IESExprCodec<IReadOnlyDictionary<K, V>> ReadOnlyMapCodec<K, V>(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) where K : notnull => new ReadOnlyMapCodec<K, V>(keyCodec, valueCodec);

	[TypeClassInstance]
	[ESExprTags(Constructors = ["map"])]
	public static IESExprCodec<IImmutableDictionary<K, V>> InterfaceImmutableMapCodec<K, V>(IESExprCodec<K> keyCodec, IESExprCodec<V> valueCodec) where K : notnull => new InterfaceImmutableMapCodec<K, V>(keyCodec, valueCodec);
	
	[TypeClassInstance]
	public static IESExprCodec<ImmutableDictionary<string, T>> ImmutableDictionaryCodec<T>(IESExprCodec<T> itemCodec) => new ImmutableDictionaryCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IESExprCodec<IDictionary<string, T>> InterfaceDictionaryCodec<T>(IESExprCodec<T> itemCodec) => new InterfaceDictionaryCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IESExprCodec<IReadOnlyDictionary<string, T>> ReadOnlyDictionaryCodec<T>(IESExprCodec<T> itemCodec) => new ReadOnlyDictionaryCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IESExprCodec<IImmutableDictionary<string, T>> InterfaceImmutableDictionaryCodec<T>(IESExprCodec<T> itemCodec) => new InterfaceImmutableDictionaryCodec<T>(itemCodec);
	
	
	
	[TypeClassInstance]
	[ESExprTags(Scalar = [ESExprTag.ScalarType.Str])]
	public static IESExprCodec<T> EnumCodec<T>() where T : struct, Enum {
		return SimpleEnumCodec<T>.Instance;
	}
}

