package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A codec for float values.
 */
public class FloatCodec extends ESExprCodec<Float> {
	private FloatCodec() {}

	/**
	 * A codec for float values.
	 */
	@ESExprOverrideCodec(float.class)
	@ESExprOverrideCodec(Float.class)
	public static final ESExprCodec<Float> INSTANCE = new FloatCodec();

	@Override
	public @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Float32());
	}

	@Override
	public @NotNull ESExpr encode(@NotNull Float value) {
		return new ESExpr.Float32(value);
	}

	@Override
	public @NotNull Float decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Float32(var f)) {
			return f;
		}
		else {
			throw new DecodeException("Expected a float value", path);
		}
	}
}
