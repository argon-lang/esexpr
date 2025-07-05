package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.Vararg;

import java.util.List;

@ESExprCodecGen
public record RequiredAfterVararg(
	@Vararg
	List<Float> a,

	int b
) {
	public static ESExprCodec<RequiredAfterVararg> codec() {
		return RequiredAfterVararg_CodecImpl.INSTANCE;
	}
}
