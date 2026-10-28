package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public sealed interface GenericInline<T> {
	@InlineValue
	record Text<T>(String value) implements GenericInline<T> {}
	record Values<T>(java.util.List<T> values) implements GenericInline<T> {}
	@TypeClassInstance
	static <T> ESExprCodec<GenericInline<T>> codec(ESExprCodec<T> element) {
		return new GenericInline_CodecImpl<>(element);
	}
}
