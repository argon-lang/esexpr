package dev.argon.esexpr.generator.gen;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record GenericDefaults<T>(
	java.util.List<java.util.List<T>> nested,
	@Keyword @DefaultValue("java.util.List.<T>of()") java.util.List<T> defaulted,
	@Keyword @OptionalValue java.util.Optional<T> optional
) {
	@TypeClassInstance
	public static <T> ESExprCodec<GenericDefaults<T>> codec(ESExprCodec<T> codecT) {
		return new GenericDefaults_CodecImpl<>(codecT);
	}
}
