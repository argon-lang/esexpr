package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record PrimitiveFieldsPositionalDefault(
	@DefaultValue("false") boolean a,
	@DefaultValue("(byte)0") byte b,
	String dummy1,
	@DefaultValue("(byte)0") @Unsigned byte b2,
	String dummy2,
	@DefaultValue("(short)0") short c,
	String dummy3,
	@DefaultValue("(short)0") @Unsigned short c2,
	String dummy4,
	@DefaultValue("0") int d,
	String dummy5,
	@DefaultValue("0") @Unsigned int d2,
	String dummy6,
	@DefaultValue("0L") long e,
	String dummy7,
	@DefaultValue("0L") @Unsigned long e2,
	@DefaultValue("0.0f") float f,
	@DefaultValue("0.0") double g
) {
	@TypeClassInstance
	public static ESExprCodec<PrimitiveFieldsPositionalDefault> codec() {
		return PrimitiveFieldsPositionalDefault_CodecImpl.INSTANCE;
	}
}

