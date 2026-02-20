package dev.argon.esexpr.codecs;

import com.google.common.primitives.UnsignedInteger;
import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned int values.
 */
public class UnsignedIntegerCodec extends IntCodecBase<UnsignedInteger> {
	private UnsignedIntegerCodec() {
		super(BigInteger.ZERO, UnsignedInteger.MAX_VALUE.bigIntegerValue());
	}

	public static final UnsignedIntegerCodec INSTANCE = new UnsignedIntegerCodec();

	@Override
	public boolean isEncodedEqual(UnsignedInteger x, UnsignedInteger y) {
		return x.equals(y);
	}

	@Override
	protected UnsignedInteger fromBigInt(BigInteger value) {
		return UnsignedInteger.fromIntBits(value.intValue());
	}

	@Override
	protected BigInteger toBigInt(UnsignedInteger value) {
		return BigInteger.valueOf(value.longValue());
	}
}
