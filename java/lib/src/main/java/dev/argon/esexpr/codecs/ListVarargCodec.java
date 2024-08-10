package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A VarArgCodec for List values.
 *
 * @param <T> The element type.
 */
@ESExprOverrideCodec(value = List.class, codecType = ESExprOverrideCodec.CodecType.VARARG)
public class ListVarargCodec<T> implements VarargCodec<List<T>> {
	/**
	 * Creates a VarArgCodec for List values.
	 *
	 * @param elementCodec A value codec for the element type.
	 */
	public ListVarargCodec(ESExprCodec<T> elementCodec) {
		this.elementCodec = elementCodec;
	}

	private final ESExprCodec<T> elementCodec;

	@Override
	public List<ESExpr> encodeVararg(List<T> value) {
		return value.stream().map(elementCodec::encode).toList();
	}

	@Override
	public List<T> decodeVararg(List<ESExpr> exprs, @NotNull PositionalPathBuilder pathBuilder) throws DecodeException {
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
