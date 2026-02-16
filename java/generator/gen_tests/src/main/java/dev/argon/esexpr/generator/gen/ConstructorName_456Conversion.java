package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.TypeClassInstance;

@ESExprCodecGen
public record ConstructorName_456Conversion(
	int a
) {
	@TypeClassInstance
	public static ESExprCodec<ConstructorName_456Conversion> codec() {
		return ConstructorName_456Conversion_CodecImpl.INSTANCE;
	}
}
