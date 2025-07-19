package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for unsigned long values.
 */
public class UnsignedLongCodec extends IntCodecBase<Long> {
	private UnsignedLongCodec() {
		super(BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
	}

	/**
	 * A codec for unsigned long values.
	 */
	@ESExprOverrideCodec(value = long.class, requiredAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Long.class, requiredAnnotations = Unsigned.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<Long> INSTANCE = new UnsignedLongCodec();

	@Override
	public boolean isEncodedEqual(Long x, Long y) {
		return x.longValue() == y.longValue();
	}

	@Override
	protected Long fromBigInt(BigInteger value) {
		return value.longValue();
	}

	@Override
	protected BigInteger toBigInt(Long value) {
		return BigInteger.valueOf(value).and(
			BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
		);
	}
}
