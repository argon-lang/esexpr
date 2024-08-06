package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A codec for dictionary argument values.
 * @param <T> The type of the dictionary argument value.
 */
public interface DictCodec<T> {
	/**
	 * Encode a dictionary argument value into a map of expressions.
	 * @param value The dictionary argument value.
	 * @return The expressions.
	 */
	Map<String, ESExpr> encodeDict(T value);

	/**
	 * Decode a map of expressions into a dictionary argument value.
	 * @param exprs The expressions.
	 * @param pathBuilder A path builder of the current expressions within the decoded object for diagnostic purposes.
	 * @return The dictionary argument value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decodeDict(Map<String, ESExpr> exprs, @NotNull KeywordPathBuilder pathBuilder) throws DecodeException;

	/**
	 * Builds paths for elements of a dictionary argument.
	 */
	public static interface KeywordPathBuilder {
		/**
		 * Build a path for the element with the provided keyword.
		 * @param keyword The keyword of the element.
		 * @return A path for the element with the keyword.
		 */
		@NotNull ESExprCodec.FailurePath pathAt(String keyword);
	}
}
