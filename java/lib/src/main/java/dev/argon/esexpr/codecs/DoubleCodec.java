package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.util.Set;

/**
 * A codec for double values.
 */
public class DoubleCodec implements ESExprCodec<Double> {
	private DoubleCodec() {}

	/**
	 * A codec for double values.
	 */
	
	
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.FLOAT64 })
	public static final ESExprCodec<Double> INSTANCE = new DoubleCodec();

	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.FLOAT64);
	}

	@Override
	public boolean isEncodedEqual(Double x, Double y) {
		return Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y);
	}

	@Override
	public ESExpr encode(Double value) {
		return new ESExpr.Float64(value);
	}

	@Override
	public Double decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Float64(var d)) {
			return d;
		}
		else {
			throw new DecodeException("Expected a double value", path);
		}
	}
}
