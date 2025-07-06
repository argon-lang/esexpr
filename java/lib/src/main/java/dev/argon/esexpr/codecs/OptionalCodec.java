package dev.argon.esexpr.codecs;

import com.google.common.collect.ImmutableSet;
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
@ESExprCodecTags(scalar = { ESExprTag.Scalar.NULL }, unionWithTypeParameters = "T")
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
	public @NotNull ESExprTagSet tags() {
		return switch(itemCodec.tags()) {
			case ESExprTagSet.Tags(var elementTags) -> {
				var tags = ImmutableSet.<@NotNull ESExprTag>builder();
				tags.add(ESExprTag.NULL);
				tags.addAll(elementTags);
				yield new ESExprTagSet.Tags(tags.build());
			}
			case ESExprTagSet.All all -> all;
		};
	}

	@Override
	public boolean isEncodedEqual(Optional<T> x, Optional<T> y) {
		if(x.isEmpty()) {
			return y.isEmpty();
		}
		else if(y.isEmpty()) {
			return false;
		}
		else {
			return itemCodec.isEncodedEqual(x.get(), y.get());
		}
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
