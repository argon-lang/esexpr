package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned byte values.
 */
public class UnsignedByteCodec extends IntCodecBase<UnsignedByte> {
	public UnsignedByteCodec() {
		super(BigInteger.ZERO, BigInteger.valueOf(0xFF));
	}

	@Override
	public boolean isEncodedEqual(UnsignedByte x, UnsignedByte y) {
		return x.byteValue() == y.byteValue();
	}

	@Override
	protected UnsignedByte fromBigInt(BigInteger value) {
		return UnsignedByte.valueOf(value.byteValue());
	}

	@Override
	protected BigInteger toBigInt(UnsignedByte value) {
		return BigInteger.valueOf(value.intValue());
	}

	@TypeClassInstance
	public static final ESExprCodec<UnsignedByte> INSTANCE = new UnsignedByteCodec();
}
