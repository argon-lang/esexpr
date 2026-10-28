package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record BoundedRecord<T extends Number>(T value) {
	@TypeClassInstance
	public static <T extends Number> ESExprCodec<BoundedRecord<T>> codec(ESExprCodec<T> codecT) {
		return new BoundedRecord_CodecImpl<>(codecT);
	}
}
