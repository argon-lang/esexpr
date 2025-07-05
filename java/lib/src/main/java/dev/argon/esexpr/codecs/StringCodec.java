package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A codec for string values.
 */
public class StringCodec extends ESExprCodec<String> {
	private StringCodec() {}

	/**
	 * A codec for string values.
	 */
	@ESExprOverrideCodec(String.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.STR })
	public static final ESExprCodec<String> INSTANCE = new StringCodec();

	@Override
	public @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.STR);
	}

	@Override
	public @NotNull ESExpr encode(@NotNull String value) {
		return new ESExpr.Str(value);
	}

	@Override
	public @NotNull String decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Str(var s)) {
			return s;
		}
		else {
			throw new DecodeException("Expected a string value", path);
		}
	}
}
