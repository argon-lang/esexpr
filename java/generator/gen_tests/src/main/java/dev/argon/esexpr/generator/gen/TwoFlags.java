package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.FlagMask;
import dev.argon.esexpr.Flags;
import dev.argon.esexpr.TypeClassInstance;

@ESExprCodecGen
@Flags
public record TwoFlags(
	@FlagMask(0b01)
	boolean a,
	@FlagMask(0b10)
	boolean b
) {
	@TypeClassInstance
	public static ESExprCodec<TwoFlags> codec() {
		return TwoFlags_CodecImpl.INSTANCE;
	}
}
