package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A VarArgCodec for List values.
 *
 * @param <T> The element type.
 */
@ESExprOverrideCodec(value = List.class, codecType = ESExprOverrideCodec.CodecType.VARARG)
public class ListVarargCodec<T> implements VarargCodec<List<T>, T> {
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
	public boolean isEncodedEqual(List<T> x, List<T> y) {
		if(x.size() != y.size()) {
			return false;
		}

		for(int i = 0; i < x.size(); ++i) {
			if(!elementCodec.isEncodedEqual(x.get(i), y.get(i))) {
				return false;
			}
		}

		return true;
	}

	@Override
	public void encodeVararg(List<T> value, List<ESExpr> exprs) {
		for(var elem : value) {
			exprs.add(elementCodec.encode(elem));
		}
	}

	@Override
	public List<T> decodeVararg(Deque<ESExpr> exprs, PositionalPathBuilder pathBuilder) throws DecodeException {
		List<T> values = new ArrayList<>(exprs.size());
		for(int i = 0;; ++i) {
			var expr = exprs.peekFirst();
			if(expr == null) {
				break;
			}

			if(!elementCodec.tags().contains(expr.tag())) {
				break;
			}

			exprs.removeFirst();

			var value = elementCodec.decode(expr, pathBuilder.pathAt(i));
			values.add(value);
		}
		return values;
	}
}
