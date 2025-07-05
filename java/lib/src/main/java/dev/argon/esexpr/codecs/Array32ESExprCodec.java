package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A codec for Array32 values.
 */
public class Array32ESExprCodec extends ESExprCodec<int[]> {
	private Array32ESExprCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(int[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY32 })
	public static final ESExprCodec<int[]> INSTANCE = new Array32ESExprCodec();


	@Override
	public @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY32);
	}

	@Override
	public @NotNull ESExpr encode(int @NotNull [] value) {
		return new ESExpr.Array32(value);
	}

	@Override
	public int @NotNull [] decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array32(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array8 value", path);
		}
	}
}
