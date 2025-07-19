package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for signed byte values.
 */
public class SignedByteCodec extends IntCodecBase<Byte> {
	private SignedByteCodec() {
		super(BigInteger.valueOf(Byte.MIN_VALUE), BigInteger.valueOf(Byte.MAX_VALUE));
	}

	/**
	 * A codec for signed byte values.
	 */
	@ESExprOverrideCodec(value = byte.class, excludedAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Byte.class, excludedAnnotations = Unsigned.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<Byte> INSTANCE = new SignedByteCodec();

	@Override
	public boolean isEncodedEqual(Byte x, Byte y) {
		return x.byteValue() == y.byteValue();
	}

	@Override
	protected Byte fromBigInt(BigInteger value) {
		return value.byteValue();
	}

	@Override
	protected BigInteger toBigInt(Byte value) {
		return BigInteger.valueOf(value);
	}
}
