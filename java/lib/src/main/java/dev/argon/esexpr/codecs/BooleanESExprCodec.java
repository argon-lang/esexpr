package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A codec for boolean values.
 */
public class BooleanESExprCodec extends ESExprCodec<Boolean> {
	private BooleanESExprCodec() {}

	/**
	 * A codec for boolean values.
	 */
	@ESExprOverrideCodec(boolean.class)
	@ESExprOverrideCodec(Boolean.class)
	public static final ESExprCodec<Boolean> INSTANCE = new BooleanESExprCodec();

	@Override
	public @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Bool());
	}

	@Override
	public @NotNull ESExpr encode(@NotNull Boolean value) {
		return new ESExpr.Bool(value);
	}

	@Override
	public @NotNull Boolean decode(@NotNull ESExpr expr, @NotNull ESExprCodec.FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Bool(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected a boolean value", path);
		}
	}
}
