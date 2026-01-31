package dev.argon.esexpr.codecs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import dev.argon.esexpr.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * A base class list codecs.
 * @param <T> The type of the list elements.
 * @param <L> The type of the list.
 */
public abstract class ListCodecBase<T, L extends List<T>> extends ESExprCodec<L> {

	/**
	 * Create a codec for list values.
	 * @param itemCodec The underlying codec for the values.
	 */
	public ListCodecBase(ESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}

	private final ESExprCodec<T> itemCodec;

	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(new ESExprTag.Constructor("list"));
	}

	@Override
	public boolean isEncodedEqual(L x, L y) {
		if(x.size() != y.size()) {
			return false;
		}

		for(int i = 0; i < x.size(); ++i) {
			if(!itemCodec.isEncodedEqual(x.get(i), y.get(i))) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ESExpr encode(L value) {
		return new ESExpr.Constructor(
			"list",
			value.stream().map(itemCodec::encode).collect(ImmutableList.toImmutableList()),
			ImmutableMap.of()
		);
	}

	@Override
	public abstract L decode(ESExpr expr, FailurePath path) throws DecodeException;

	/**
	 * Decodes the given ESExpr representing a list into individual elements and passes them to the
	 * provided consumer.
	 * The method expects the expression to represent a "list" constructor with no keyword arguments.
	 * If the expression does not meet this requirement, a {@link DecodeException} is thrown.
	 *
	 * @param expr The ESExpr to be decoded.
	 * @param path The current failure path for error reporting.
	 * @param add A consumer that accepts each decoded list element.
	 * @throws DecodeException If the expression is not a valid "list" constructor,
	 *                         it contains keyword arguments, or
	 *                         decoding fails for any of the list elements.
	 */
	protected final void decodeInto(ESExpr expr, FailurePath path, Consumer<T> add) throws DecodeException {
		if(expr instanceof ESExpr.Constructor(var name, var args, var kwargs) && name.equals("list")) {
			if(!kwargs.isEmpty()) {
				throw new DecodeException("Unexpected keyword arguments for list.", path.withConstructor("list"));
			}

			int i = 0;
			for(ESExpr item : args) {
				var decItem = itemCodec.decode(item, path.append("list", i));
				add.accept(decItem);

				++i;
			}
		}
		else {
			throw new DecodeException("Expected a list constructor", path);
		}

	}
}
