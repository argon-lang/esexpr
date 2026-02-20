package dev.argon.esexpr;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.math.BigInteger;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import dev.argon.esexpr.codecs.*;
import org.eclipse.collections.api.list.primitive.ImmutableByteList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.ImmutableShortList;

/**
 * Encodes and Decodes ESExpr values into concrete types.
 * @param <T> The concrete type.
 */
public interface ESExprCodec<T> {
	/**
	 * Gets the set of tags of values for this type.
	 * @return The set of tags.
	 */
	ESExprTagSet tags();

	/**
	 * Encode a value into an ESExpr.
	 * @param value The value to encode.
	 * @return The encoded ESExpr.
	 */
	ESExpr encode(T value);

	/**
	 * Decode an ESExpr into a value.
	 * @param expr The ESExpr to decode.
	 * @return The decoded value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	default T decode(ESExpr expr) throws DecodeException {
		return decode(expr, new FailurePath.Current());
	}

	/**
	 * Decode an ESExpr into a value.
	 * @param expr The ESExpr to decode.
	 * @param path The path of the current value within the decoded object for diagnostic purposes.
	 * @return The decoded value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decode(ESExpr expr, FailurePath path) throws DecodeException;

	/**
	 * Determines whether two values are equal when encoded as an `ESExpr`.
	 * @param x The first value.
	 * @param y The second value.
	 * @return true iff the values are equal when encoded.
	 */
	boolean isEncodedEqual(T x, T y);


	/**
	 * The path of a decode failure.
	 */
	public sealed interface FailurePath {
		/**
		 * Gets a sub-path for a positional argument.
		 * @param constructor The constructor name of the object containing the value indicated by the new subpath.
		 * @param index The index of the positional argument indicated by the new subpath.
		 * @return The sub-path.
		 */
		FailurePath append(String constructor, int index);

		/**
		 * Creates a path builder for a vararg.
		 * @param constructor The constructor name of the object containing the value indicated by the new subpath.
		 * @param offset The index offset of the positional argument indicated by the new subpath.
		 * @return The path builder.
		 */
		default VarargCodec.PositionalPathBuilder appenderWithOffset(String constructor, int offset) {
			return i -> append(constructor, offset + i);
		}

		/**
		 * Gets a sub-path for a keyword argument.
		 * @param constructor The constructor name of the object containing the value indicated by the new subpath.
		 * @param keyword The name of the keyword argument indicated by the new subpath.
		 * @return The sub-path.
		 */
		FailurePath append(String constructor, String keyword);

		/**
		 * Specifies the name of the constructor at the current path.
		 * @param constructor The constructor name.
		 * @return The sub-path.
		 */
		FailurePath withConstructor(String constructor);

		/**
		 * Indicates that the path ends at the current object.
		 */
		public record Current() implements FailurePath {
			@Override
			public FailurePath append(String constructor, int index) {
				return new Positional(constructor, index, this);
			}

			@Override
			public FailurePath append(String constructor, String keyword) {
				return new Keyword(constructor, keyword, this);
			}

			@Override
			public FailurePath withConstructor(String constructor) {
				return new Constructor(constructor);
			}
		}

		/**
		 * Indicates that the path ends at a constructor value.
		 * @param name The name of the constructor.
		 */
		public record Constructor(String name) implements FailurePath {
			@Override
			public FailurePath append(String constructor, int index) {
				return new Positional(constructor, index, new Current());
			}

			@Override
			public FailurePath append(String constructor, String keyword) {
				return new Keyword(constructor, keyword, new Current());
			}

			@Override
			public FailurePath withConstructor(String constructor) {
				return new Constructor(constructor);
			}
		}

		/**
		 * Indicates that the next part of the path is a positional argument.
		 * @param constructor The name of the constructor.
		 * @param index The index of the positional argument.
		 * @param next The next part of the path.
		 */
		public record Positional(String constructor, int index, FailurePath next) implements FailurePath {
			@Override
			public FailurePath append(String constructor, int index) {
				return new Positional(this.constructor, this.index, next.append(constructor, index));
			}

			@Override
			public FailurePath append(String constructor, String keyword) {
				return new Positional(this.constructor, index, next.append(constructor, keyword));
			}

			@Override
			public FailurePath withConstructor(String constructor) {
				return new Positional(this.constructor, index, next.withConstructor(constructor));
			}
		}

		/**
		 * Indicates that the next part of the path is a keyword argument.
		 * @param constructor The name of the constructor.
		 * @param keyword The name of the keyword argument.
		 * @param next The next part of the path.
		 */
		public record Keyword(String constructor, String keyword, FailurePath next) implements FailurePath {
			@Override
			public FailurePath append(String constructor, int index) {
				return new Keyword(this.constructor, this.keyword, next.append(constructor, index));
			}

			@Override
			public FailurePath append(String constructor, String keyword) {
				return new Keyword(this.constructor, keyword, next.append(constructor, keyword));
			}

			@Override
			public FailurePath withConstructor(String constructor) {
				return new Keyword(this.constructor, keyword, next.withConstructor(constructor));
			}}
	}

	/**
	 * A codec for optional values.
	 * @param <T> The type of the optional value.
	 * @param itemCodec The underlying codec for the values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.NULL }, unionWithTypeParameters = "T")
	public static <T> ESExprCodec<Optional<T>> optionalCodec(ESExprCodec<T> itemCodec) {
		return new OptionalCodec<>(itemCodec);
	}

	/**
	 * A codec for list values.
	 * @param <T> The type of the list elements.
	 * @param itemCodec The underlying codec for the values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(constructors = { "list" })
	public static <T> ESExprCodec<List<T>> listCodec(ESExprCodec<T> itemCodec) {
		return new ListCodec<>(itemCodec);
	}

	/**
	 * A codec for Boolean values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.BOOL })
	public static ESExprCodec<Boolean> booleanCodec() {
		return BooleanCodec.INSTANCE;
	}

	/**
	 * A codec for String values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.STR })
	public static ESExprCodec<String> stringCodec() {
		return StringCodec.INSTANCE;
	}

	/**
	 * A codec for Float values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.FLOAT32 })
	public static ESExprCodec<Float> floatCodec() {
		return FloatCodec.INSTANCE;
	}

	/**
	 * A codec for Double values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.FLOAT64 })
	public static ESExprCodec<Double> doubleCodec() {
		return DoubleCodec.INSTANCE;
	}

	/**
	 * A codec for signed Integer values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<Integer> signedIntegerCodec() {
		return SignedIntegerCodec.INSTANCE;
	}

	/**
	 * A codec for signed Long values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<Long> signedLongCodec() {
		return SignedLongCodec.INSTANCE;
	}

	/**
	 * A codec for signed Short values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<Short> signedShortCodec() {
		return SignedShortCodec.INSTANCE;
	}

	/**
	 * A codec for signed Byte values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<Byte> signedByteCodec() {
		return SignedByteCodec.INSTANCE;
	}

	/**
	 * A codec for unsigned Integer values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<UnsignedInteger> unsignedIntegerCodec() {
		return UnsignedIntegerCodec.INSTANCE;
	}

	/**
	 * A codec for unsigned Long values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<UnsignedLong> unsignedLongCodec() {
		return UnsignedLongCodec.INSTANCE;
	}

	/**
	 * A codec for BigInteger values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<BigInteger> bigIntegerCodec() {
		return BigIntegerCodec.INSTANCE;
	}

	/**
	 * A codec for byte array values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY8 })
	public static ESExprCodec<byte[]> byteArrayCodec() {
		return ByteArrayCodec.INSTANCE;
	}

	/**
	 * A codec for short array values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY16 })
	public static ESExprCodec<short[]> shortArrayCodec() {
		return ShortArrayCodec.INSTANCE;
	}

	/**
	 * A codec for int array values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY32 })
	public static ESExprCodec<int[]> intArrayCodec() {
		return IntArrayCodec.INSTANCE;
	}

	/**
	 * A codec for long array values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY64 })
	public static ESExprCodec<long[]> longArrayCodec() {
		return LongArrayCodec.INSTANCE;
	}

	/**
	 * A codec for ImmutableByteList values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY8 })
	public static ESExprCodec<ImmutableByteList> immutableByteListCodec() {
		return ImmutableByteListCodec.INSTANCE;
	}

	/**
	 * A codec for ImmutableShortList values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY16 })
	public static ESExprCodec<ImmutableShortList> immutableShortListCodec() {
		return ImmutableShortListCodec.INSTANCE;
	}

	/**
	 * A codec for ImmutableIntList values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY32 })
	public static ESExprCodec<ImmutableIntList> immutableIntListCodec() {
		return ImmutableIntListCodec.INSTANCE;
	}

	/**
	 * A codec for ImmutableLongList values.
	 * @return The codec.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY64 })
	public static ESExprCodec<ImmutableLongList> immutableLongListCodec() {
		return ImmutableLongListCodec.INSTANCE;
	}


	/**
	 * Creates an {@link ESExprCodec} for encoding and decoding maps.
	 *
	 * @param <K> The type of the keys in the map.
	 * @param <V> The type of the values in the map.
	 * @param keyCodec The codec used for encoding and decoding the keys in the map.
	 * @param valueCodec The codec used for encoding and decoding the values in the map.
	 * @return An {@link ESExprCodec} instance configured to handle maps with the specified key and value types.
	 */
	@TypeClassInstance
	@ESExprCodecTags(constructors = { "map" })
	public static <K, V> ESExprCodec<Map<K, V>> mapCodec(ESExprCodec<K> keyCodec, ESExprCodec<V> valueCodec) {
		return new MapCodec<>(keyCodec, valueCodec);
	}

	/**
	 * Creates an {@link ESExprCodec} for encoding and decoding immutable maps.
	 *
	 * @param <K> The type of the keys in the map.
	 * @param <V> The type of the values in the map.
	 * @param keyCodec The codec used for encoding and decoding the keys in the map.
	 * @param valueCodec The codec used for encoding and decoding the values in the map.
	 * @return An {@link ESExprCodec} instance configured to handle immutable maps with the specified key and value types.
	 */
	@TypeClassInstance
	@ESExprCodecTags(constructors = { "map" })
	public static <K, V> ESExprCodec<ImmutableMap<K, V>> immutableMapCodec(ESExprCodec<K> keyCodec, ESExprCodec<V> valueCodec) {
		return new ImmutableMapCodec<>(keyCodec, valueCodec);
	}


	/**
	 * Creates an {@link ESExprCodec} for encoding and decoding sets of elements.
	 *
	 * @param <T> The type of elements contained in the set.
	 * @param elementCodec The codec used for encoding and decoding individual elements within the set.
	 * @return An {@link ESExprCodec} instance configured to handle sets of the specified element type.
	 */
	@TypeClassInstance
	@ESExprCodecTags(constructors = { "set" })
	public static <T> ESExprCodec<Set<T>> setCodec(ESExprCodec<T> elementCodec) {
		return new SetCodec<>(elementCodec);
	}

	/**
	 * Creates an {@link ESExprCodec} for encoding and decoding immutable sets.
	 *
	 * @param <T> The type of the elements in the set.
	 * @param elementCodec The codec used for encoding and decoding the individual elements of the set.
	 * @return An {@link ESExprCodec} instance configured to handle immutable sets of the specified element type.
	 */
	@TypeClassInstance
	@ESExprCodecTags(constructors = { "set" })
	public static <T> ESExprCodec<ImmutableSet<T>> immutableSetCodec(ESExprCodec<T> elementCodec) {
		return new ImmutableSetCodec<>(elementCodec);
	}

}
