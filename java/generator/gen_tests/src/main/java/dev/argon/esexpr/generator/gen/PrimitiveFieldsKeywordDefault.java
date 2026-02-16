package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record PrimitiveFieldsKeywordDefault(
	@DefaultValue("false") @Keyword boolean a,
	@DefaultValue("(byte)0") @Keyword byte b,
	@DefaultValue("(byte)0") @Keyword @Unsigned byte b2,
	@DefaultValue("(short)0") @Keyword short c,
	@DefaultValue("(short)0") @Keyword @Unsigned short c2,
	@DefaultValue("0") @Keyword int d,
	@DefaultValue("0") @Keyword @Unsigned int d2,
	@DefaultValue("0L") @Keyword long e,
	@DefaultValue("0L") @Keyword @Unsigned long e2,
	@DefaultValue("0.0f") @Keyword float f,
	@DefaultValue("0.0") @Keyword double g
) {
	@TypeClassInstance
	public static ESExprCodec<PrimitiveFieldsKeywordDefault> codec() {
		return PrimitiveFieldsKeywordDefault_CodecImpl.INSTANCE;
	}
}

