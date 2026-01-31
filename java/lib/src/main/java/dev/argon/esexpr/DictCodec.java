package dev.argon.esexpr;


import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

/**
 * A codec for dictionary argument values.
 * @param <T> The type of the dictionary argument value.
 * @param <E> The type of the dictionary key elements.
 */
public interface DictCodec<T, E> {
	/**
	 * Compares two dictionary argument values to determine if they are encoded equivalently.
	 *
	 * @param x The first encoded dictionary argument value to compare.
	 * @param y The second encoded dictionary argument value to compare.
	 * @return true if the two dictionary argument values are equivalent when encoded, otherwise false.
	 */
	boolean isEncodedEqual(T x, T y);

	/**
	 * Encode a dictionary argument value into a map of expressions.
	 * @param value The dictionary argument value.
	 * @param builder The map to which any expressions will be added.
	 */
	void encodeDict(T value, ImmutableMap.Builder<String, ESExpr> builder);

	/**
	 * Decode a map of expressions into a dictionary argument value.
	 * @param exprs The expressions.
	 * @param pathBuilder A path builder of the current expressions within the decoded object for diagnostic purposes.
	 * @return The dictionary argument value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decodeDict(Map<String, ESExpr> exprs, KeywordPathBuilder pathBuilder) throws DecodeException;

	/**
	 * Builds paths for elements of a dictionary argument.
	 */
	public static interface KeywordPathBuilder {
		/**
		 * Build a path for the element with the provided keyword.
		 * @param keyword The keyword of the element.
		 * @return A path for the element with the keyword.
		 */
		ESExprCodec.FailurePath pathAt(String keyword);
	}
}
