package dev.argon.esexpr.generator.gen;

import java.util.List;

import dev.argon.esexpr.*;

@ESExprCodecGen
public record RepeatedArguments(
	@VarArg(VarArgCodec.ForList.class)
	List<String> args,

	@Dict(DictCodec.ForKeywordMapping.class)
	KeywordMapping<String> kwargs
) {
	public static ESExprCodec<RepeatedArguments> codec() {
		return RepeatedArguments_CodecImpl.INSTANCE;
	}
}

