package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record RecursiveBoundRecord<T extends Number & Comparable<T>>(T value) {
	@TypeClassInstance
	public static <T extends Number & Comparable<T>> ESExprCodec<RecursiveBoundRecord<T>> codec(ESExprCodec<T> codecT) {
		return new RecursiveBoundRecord_CodecImpl<>(codecT);
	}
}
