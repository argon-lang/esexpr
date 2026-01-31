package dev.argon.esexpr.codecs;

import com.google.common.collect.ImmutableList;
import dev.argon.esexpr.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * A codec for list values.
 * @param <T> The type of the list elements.
 */
@ESExprOverrideCodec(List.class)
@ESExprCodecTags(constructors = { "list" })
public class ListCodec<T> extends ListCodecBase<T, List<T>> {

	/**
	 * Create a codec for list values.
	 * @param itemCodec The underlying codec for the values.
	 */
	public ListCodec(ESExprCodec<T> itemCodec) {
		super(itemCodec);
	}

	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(new ESExprTag.Constructor("list"));
	}

	@Override
	public List<T> decode(ESExpr expr, FailurePath path) throws DecodeException {
		var builder = ImmutableList.<T>builder();
		decodeInto(expr, path, builder::add);
		return builder.build();
	}
}
