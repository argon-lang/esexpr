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
}
