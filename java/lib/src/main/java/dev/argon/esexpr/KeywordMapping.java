package dev.argon.esexpr;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A KeywordMapping is a Map with String keys.
 * @param map The underlying map.
 * @param <T> The element type.
 */
public record KeywordMapping<T>(ImmutableMap<String, T> map) {
	/**
	 * Get a codec for the keyword mapping.
	 * @param tCodec The element codec.
	 * @return The codec.
	 * @param <T> The element type.
	 */
	@ESExprCodecTags(constructors = { "dict" })
	public static <T> ESExprCodec<KeywordMapping<T>> codec(ESExprCodec<T> tCodec) {
		return new ESExprCodec<>() {
			private static final String DICT_CONSTRUCTOR = "dict";

			@Override
			public ESExprTagSet tags() {
				return ESExprTagSet.of(new ESExprTag.Constructor(DICT_CONSTRUCTOR));
			}

			@Override
			public boolean isEncodedEqual(KeywordMapping<T> x, KeywordMapping<T> y) {
				if(x.map().size() != y.map().size()) {
					return false;
				}

				for(var entryA : x.map().entrySet()) {
					var other = y.map().get(entryA.getKey());
					if(other == null) {
						return false;
					}

					if(!tCodec.isEncodedEqual(entryA.getValue(), other)) {
						return false;
					}
				}

				return true;
			}

			@Override
			public ESExpr encode(KeywordMapping<T> value) {
				var builder = ImmutableMap.<String, ESExpr>builder();
				dictCodec(tCodec).encodeDict(value, builder);
				return new ESExpr.Constructor(
					DICT_CONSTRUCTOR,
					ImmutableList.of(),
					builder.build()
				);
			}

			@Override
			public KeywordMapping<T> decode(ESExpr expr, FailurePath path) throws DecodeException {
				if(expr instanceof ESExpr.Constructor(var name, var args, var kwargs) && name.equals(DICT_CONSTRUCTOR)) {
					if(!args.isEmpty()) {
						throw new DecodeException("Invalid positional arguments for dict", path.withConstructor(name));
					}

					return dictCodec(tCodec).decodeDict(kwargs, kw -> path.append(name, kw));
				}
				else {
					throw new DecodeException("Expected a dict constructor.", path);
				}
			}
		};
	}

	/**
	 * Creates a DictCodec for KeywordMapping values.
	 * @param tCodec A value codec for the element type.
	 * @return The DictCodec.
	 * @param <T> The element type.
	 */
	public static <T> DictCodec<KeywordMapping<T>, T> dictCodec(ESExprCodec<T> tCodec) {
		return new DictCodec<>() {
			@Override
			public boolean isEncodedEqual(KeywordMapping<T> x, KeywordMapping<T> y) {
				if(x.map().size() != y.map().size()) {
					return false;
				}

				for(var entryA : x.map().entrySet()) {
					var other = y.map().get(entryA.getKey());
					if(other == null) {
						return false;
					}

					if(!tCodec.isEncodedEqual(entryA.getValue(), other)) {
						return false;
					}
				}

				return true;
			}

			@Override
			public void encodeDict(KeywordMapping<T> value, ImmutableMap.Builder<String, ESExpr> builder) {
				for(var entry : value.map().entrySet()) {
					builder.put(entry.getKey(), tCodec.encode(entry.getValue()));
				}
			}

			@Override
			public KeywordMapping<T> decodeDict(Map<String, ESExpr> exprs, DictCodec.KeywordPathBuilder pathBuilder) throws DecodeException {
				var builder = ImmutableMap.<String, T>builder();
				for(var entry : exprs.entrySet()) {
					var value = tCodec.decode(entry.getValue(), pathBuilder.pathAt(entry.getKey()));
					builder.put(entry.getKey(), value);
				}
				return new KeywordMapping<>(builder.build());
			}
		};
	}
}
