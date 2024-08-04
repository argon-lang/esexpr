package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A codec for variable argument values.
 * @param <T> The type of the variable argument value.
 */
public interface VarArgCodec<T> {
	/**
	 * Encode a variable argument value into a list of expressions.
	 * @param value The variable argument value.
	 * @return The expressions.
	 */
	List<ESExpr> encodeVarArg(T value);

	/**
	 * Decode a list of expressions into a variable argument value.
	 * @param exprs The expressions.
	 * @param pathBuilder A path builder of the current expressions within the decoded object for diagnostic purposes.
	 * @return The variable argument value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decodeVarArg(List<ESExpr> exprs, @NotNull PositionalPathBuilder pathBuilder) throws DecodeException;

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

	/**
	 * A VarArgCodec for List values.
	 * @param <T> The element type.
	 */
	public static class ForList<T> implements VarArgCodec<List<T>> {
		/**
		 * Creates a VarArgCodec for List values.
		 * @param elementCodec A value codec for the element type.
		 */
		public ForList(ESExprCodec<T> elementCodec) {
			this.elementCodec = elementCodec;
		}

		private final ESExprCodec<T> elementCodec;

		@Override
		public List<ESExpr> encodeVarArg(List<T> value) {
			return value.stream().map(elementCodec::encode).toList();
		}

		@Override
		public List<T> decodeVarArg(List<ESExpr> exprs, @NotNull PositionalPathBuilder pathBuilder) throws DecodeException {
			List<T> values = new ArrayList<>(exprs.size());
			int i = 0;
			for(var expr : exprs) {
				var value = elementCodec.decode(expr, pathBuilder.pathAt(i));
				values.add(value);
				++i;
			}
			return values;
		}
	}
}
