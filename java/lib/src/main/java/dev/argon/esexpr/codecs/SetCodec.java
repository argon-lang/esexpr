package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.util.HashSet;
import java.util.Set;

/**
 * A codec for mutable sets.
 * @param <T> The element type.
 */
public class SetCodec<T> extends SetCodecBase<T, Set<T>> {

	/**
	 * Creates a codec for encoding and decoding mutable sets.
	 *
	 * @param codec The codec used for encoding and decoding the elements of the set.
	 */
	public SetCodec(ESExprCodec<T> codec) {
		super(codec);
	}

	@Override
	public Set<T> decode(ESExpr expr, FailurePath path) throws DecodeException {
		Set<T> set = new HashSet<>();
		decodeInto(expr, path, set::add);
		return set;
	}
}
