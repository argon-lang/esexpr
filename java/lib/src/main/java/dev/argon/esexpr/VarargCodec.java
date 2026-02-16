package dev.argon.esexpr;


import com.google.common.collect.ImmutableList;
import dev.argon.esexpr.codecs.ListVarargCodec;

import java.util.Deque;
import java.util.List;

/**
 * A codec for variable argument values.
 * @param <T> The type of the variable argument value.
 * @param <E> The type of the repeated elements.
 */
public interface VarargCodec<T, E> {
	/**
	 * Determines whether two variable argument values are encoded equally.
	 *
	 * @param x The first variable argument value.
	 * @param y The second variable argument value.
	 * @return true if the two values are encoded equally, false otherwise.
	 */
	boolean isEncodedEqual(T x, T y);

	/**
	 * Encode a variable argument value into a list of expressions.
	 * @param value The variable argument value.
	 * @param exprs The list to which any expressions will be appended.
	 */
	void encodeVararg(T value, ImmutableList.Builder<ESExpr> exprs);

	/**
	 * Decode a list of expressions into a variable argument value.
	 * @param exprs The expressions.
	 * @param pathBuilder A path builder of the current expressions within the decoded object for diagnostic purposes.
	 * @return The variable argument value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decodeVararg(Deque<ESExpr> exprs, PositionalPathBuilder pathBuilder) throws DecodeException;

	/**
	 * Builds paths for elements of a variable argument.
	 */
	public static interface PositionalPathBuilder {
		/**
		 * Build a path for the element at index.
		 * @param index The index of the element.
		 * @return A path for the element at the index.
		 */
		ESExprCodec.FailurePath pathAt(int index);
	}

	/**
	 * Creates a variable argument codec for a list of elements of type {@code T}.
	 *
	 * @param <T> The type of the elements in the list.
	 * @param itemCodec The codec used for encoding and decoding individual elements.
	 * @return A {@code VarargCodec} capable of encoding a list of elements of type {@code T}.
	 */
	@TypeClassInstance
	static <T> VarargCodec<List<T>, T> listCodec(ESExprCodec<T> itemCodec) {
		return new ListVarargCodec<>(itemCodec);
	}
}
