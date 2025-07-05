package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A codec for Array64 values.
 */
public class Array64ESExprCodec extends ESExprCodec<long[]> {
	private Array64ESExprCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(long[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY64 })
	public static final ESExprCodec<long[]> INSTANCE = new Array64ESExprCodec();


	@Override
	public @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY64);
	}

	@Override
	public @NotNull ESExpr encode(long @NotNull [] value) {
		return new ESExpr.Array64(value);
	}

	@Override
	public long @NotNull [] decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array64(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array64 value", path);
		}
	}
}
