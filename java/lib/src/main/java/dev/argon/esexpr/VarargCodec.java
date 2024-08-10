package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A codec for variable argument values.
 * @param <T> The type of the variable argument value.
 */
public interface VarargCodec<T> {
	/**
	 * Encode a variable argument value into a list of expressions.
	 * @param value The variable argument value.
	 * @return The expressions.
	 */
	List<ESExpr> encodeVararg(T value);

	/**
	 * Decode a list of expressions into a variable argument value.
	 * @param exprs The expressions.
	 * @param pathBuilder A path builder of the current expressions within the decoded object for diagnostic purposes.
	 * @return The variable argument value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decodeVararg(List<ESExpr> exprs, @NotNull PositionalPathBuilder pathBuilder) throws DecodeException;

	/**
	 * Builds paths for elements of a variable argument.
	 */
	public static interface PositionalPathBuilder {
		/**
		 * Build a path for the element at index.
		 * @param index The index of the element.
		 * @return A path for the element at the index.
		 */
		@NotNull ESExprCodec.FailurePath pathAt(int index);
	}
}
