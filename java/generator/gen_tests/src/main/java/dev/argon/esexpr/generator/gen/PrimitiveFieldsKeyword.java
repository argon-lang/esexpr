package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record PrimitiveFieldsKeyword(
	@Keyword boolean a,
	@Keyword byte b,
	@Keyword @Unsigned byte b2,
	@Keyword short c,
	@Keyword @Unsigned short c2,
	@Keyword int d,
	@Keyword @Unsigned int d2,
	@Keyword long e,
	@Keyword @Unsigned long e2,
	@Keyword float f,
	@Keyword double g
) {
	@TypeClassInstance
	public static ESExprCodec<PrimitiveFieldsKeyword> codec() {
		return PrimitiveFieldsKeyword_CodecImpl.INSTANCE;
	}
}

