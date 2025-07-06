package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

/**
 * A codec for Array16 values.
 */
public class Array16ESExprCodec extends ESExprCodec<short[]> {
	private Array16ESExprCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(short[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY16 })
	public static final ESExprCodec<short[]> INSTANCE = new Array16ESExprCodec();


	@Override
	public @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY16);
	}

	@Override
	public boolean isEncodedEqual(short[] x, short[] y) {
		return Arrays.equals(x, y);
	}

	@Override
	public @NotNull ESExpr encode(short @NotNull [] value) {
		return new ESExpr.Array16(value);
	}

	@Override
	public short @NotNull [] decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array16(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array16 value", path);
		}
	}
}
