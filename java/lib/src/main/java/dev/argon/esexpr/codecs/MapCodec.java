package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.util.HashMap;
import java.util.Map;

/**
 * A codec for mutable maps.
 * @param <K> The key type.
 * @param <V> The value type.
 */
@ESExprOverrideCodec(Map.class)
@ESExprCodecTags(constructors = { "map" })
public class MapCodec<K, V> extends MapCodecBase<K, V, Map<K, V>> {

	/**
	 * Creates a codec for encoding and decoding mutable maps.
	 *
	 * @param kCodec The codec used for encoding and decoding the keys of the map.
	 * @param vCodec The codec used for encoding and decoding the values of the map.
	 */
	public MapCodec(ESExprCodec<K> kCodec, ESExprCodec<V> vCodec) {
		super(kCodec, vCodec);
	}

	@Override
	public Map<K, V> decode(ESExpr expr, FailurePath path) throws DecodeException {
		Map<K, V> map = new HashMap<>();
		decodeInto(expr, path, map::put);
		return map;
	}
}
