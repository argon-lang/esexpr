package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
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
		return value.map(x -> {
			var res = itemCodec.encode(x);
			if(res instanceof ESExpr.Null(var level)) {
				return new ESExpr.Null(level.add(BigInteger.ONE));
			}
			else {
				return res;
			}
		}).orElseGet(() -> new ESExpr.Null(BigInteger.ZERO));
	}

	@Override
	public @NotNull Optional<T> decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Null(var level)) {
			if(level.signum() == 0) {
				return Optional.empty();
			}
			else {
				return Optional.of(itemCodec.decode(new ESExpr.Null(level.subtract(BigInteger.ONE))));
			}
		}
		else {
			return Optional.of(itemCodec.decode(expr));
		}
	}
}
