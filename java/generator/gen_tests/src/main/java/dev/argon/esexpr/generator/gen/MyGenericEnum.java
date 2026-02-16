package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.TypeClassInstance;


@ESExprCodecGen
public sealed interface MyGenericEnum<A, B> {
	record Lefty<A, B>(A a) implements MyGenericEnum<A, B> {}
	record Righty<A, B>(B b) implements MyGenericEnum<A, B> {}

	@TypeClassInstance
	static <A, B> ESExprCodec<MyGenericEnum<A, B>> codec(ESExprCodec<A> aCodec, ESExprCodec<B> bCodec) {
		return new MyGenericEnum_CodecImpl<>(aCodec, bCodec);
	}
}
