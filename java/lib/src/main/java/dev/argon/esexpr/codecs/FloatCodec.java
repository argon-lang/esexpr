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
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.FLOAT32 })
	public static final ESExprCodec<Float> INSTANCE = new FloatCodec();

	@Override
	public @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.FLOAT32);
	}

	@Override
	public boolean isEncodedEqual(Float x, Float y) {
		return Float.floatToRawIntBits(x) == Float.floatToRawIntBits(y);
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
