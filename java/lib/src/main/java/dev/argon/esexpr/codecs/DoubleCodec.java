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
	public static final ESExprCodec<Double> INSTANCE = new DoubleCodec();

	@Override
	public @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Float64());
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
