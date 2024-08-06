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
	public static final ESExprCodec<String> INSTANCE = new StringCodec();

	@Override
	public @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Str());
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
