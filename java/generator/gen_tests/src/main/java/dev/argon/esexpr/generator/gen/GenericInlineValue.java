package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.InlineValue;

@ESExprCodecGen
public sealed interface GenericInlineValue<A> {
	@InlineValue
	record WrappedString<A>(String s) implements GenericInlineValue<A> {}

	record WrappedA<A>(A a) implements GenericInlineValue<A> {}

	public static <A> ESExprCodec<GenericInlineValue<A>> codec(ESExprCodec<A> aCodec) {
		return new GenericInlineValue_CodecImpl<>(aCodec);
	}
}
