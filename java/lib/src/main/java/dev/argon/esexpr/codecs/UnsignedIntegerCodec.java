package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned int values.
 */
public class UnsignedIntegerCodec extends IntCodecBase<Integer> {
	private UnsignedIntegerCodec() {
		super(BigInteger.ZERO, BigInteger.valueOf(0xFFFFFFFFL));
	}

	/**
	 * A codec for unsigned byte values.
	 */
	@ESExprOverrideCodec(value = int.class, requiredAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Integer.class, requiredAnnotations = Unsigned.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<Integer> INSTANCE = new UnsignedIntegerCodec();

	@Override
	public boolean isEncodedEqual(Integer x, Integer y) {
		return x.equals(y);
	}

	@Override
	protected Integer fromBigInt(BigInteger value) {
		return value.intValue();
	}

	@Override
	protected BigInteger toBigInt(Integer value) {
		return BigInteger.valueOf(Integer.toUnsignedLong(value));
	}
}
