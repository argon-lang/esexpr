package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
@Flags
public record AboveIntMask(Bits value) {
	public enum Bits {
		@FlagMask(0)
		ZERO,
		@FlagMask(bits = "2147483648")
		VALUE
	}
	@TypeClassInstance
	public static ESExprCodec<AboveIntMask> codec() {
		return AboveIntMask_CodecImpl.INSTANCE;
	}
}
