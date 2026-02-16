package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned short values.
 */
public class UnsignedShortCodec extends IntCodecBase<UnsignedShort> {
	public UnsignedShortCodec() {
		super(BigInteger.ZERO, BigInteger.valueOf(0xFFFF));
	}


	@Override
	public boolean isEncodedEqual(UnsignedShort x, UnsignedShort y) {
		return x.equals(y);
	}

	@Override
	protected UnsignedShort fromBigInt(BigInteger value) {
		return UnsignedShort.valueOf(value.shortValue());
	}

	@Override
	protected BigInteger toBigInt(UnsignedShort value) {
		return BigInteger.valueOf(value.intValue());
	}
}
