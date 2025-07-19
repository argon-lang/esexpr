package dev.argon.esexpr;


import java.util.Optional;

/**
 * A codec for optional values.
 * @param <T> The type of the optional value.
 * @param <E> The type of the optional elements.
 */
public interface OptionalValueCodec<T, E> {
	/**
	 * Determines if two optional values, after being encoded, are equal.
	 *
	 * @param x The first optional value to compare.
	 * @param y The second optional value to compare.
	 * @return true if the two values are encoded equally, false otherwise.
	 */
	boolean isEncodedEqual(T x, T y);

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
	T decodeOptional(Optional<ESExpr> expr, ESExprCodec.FailurePath path) throws DecodeException;
}
