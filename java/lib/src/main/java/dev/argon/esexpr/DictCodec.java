package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A codec for dictionary argument values.
 * @param <T> The type of the dictionary argument value.
 */
public interface DictCodec<T> {
	/**
	 * Encode a dictionary argument value into a map of expressions.
	 * @param value The dictionary argument value.
	 * @return The expressions.
	 */
	Map<String, ESExpr> encodeDict(T value);

	/**
	 * Decode a map of expressions into a dictionary argument value.
	 * @param exprs The expressions.
	 * @param pathBuilder A path builder of the current expressions within the decoded object for diagnostic purposes.
	 * @return The dictionary argument value.
	 * @throws DecodeException when the value cannot be decoded.
	 */
	T decodeDict(Map<String, ESExpr> exprs, @NotNull KeywordPathBuilder pathBuilder) throws DecodeException;

	/**
	 * Builds paths for elements of a dictionary argument.
	 */
	public static interface KeywordPathBuilder {
		/**
		 * Build a path for the element with the provided keyword.
		 * @param keyword The keyword of the element.
		 * @return A path for the element with the keyword.
		 */
		@NotNull ESExprCodec.FailurePath pathAt(String keyword);
	}

	/**
	 * A DictCodec for KeywordMapping values.
	 * @param <T> The element type.
	 */
	public static class ForKeywordMapping<T> implements DictCodec<KeywordMapping<T>> {
		/**
		 * Creates a DictCodec for KeywordMapping values.
		 * @param elementCodec A value codec for the element type.
		 */
		public ForKeywordMapping(ESExprCodec<T> elementCodec) {
			this.elementCodec = elementCodec;
		}

		private final ESExprCodec<T> elementCodec;

		@Override
		public Map<String, ESExpr> encodeDict(KeywordMapping<T> value) {
			Map<String, ESExpr> map = new HashMap<>();
			for(var entry : value.map().entrySet()) {
				map.put(entry.getKey(), elementCodec.encode(entry.getValue()));
			}
			return map;
		}

		@Override
		public KeywordMapping<T> decodeDict(Map<String, ESExpr> exprs, @NotNull KeywordPathBuilder pathBuilder) throws DecodeException {
			Map<String, T> values = new HashMap<>();
			int i = 0;
			for(var entry : exprs.entrySet()) {
				var value = elementCodec.decode(entry.getValue(), pathBuilder.pathAt(entry.getKey()));
				values.put(entry.getKey(), value);
				++i;
			}
			return new KeywordMapping<>(values);
		}
	}
}
