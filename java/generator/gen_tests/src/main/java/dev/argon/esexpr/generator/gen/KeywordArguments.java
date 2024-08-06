package dev.argon.esexpr.generator.gen;

import java.util.Optional;
import dev.argon.esexpr.*;
import dev.argon.esexpr.codecs.OptionalOptionalValueCodec;

@ESExprCodecGen
public record KeywordArguments(
	@Keyword
	boolean a,

	@Keyword("b2")
	boolean b,

	@Keyword("c2")
	@OptionalValue
	Optional<Boolean> c,

	@Keyword
	@OptionalValue
	Optional<Boolean> d,

	@Keyword
	@DefaultValue("true")
	boolean e,

	@Keyword
	Optional<Boolean> f
) {
	public static ESExprCodec<KeywordArguments> codec() {
		return KeywordArguments_CodecImpl.INSTANCE;
	}
}

