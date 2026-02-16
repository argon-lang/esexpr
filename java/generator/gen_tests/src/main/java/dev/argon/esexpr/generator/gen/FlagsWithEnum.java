package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.FlagMask;
import dev.argon.esexpr.Flags;
import dev.argon.esexpr.TypeClassInstance;

@ESExprCodecGen
@Flags
public record FlagsWithEnum(
	@FlagMask(0b01)
	boolean a,

	MyFlagsEnum b
) {
	@TypeClassInstance
	public static ESExprCodec<FlagsWithEnum> codec() {
		return FlagsWithEnum_CodecImpl.INSTANCE;
	}

	public enum MyFlagsEnum {
		@FlagMask(0b000)
		A,
		@FlagMask(0b010)
		B,
		@FlagMask(0b100)
		C,
	}

}
