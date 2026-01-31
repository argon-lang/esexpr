package dev.argon.esexpr.codecs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.argon.esexpr.*;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * An abstract base class for implementing codecs that encode and decode Map-like structures.
 *
 * @param <K> The type of map keys.
 * @param <V> The type of map values.
 * @param <M> The type of map being encoded and decoded.
 */
public abstract class MapCodecBase<K, V, M extends Map<K, V>> extends ESExprCodec<M> {

	/**
	 * Creates a codec for encoding and decoding Map-like structures.
	 *
	 * @param kCodec The codec used for encoding and decoding the keys of the map.
	 * @param vCodec The codec used for encoding and decoding the values of the map.
	 */
	public MapCodecBase(ESExprCodec<K> kCodec, ESExprCodec<V> vCodec) {
		this.kCodec = kCodec;
		this.vCodec = vCodec;
	}

	private final ESExprCodec<K> kCodec;
	private final ESExprCodec<V> vCodec;

	@Override
	public ESExprTagSet tags() {
		return new ESExprTagSet.Tags(ImmutableSet.of(new ESExprTag.Constructor("map")));
	}

	@Override
	public ESExpr encode(M value) {
		var args = ImmutableList.<ESExpr>builder();

		for(var entry : value.entrySet()) {
			args.add(kCodec.encode(entry.getKey()));
			args.add(vCodec.encode(entry.getValue()));
		}

		return new ESExpr.Constructor("map", args.build(), ImmutableMap.of());
	}

	@Override
	public abstract M decode(ESExpr expr, FailurePath path) throws DecodeException;

	/**
	 * Decodes the given ESExpr representing a map into individual key-value pairs and passes them to the provided consumer.
	 * The method expects the expression to represent a "map" constructor with no keyword arguments.
	 * If the expression does not meet this requirement, a {@link DecodeException} is thrown.
	 *
	 * @param expr The ESExpr to be decoded.
	 * @param path The current failure path for error reporting.
	 * @param add A consumer that accepts each decoded key-value pair.
	 * @throws DecodeException If the expression is not a valid "map" constructor,
	 *                         it contains keyword arguments, or
	 *                         decoding fails for any of the map entries.
	 */
	protected final void decodeInto(ESExpr expr, FailurePath path, BiConsumer<K, V> add) throws DecodeException {
		if(!(expr instanceof ESExpr.Constructor(var name, var args, var kwargs) && name.equals("map"))) {
			throw new DecodeException("Expected a map value", path);
		}

		if(!kwargs.isEmpty()) {
			throw new DecodeException("Unexpected keyword arguments for map.", path.withConstructor("map"));
		}

		if(args.size() % 2 != 0) {
			throw new DecodeException("Map must have an even number of entries.", path);
		}

		for(int i = 0; i < args.size(); i += 2) {
			var k = kCodec.decode(args.get(i), path.append("map", i));
			var v = vCodec.decode(args.get(i + 1), path.append("map", i + 1));
			add.accept(k, v);
		}
	}

	@Override
	public boolean isEncodedEqual(M x, M y) {
		if(x.size() != y.size()) {
			return false;
		}

		for(var entry : x.entrySet()) {
			if(!y.containsKey(entry.getKey()) || !vCodec.isEncodedEqual(entry.getValue(), y.get(entry.getKey()))) {
				return false;
			}
		}

		return true;
	}
}
