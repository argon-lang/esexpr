package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record DependentBoundRecord<T extends Number, U extends T>(T first, U second) {
	@TypeClassInstance
	public static <T extends Number, U extends T> ESExprCodec<DependentBoundRecord<T, U>> codec(
		ESExprCodec<T> codecT,
		ESExprCodec<U> codecU
	) {
		return new DependentBoundRecord_CodecImpl<>(codecT, codecU);
	}
}
