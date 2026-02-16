package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for non-negative bigint values.
 */
public class NonNegativeBigIntegerCodec implements ESExprCodec<UnsignedBigInteger> {
	public NonNegativeBigIntegerCodec() {}


	@Override
	public final ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.INT);
	}

	@Override
	public boolean isEncodedEqual(UnsignedBigInteger x, UnsignedBigInteger y) {
		return x.equals(y);
	}

	@Override
	public final ESExpr encode(UnsignedBigInteger value) {
		return new ESExpr.Int(value.toBigInteger());
	}

	@Override
	public final UnsignedBigInteger decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Int(var i)) {
			if(i.compareTo(BigInteger.ZERO) < 0) {
				throw new DecodeException("Integer value out of range", path);
			}

			return UnsignedBigInteger.valueOf(i);
		}
		else {
			throw new DecodeException("Expected an integer value", path);
		}
	}
}
