package dev.argon.esexpr.codecs;

import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.*;

/**
 * A codec for immutable sets.
 * @param <T> The element type.
 */
public class ImmutableSetCodec<T> extends SetCodecBase<T, ImmutableSet<T>> {
	/**
	 * Create a codec for immutable sets.
	 * @param codec The element codec.
	 */
	public ImmutableSetCodec(ESExprCodec<T> codec) {
		super(codec);
	}

	@Override
	public ImmutableSet<T> decode(ESExpr expr, FailurePath path) throws DecodeException {
		var builder = ImmutableSet.<T>builder();
		decodeInto(expr, path, builder::add);
		return builder.build();
	}
}
