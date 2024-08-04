package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A codec for optional values.
 * @param <T> The type of the optional value.
 */
public interface OptionalValueCodec<T> {
	/**
	 * Encode an optional value into an optional expression.
	 * @param value The optional value.
	 * @return The optional expression.
	 */
	Optional<ESExpr> encodeOptional(T value);

	/**
	 * Decode an optional expression into an optional value.
	 * @param expr The optional expression.
	 * @param path The path of the current value within the decoded object for diagnostic purposes.
	 * @return The optional value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decodeOptional(Optional<ESExpr> expr, @NotNull ESExprCodec.FailurePath path) throws DecodeException;

	/**
	 * An OptionalValueCodec for Optional values.
	 * @param <T> The element type.
	 */
	public static class ForOptional<T> implements OptionalValueCodec<Optional<T>> {
		/**
		 * Creates an OptionalValueCodec for Optional values.
		 * @param elementCodec A value codec for the element type.
		 */
		public ForOptional(ESExprCodec<T> elementCodec) {
			this.elementCodec = elementCodec;
		}

		private final ESExprCodec<T> elementCodec;

		@Override
		public Optional<ESExpr> encodeOptional(Optional<T> value) {
			return value.map(elementCodec::encode);
		}

		@Override
		public Optional<T> decodeOptional(Optional<ESExpr> expr, @NotNull ESExprCodec.FailurePath path) throws DecodeException {
			var expr2 = expr.orElse(null);
			if(expr2 == null) {
				return Optional.empty();
			}

			var value = elementCodec.decode(expr2, path);
			return Optional.of(value);
		}
	}
}
