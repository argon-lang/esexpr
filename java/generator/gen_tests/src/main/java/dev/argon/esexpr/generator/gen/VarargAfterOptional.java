package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprCodecGen;
import dev.argon.esexpr.OptionalValue;
import dev.argon.esexpr.TypeClassInstance;
import dev.argon.esexpr.Vararg;

import java.util.List;
import java.util.Optional;

@ESExprCodecGen
public record VarargAfterOptional(
	@OptionalValue
	Optional<Integer> a,

	@Vararg
	List<Float> b
) {
	@TypeClassInstance
	public static ESExprCodec<VarargAfterOptional> codec() {
		return VarargAfterOptional_CodecImpl.INSTANCE;
	}
}
