package dev.argon.esexpr.generator.gen;

import java.util.Optional;
import dev.argon.esexpr.*;

@ESExprCodecGen
public record KeywordArguments(
	@Keyword
	boolean a,

	@Keyword("b2")
	boolean b,

	@Keyword("c2")
	@OptionalValue(OptionalValueCodec.ForOptional.class)
	Optional<Boolean> c,

	@Keyword
	@OptionalValue(OptionalValueCodec.ForOptional.class)
	Optional<Boolean> d,

	@Keyword
	@DefaultValue("trueValue")
	boolean e,

	@Keyword
	Optional<Boolean> f
) {
	static boolean trueValue() {
		return true;
	}
}

