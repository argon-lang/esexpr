package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned long values.
 */
public class UnsignedLongCodec extends IntCodecBase<UnsignedLong> {
	public UnsignedLongCodec() {
		super(BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
	}

	@Override
	public boolean isEncodedEqual(UnsignedLong x, UnsignedLong y) {
		return x.equals(y);
	}

	@Override
	protected UnsignedLong fromBigInt(BigInteger value) {
		return UnsignedLong.valueOf(value.longValue());
	}

	@Override
	protected BigInteger toBigInt(UnsignedLong value) {
		return value.bigIntegerValue();
	}
}
