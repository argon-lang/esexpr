package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A KeywordMapping is a Map with String keys.
 * @param map The underlying map.
 * @param <T> The element type.
 */
public record KeywordMapping<T>(@NotNull Map<String, T> map) {
	/**
	 * Get a codec for the keyword mapping.
	 * @param tCodec The element codec.
	 * @return The codec.
	 * @param <T> The element type.
	 */
	public static <T> ESExprCodec<KeywordMapping<T>> codec(ESExprCodec<T> tCodec) {
		return new ESExprCodec<>() {
			private static final String DICT_CONSTRUCTOR = "dict";

			@Override
			public @NotNull Set<@NotNull ESExprTag> tags() {
				return Set.of(new ESExprTag.Constructor(DICT_CONSTRUCTOR));
			}

			@Override
			public @NotNull ESExpr encode(@NotNull KeywordMapping<T> value) {
				var map = dictCodec(tCodec).encodeDict(value);
				return new ESExpr.Constructor(
					DICT_CONSTRUCTOR,
					List.of(),
					map
				);
			}

			@Override
			public @NotNull KeywordMapping<T> decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
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
	public static <T> DictCodec<KeywordMapping<T>> dictCodec(ESExprCodec<T> tCodec) {
		return new DictCodec<>() {
			@Override
			public Map<String, ESExpr> encodeDict(KeywordMapping<T> value) {
				Map<String, ESExpr> map = new HashMap<>();
				for(var entry : value.map().entrySet()) {
					map.put(entry.getKey(), tCodec.encode(entry.getValue()));
				}
				return map;
			}

			@Override
			public KeywordMapping<T> decodeDict(Map<String, ESExpr> exprs, @NotNull DictCodec.KeywordPathBuilder pathBuilder) throws DecodeException {
				Map<String, T> values = new HashMap<>();
				int i = 0;
				for(var entry : exprs.entrySet()) {
					var value = tCodec.decode(entry.getValue(), pathBuilder.pathAt(entry.getKey()));
					values.put(entry.getKey(), value);
					++i;
				}
				return new KeywordMapping<>(values);
			}
		};
	}
}
