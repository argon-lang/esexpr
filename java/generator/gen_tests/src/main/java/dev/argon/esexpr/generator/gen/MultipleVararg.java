package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.TypeClassInstance;
import dev.argon.esexpr.Vararg;

import java.util.List;

@ESExprCodecGen
public record MultipleVararg(
	@Vararg
	List<Float> a,

	@Vararg
	List<Integer> b
) {
	@TypeClassInstance
	public static ESExprCodec<MultipleVararg> codec() {
		return MultipleVararg_CodecImpl.INSTANCE;
	}
}
