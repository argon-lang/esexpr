package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Set;

/**
 * A codec for bigint values.
 */
public class BigIntegerCodec extends ESExprCodec<BigInteger> {
	private BigIntegerCodec() {}

	/**
	 * A codec for bigint values.
	 */
	@ESExprOverrideCodec(value = BigInteger.class, excludedAnnotations = Unsigned.class)
	public static final ESExprCodec<BigInteger> INSTANCE = new BigIntegerCodec();

	@Override
	public final @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Int());
	}

	@Override
	public final @NotNull ESExpr encode(@NotNull BigInteger value) {
		return new ESExpr.Int(value);
	}

	@Override
	public final @NotNull BigInteger decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Int(var i)) {
			return i;
		}
		else {
			throw new DecodeException("Expected an integer value", path);
		}
	}
}
