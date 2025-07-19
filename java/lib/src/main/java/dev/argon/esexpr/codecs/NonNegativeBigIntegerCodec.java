package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for non-negative bigint values.
 */
public class NonNegativeBigIntegerCodec extends ESExprCodec<BigInteger> {
	private NonNegativeBigIntegerCodec() {}

	/**
	 * A codec for non-negative bigint values.
	 */
	@ESExprOverrideCodec(value = BigInteger.class, requiredAnnotations = Unsigned.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<BigInteger> INSTANCE = new NonNegativeBigIntegerCodec();


	@Override
	public final ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.INT);
	}

	@Override
	public boolean isEncodedEqual(BigInteger x, BigInteger y) {
		return x.equals(y);
	}

	@Override
	public final ESExpr encode(BigInteger value) {
		return new ESExpr.Int(value);
	}

	@Override
	public final BigInteger decode(ESExpr expr, FailurePath path) throws DecodeException {
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
