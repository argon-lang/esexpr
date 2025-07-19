package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

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
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.FLOAT32);
	}

	@Override
	public boolean isEncodedEqual(Float x, Float y) {
		return Float.floatToRawIntBits(x) == Float.floatToRawIntBits(y);
	}

	@Override
	public ESExpr encode(Float value) {
		return new ESExpr.Float32(value);
	}

	@Override
	public Float decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Float32(var f)) {
			return f;
		}
		else {
			throw new DecodeException("Expected a float value", path);
		}
	}
}
