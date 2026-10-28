package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
@Flags
public record IntMaxMask(Bits value) {
	public enum Bits {
		@FlagMask(0)
		ZERO,
		@FlagMask(bits = "2147483647")
		VALUE
	}
	@TypeClassInstance
	public static ESExprCodec<IntMaxMask> codec() {
		return IntMaxMask_CodecImpl.INSTANCE;
	}
}
