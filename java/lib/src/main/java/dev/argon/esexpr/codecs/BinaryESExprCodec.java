package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Set;

/**
 * A codec for binary values.
 */
public class BinaryESExprCodec extends ESExprCodec<byte[]> {
	private BinaryESExprCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(byte[].class)
	public static final ESExprCodec<byte[]> INSTANCE = new BinaryESExprCodec();


	@Override
	public @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Binary());
	}

	@Override
	public @NotNull ESExpr encode(byte @NotNull [] value) {
		return new ESExpr.Binary(value);
	}

	@Override
	public byte @NotNull [] decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Binary(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected a binary value", path);
		}
	}
}
