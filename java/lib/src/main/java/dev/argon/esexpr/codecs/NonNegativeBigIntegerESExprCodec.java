package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Set;

/**
 * A codec for non-negative bigint values.
 */
public class NonNegativeBigIntegerESExprCodec extends ESExprCodec<BigInteger> {
	private NonNegativeBigIntegerESExprCodec() {}

	/**
	 * A codec for non-negative bigint values.
	 */
	@ESExprOverrideCodec(value = BigInteger.class, requiredAnnotations = Unsigned.class)
	public static final ESExprCodec<BigInteger> INSTANCE = new NonNegativeBigIntegerESExprCodec();


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
			if(i.compareTo(BigInteger.ZERO) < 0) {
				throw new DecodeException("Integer value out of range", path);
			}

			return i;
		}
		else {
			throw new DecodeException("Expected an integer value", path);
		}
	}
}
