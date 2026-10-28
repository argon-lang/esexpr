package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
@Flags
public record LongMaxMask(Bits value) {
	public enum Bits {
		@FlagMask(0)
		ZERO,
		@FlagMask(bits = "9223372036854775807")
		VALUE
	}
	@TypeClassInstance
	public static ESExprCodec<LongMaxMask> codec() {
		return LongMaxMask_CodecImpl.INSTANCE;
	}
}
