package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A codec for double values.
 */
public class DoubleCodec extends ESExprCodec<Double> {
	private DoubleCodec() {}

	/**
	 * A codec for double values.
	 */
	@ESExprOverrideCodec(double.class)
	@ESExprOverrideCodec(Double.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.FLOAT64 })
	public static final ESExprCodec<Double> INSTANCE = new DoubleCodec();

	@Override
	public @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.FLOAT64);
	}

	@Override
	public boolean isEncodedEqual(Double x, Double y) {
		return Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y);
	}

	@Override
	public @NotNull ESExpr encode(@NotNull Double value) {
		return new ESExpr.Float64(value);
	}

	@Override
	public @NotNull Double decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Float64(var d)) {
			return d;
		}
		else {
			throw new DecodeException("Expected a double value", path);
		}
	}
}
