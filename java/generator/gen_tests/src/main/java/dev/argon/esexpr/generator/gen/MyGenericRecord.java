package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;


@ESExprCodecGen
public record MyGenericRecord<T>(
	T value
) {

	@TypeClassInstance
	public static <T> ESExprCodec<MyGenericRecord<T>> codec(ESExprCodec<T> tCodec) {
		return new MyGenericRecord_CodecImpl<T>(tCodec);
	}

}

