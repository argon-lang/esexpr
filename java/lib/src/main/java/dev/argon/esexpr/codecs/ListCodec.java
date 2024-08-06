package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * A codec for list values.
 * @param <T> The type of the list elements.
 */
@ESExprOverrideCodec(List.class)
public class ListCodec<T> extends ESExprCodec<List<T>> {

	/**
	 * Create a codec for list values.
	 * @param itemCodec The underlying codec for the values.
	 */
	public ListCodec(ESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}

	private final ESExprCodec<T> itemCodec;

	@Override
	public @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Constructor("list"));
	}

	@Override
	public @NotNull ESExpr encode(@NotNull List<T> value) {
		return new ESExpr.Constructor("list", value.stream().map(itemCodec::encode).toList(), new HashMap<>());
	}

	@Override
	public @NotNull List<T> decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Constructor(var name, var args, var kwargs) && name.equals("list")) {
			if(!kwargs.isEmpty()) {
				throw new DecodeException("Unexpected keyword arguments for list.", path.withConstructor("list"));
			}

			List<T> res = new ArrayList<T>(args.size());
			int i = 0;
			for(ESExpr item : args) {
				res.add(itemCodec.decode(item, path.append("list", i)));

				++i;
			}
			return res;
		}
		else {
			throw new DecodeException("Expected a list constructor", path);
		}
	}
}
