package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * A codec for Array8 values.
 */
public class Array8ESExprCodec extends ESExprCodec<byte[]> {
	private Array8ESExprCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(byte[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY8 })
	public static final ESExprCodec<byte[]> INSTANCE = new Array8ESExprCodec();


	@Override
	public @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY8);
	}

	@Override
	public @NotNull ESExpr encode(byte @NotNull [] value) {
		return new ESExpr.Array8(value);
	}

	@Override
	public byte @NotNull [] decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array8(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array8 value", path);
		}
	}
}
