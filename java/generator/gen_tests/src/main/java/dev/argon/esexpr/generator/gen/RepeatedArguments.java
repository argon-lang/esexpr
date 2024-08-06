package dev.argon.esexpr.generator.gen;

import java.util.List;

import dev.argon.esexpr.*;
import dev.argon.esexpr.codecs.ListVarargCodec;

@ESExprCodecGen
public record RepeatedArguments(
	@Vararg
	List<String> args,

	@Dict
	KeywordMapping<String> kwargs
) {
	public static ESExprCodec<RepeatedArguments> codec() {
		return RepeatedArguments_CodecImpl.INSTANCE;
	}
}

