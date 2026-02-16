package dev.argon.esexpr.codecs;

import com.google.common.collect.ImmutableMap;
import dev.argon.esexpr.*;

import java.util.HashMap;
import java.util.Map;

/**
 * A codec for immutable maps.
 * @param <K> The key type.
 * @param <V> The value type.
 */
public class ImmutableMapCodec<K, V> extends MapCodecBase<K, V, ImmutableMap<K, V>> {
	/**
	 * Create a codec for immutable maps.
	 * @param kCodec The key codec.
	 * @param vCodec The value codec.
	 */
	public ImmutableMapCodec(ESExprCodec<K> kCodec, ESExprCodec<V> vCodec) {
		super(kCodec, vCodec);
	}

	@Override
	public ImmutableMap<K, V> decode(ESExpr expr, FailurePath path) throws DecodeException {
		var builder = ImmutableMap.<K, V>builder();
		decodeInto(expr, path, builder::put);
		return builder.build();
	}
}
