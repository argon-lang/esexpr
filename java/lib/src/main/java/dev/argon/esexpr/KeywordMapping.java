package dev.argon.esexpr;

import java.util.Map;

/**
 * A KeywordMapping is a Map with String keys.
 * @param map The underlying map.
 * @param <T> The element type.
 */
public record KeywordMapping<T>(Map<String, T> map) {
}
