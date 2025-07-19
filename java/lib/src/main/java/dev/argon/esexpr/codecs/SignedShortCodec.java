package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for signed short values.
 */
public class SignedShortCodec extends IntCodecBase<Short> {
	private SignedShortCodec() {
		super(BigInteger.valueOf(Short.MIN_VALUE), BigInteger.valueOf(Short.MAX_VALUE));
	}

	/**
	 * A codec for signed short values.
	 */
	@ESExprOverrideCodec(value = short.class, excludedAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Short.class, excludedAnnotations = Unsigned.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<Short> INSTANCE = new SignedShortCodec();

	@Override
	public boolean isEncodedEqual(Short x, Short y) {
		return x.shortValue() == y.shortValue();
	}

	@Override
	protected Short fromBigInt(BigInteger value) {
		return value.shortValue();
	}

	@Override
	protected BigInteger toBigInt(Short value) {
		return BigInteger.valueOf(value);
	}
}
