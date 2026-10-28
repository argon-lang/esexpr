package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record WildcardBoundRecord<T extends Number & Comparable<? super T>>(T value) {
	@TypeClassInstance
	public static <T extends Number & Comparable<? super T>> ESExprCodec<WildcardBoundRecord<T>> codec(
		ESExprCodec<T> codecT
	) {
		return new WildcardBoundRecord_CodecImpl<>(codecT);
	}
}
