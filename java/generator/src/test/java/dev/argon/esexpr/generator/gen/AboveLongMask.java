package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
@Flags
public record AboveLongMask(Bits value) {
	public enum Bits {
		@FlagMask(0)
		ZERO,
		@FlagMask(bits = "9223372036854775808")
		VALUE
	}
	@TypeClassInstance
	public static ESExprCodec<AboveLongMask> codec() {
		return AboveLongMask_CodecImpl.INSTANCE;
	}
}
