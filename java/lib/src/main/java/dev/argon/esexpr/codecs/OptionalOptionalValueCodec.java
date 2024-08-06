package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * An OptionalValueCodec for Optional values.
 *
 * @param <T> The element type.
 */
@ESExprOverrideCodec(value = Optional.class, codecType = ESExprOverrideCodec.CodecType.OPTIONAL_VALUE)
public class OptionalOptionalValueCodec<T> implements OptionalValueCodec<Optional<T>> {
	/**
	 * Creates an OptionalValueCodec for Optional values.
	 *
	 * @param elementCodec A value codec for the element type.
	 */
	public OptionalOptionalValueCodec(ESExprCodec<T> elementCodec) {
		this.elementCodec = elementCodec;
	}

	private final ESExprCodec<T> elementCodec;

	@Override
	public Optional<ESExpr> encodeOptional(Optional<T> value) {
		return value.map(elementCodec::encode);
	}

	@Override
	public Optional<T> decodeOptional(Optional<ESExpr> expr, @NotNull ESExprCodec.FailurePath path) throws DecodeException {
		var expr2 = expr.orElse(null);
		if(expr2 == null) {
			return Optional.empty();
		}

		var value = elementCodec.decode(expr2, path);
		return Optional.of(value);
	}
}
