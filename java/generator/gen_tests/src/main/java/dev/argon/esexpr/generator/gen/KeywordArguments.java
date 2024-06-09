package dev.argon.esexpr.generator.gen;

import java.util.Optional;
import dev.argon.esexpr.*;

@ESExprCodecGen
public record KeywordArguments(
	@Keyword
	boolean a,

	@Keyword(name = "b2")
	boolean b,

	@Keyword(name = "c2", required = false)
	Optional<Boolean> c,

	@Keyword(required = false)
	Optional<Boolean> d,

	@Keyword(defaultValueMethod = "trueValue")
	boolean e,

	@Keyword
	Optional<Boolean> f
) {
	static boolean trueValue() {
		return true;
	}
}

