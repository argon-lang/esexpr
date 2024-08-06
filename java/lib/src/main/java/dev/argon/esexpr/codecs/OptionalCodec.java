package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A codec for optional values.
 * @param <T> The type of the optional value.
 */
@ESExprOverrideCodec(Optional.class)
public class OptionalCodec<T> extends ESExprCodec<Optional<T>> {
	/**
	 * Create a codec for optional values.
	 * @param itemCodec The underlying codec for the values.
	 */
	public OptionalCodec(ESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}

	private final ESExprCodec<T> itemCodec;

	@Override
	public @NotNull Set<@NotNull ESExprTag> tags() {
		var tags = new HashSet<ESExprTag>();
		tags.add(new ESExprTag.Null());
		tags.addAll(itemCodec.tags());
		return tags;
	}

	@Override
	public @NotNull ESExpr encode(@NotNull Optional<T> value) {
		return value.map(itemCodec::encode).orElseGet(ESExpr.Null::new);
	}

	@Override
	public @NotNull Optional<T> decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Null) {
			return Optional.empty();
		}
		else {
			return Optional.of(itemCodec.decode(expr));
		}
	}
}
