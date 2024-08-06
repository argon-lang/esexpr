package dev.argon.esexpr;

import java.util.Set;
import java.util.Optional;

import dev.argon.esexpr.codecs.OptionalCodec;
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
	 * A codec for optional values.
	 * @param <T> The type of the optional value.
	 * @param itemCodec The underlying codec for the values.
	 * @return The codec.
	 */
	public static <T> ESExprCodec<Optional<T>> optionalCodec(ESExprCodec<T> itemCodec) {
		return new OptionalCodec<>(itemCodec);
	}


}
