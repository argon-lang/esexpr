package dev.argon.esexpr;

import java.math.BigInteger;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Encodes and Decodes ESExpr values into concrete types.
 * @param <T> The concrete type.
 */
public abstract class ESExprCodec<T> {
	/**
	 * Creates a codec.
	 */
	public ESExprCodec() {
	}

	/**
	 * Gets the set of tags of values for this type.
	 * @return The set of tags.
	 */
	public abstract @NotNull Set<@NotNull ESExprTag> tags();

	/**
	 * Encode a value into an ESExpr.
	 * @param value The value to encode.
	 * @return The encoded ESExpr.
	 */
	public abstract @NotNull ESExpr encode(@NotNull T value);

	/**
	 * Decode an ESExpr into a value.
	 * @param expr The ESExpr to decode.
	 * @return The decoded value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	public final @NotNull T decode(@NotNull ESExpr expr) throws DecodeException {
		return decode(expr, new FailurePath.Current());
	}

	/**
	 * Decode an ESExpr into a value.
	 * @param expr The ESExpr to decode.
	 * @param path The path of the current value within the decoded object for diagnostic purposes.
	 * @return The decoded value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	public abstract @NotNull T decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException;

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
	 * A codec for boolean values.
	 */
	public static final ESExprCodec<Boolean> BOOLEAN_CODEC = new ESExprCodec<Boolean>() {
		@Override
		public @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Bool());
		}

		@Override
		public @NotNull ESExpr encode(@NotNull Boolean value) {
			return new ESExpr.Bool(value);
		}

		@Override
		public @NotNull Boolean decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Bool(var b)) {
				return b;
			}
			else {
				throw new DecodeException("Expected a boolean value", path);
			}
		}
	};

	/**
	 * A codec for signed byte values.
	 */
	public static final ESExprCodec<Byte> SIGNED_BYTE_CODEC = new IntCodecBase<Byte>(BigInteger.valueOf(Byte.MIN_VALUE), BigInteger.valueOf(Byte.MAX_VALUE)) {
		@Override
		protected @NotNull Byte fromBigInt(@NotNull BigInteger value) {
			return value.byteValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Byte value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for unsigned byte values.
	 */
	public static final ESExprCodec<Byte> UNSIGNED_BYTE_CODEC = new IntCodecBase<Byte>(BigInteger.ZERO, BigInteger.valueOf(0xFF)) {
		@Override
		protected @NotNull Byte fromBigInt(@NotNull BigInteger value) {
			return value.byteValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Byte value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for signed short values.
	 */
	public static final ESExprCodec<Short> SIGNED_SHORT_CODEC = new IntCodecBase<Short>(BigInteger.valueOf(Short.MIN_VALUE), BigInteger.valueOf(Short.MAX_VALUE)) {
		@Override
		protected @NotNull Short fromBigInt(@NotNull BigInteger value) {
			return value.shortValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Short value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for unsigned short values.
	 */
	public static final ESExprCodec<Short> UNSIGNED_SHORT_CODEC = new IntCodecBase<Short>(BigInteger.ZERO, BigInteger.valueOf(0xFFFF)) {
		@Override
		protected @NotNull Short fromBigInt(@NotNull BigInteger value) {
			return value.shortValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Short value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for signed int values.
	 */
	public static final ESExprCodec<Integer> SIGNED_INT_CODEC = new IntCodecBase<Integer>(BigInteger.valueOf(Integer.MIN_VALUE), BigInteger.valueOf(Integer.MAX_VALUE)) {
		@Override
		protected @NotNull Integer fromBigInt(@NotNull BigInteger value) {
			return value.intValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Integer value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for unsigned int values.
	 */
	public static final ESExprCodec<Integer> UNSIGNED_INT_CODEC = new IntCodecBase<Integer>(BigInteger.ZERO, BigInteger.valueOf(0xFFFFFFFFL)) {
		@Override
		protected @NotNull Integer fromBigInt(@NotNull BigInteger value) {
			return value.intValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Integer value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for signed long values.
	 */
	public static final ESExprCodec<Long> SIGNED_LONG_CODEC = new IntCodecBase<Long>(BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE)) {
		@Override
		protected @NotNull Long fromBigInt(@NotNull BigInteger value) {
			return value.longValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Long value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for unsigned long values.
	 */
	public static final ESExprCodec<Long> UNSIGNED_LONG_CODEC = new IntCodecBase<Long>(BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)) {
		@Override
		protected @NotNull Long fromBigInt(@NotNull BigInteger value) {
			return value.longValue();
		}

		@Override
		protected @NotNull BigInteger toBigInt(@NotNull Long value) {
			return BigInteger.valueOf(value);
		}
	};


	/**
	 * A codec for bigint values.
	 */
	public static final ESExprCodec<BigInteger> BIG_INTEGER_CODEC = new ESExprCodec<>() {
		@Override
		public final @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Int());
		}

		@Override
		public final @NotNull ESExpr encode(@NotNull BigInteger value) {
			return new ESExpr.Int(value);
		}

		@Override
		public final @NotNull BigInteger decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Int(var i)) {
				return i;
			}
			else {
				throw new DecodeException("Expected an integer value", path);
			}
		}
	};

	/**
	 * A codec for positive bigint values.
	 */
	public static final ESExprCodec<BigInteger> NAT_CODEC = new ESExprCodec<>() {
		@Override
		public final @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Int());
		}

		@Override
		public final @NotNull ESExpr encode(@NotNull BigInteger value) {
			return new ESExpr.Int(value);
		}

		@Override
		public final @NotNull BigInteger decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Int(var i)) {
				if(i.compareTo(BigInteger.ZERO) < 0) {
					throw new DecodeException("Integer value out of range", path);
				}

				return i;
			}
			else {
				throw new DecodeException("Expected an integer value", path);
			}
		}
	};

	/**
	 * A codec for string values.
	 */
	public static final ESExprCodec<String> STRING_CODEC = new ESExprCodec<>() {
		@Override
		public @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Str());
		}

		@Override
		public @NotNull ESExpr encode(@NotNull String value) {
			return new ESExpr.Str(value);
		}

		@Override
		public @NotNull String decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Str(var s)) {
				return s;
			}
			else {
				throw new DecodeException("Expected a string value", path);
			}
		}
	};


	/**
	 * A codec for binary values.
	 */
	public static final ESExprCodec<byte[]> BYTE_ARRAY_CODEC = new ESExprCodec<>() {
		@Override
		public @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Binary());
		}

		@Override
		public @NotNull ESExpr encode(byte @NotNull[] value) {
			return new ESExpr.Binary(value);
		}

		@Override
		public byte @NotNull[] decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Binary(var b)) {
				return b;
			}
			else {
				throw new DecodeException("Expected a binary value", path);
			}
		}
	};


	/**
	 * A codec for float values.
	 */
	public static final ESExprCodec<Float> FLOAT_CODEC = new ESExprCodec<>() {
		@Override
		public @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Float32());
		}

		@Override
		public @NotNull ESExpr encode(@NotNull Float value) {
			return new ESExpr.Float32(value);
		}

		@Override
		public @NotNull Float decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Float32(var f)) {
				return f;
			}
			else {
				throw new DecodeException("Expected a float value", path);
			}
		}
	};
	
	/**
	 * A codec for double values.
	 */
	public static final ESExprCodec<Double> DOUBLE_CODEC = new ESExprCodec<>() {
		@Override
		public @NotNull Set<@NotNull ESExprTag> tags() {
			return Set.of(new ESExprTag.Float64());
		}

		@Override
		public @NotNull ESExpr encode(@NotNull Double value) {
			return new ESExpr.Float64(value);
		}

		@Override
		public @NotNull Double decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			if(expr instanceof ESExpr.Float64(var d)) {
				return d;
			}
			else {
				throw new DecodeException("Expected a double value", path);
			}
		}
	};


	/**
	 * A codec for list values.
	 * @param <T> The type of the list elements.
	 * @param itemCodec The underlying codec for the values.
	 * @return The codec.
	 */
	public static <T> ESExprCodec<List<T>> listCodec(ESExprCodec<T> itemCodec) {
		return new ESExprCodec<List<T>>() {	
			@Override
			public @NotNull Set<@NotNull ESExprTag> tags() {
				return Set.of(new ESExprTag.Constructor("list"));
			}
	
			@Override
			public @NotNull ESExpr encode(@NotNull List<T> value) {
				return new ESExpr.Constructor("list", value.stream().map(itemCodec::encode).toList(), new HashMap<>());
			}
	
			@Override
			public @NotNull List<T> decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
				if(expr instanceof ESExpr.Constructor(var name, var args, var kwargs) && name.equals("list")) {
					if(kwargs.size() > 0) {
						throw new DecodeException("Unexpected keyword arguments for list.", path.withConstructor("list"));
					}
	
					List<T> res = new ArrayList<T>(args.size());
					int i = 0;
					for(ESExpr item : args) {
						res.add(itemCodec.decode(item, path.append("list", i)));

						++i;
					}
					return res;
				}
				else {
					throw new DecodeException("Expected a list constructor", path);
				}
			}
		};
	}

	/**
	 * A codec for optional values.
	 * @param <T> The type of the optional value.
	 * @param itemCodec The underlying codec for the values.
	 * @return The codec.
	 */
	public static <T> ESExprCodec<Optional<T>> optionalCodec(ESExprCodec<T> itemCodec) {
		return new ESExprCodec<Optional<T>>() {	
			@Override
			public @NotNull Set<@NotNull ESExprTag> tags() {
				var tags = new HashSet<ESExprTag>();
				tags.add(new ESExprTag.Null());
				tags.addAll(itemCodec.tags());
				return tags;
			}
	
			@Override
			public @NotNull ESExpr encode(@NotNull Optional<T> value) {
				return value.map(itemCodec::encode).orElseGet(() -> new ESExpr.Null());
			}
	
			@Override
			public @NotNull Optional<T> decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
				if(expr instanceof ESExpr.Null) {
					return Optional.empty();
				}
				else {
					return Optional.of(itemCodec.decode(expr));
				}
			}
		};
	}

	
}
