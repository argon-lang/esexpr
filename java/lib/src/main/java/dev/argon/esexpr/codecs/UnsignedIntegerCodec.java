package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned int values.
 */
public class UnsignedIntegerCodec extends IntCodecBase<UnsignedInteger> {
	public UnsignedIntegerCodec() {
		super(BigInteger.ZERO, BigInteger.valueOf(0xFFFFFFFFL));
	}

	@Override
	public boolean isEncodedEqual(UnsignedInteger x, UnsignedInteger y) {
		return x.equals(y);
	}

	@Override
	protected UnsignedInteger fromBigInt(BigInteger value) {
		return UnsignedInteger.valueOf(value.intValue());
	}

	@Override
	protected BigInteger toBigInt(UnsignedInteger value) {
		return BigInteger.valueOf(value.longValue());
	}
}
