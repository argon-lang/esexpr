package dev.argon.esexpr.codecs;

import com.google.common.primitives.UnsignedLong;
import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned long values.
 */
public class UnsignedLongCodec extends IntCodecBase<UnsignedLong> {
	private UnsignedLongCodec() {
		super(BigInteger.ZERO, UnsignedLong.MAX_VALUE.bigIntegerValue());
	}

	public static final UnsignedLongCodec INSTANCE = new UnsignedLongCodec();

	@Override
	public boolean isEncodedEqual(UnsignedLong x, UnsignedLong y) {
		return x.equals(y);
	}

	@Override
	protected UnsignedLong fromBigInt(BigInteger value) {
		return UnsignedLong.fromLongBits(value.longValue());
	}

	@Override
	protected BigInteger toBigInt(UnsignedLong value) {
		return value.bigIntegerValue();
	}
}
