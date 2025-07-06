package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.DefaultValue;
import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;

@ESExprCodecGen
public record PositionalDefaultValue(
	@DefaultValue("4")
	int a,
	@DefaultValue("5.0f")
	float b,
	String c
) {
	public static ESExprCodec<PositionalDefaultValue> codec() {
		return new PositionalDefaultValue_CodecImpl();
	}
}
