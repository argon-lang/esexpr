package dev.argon.esexpr.generator.gen;

import java.util.List;
import java.util.Map;
import dev.argon.esexpr.*;

@ESExprCodecGen
public record RepeatedArguments(
	@VarArgs
	List<String> args,

	@Dict
	Map<String, String> kwargs
) {
}

