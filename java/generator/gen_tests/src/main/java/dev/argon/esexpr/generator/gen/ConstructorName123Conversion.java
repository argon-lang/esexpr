package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;

@ESExprCodecGen
public record ConstructorName123Conversion(
	int a
) {
	public static ESExprCodec<ConstructorName123Conversion> codec() {
		return ConstructorName123Conversion_CodecImpl.INSTANCE;
	}
}
