package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.math.BigInteger;

/**
 * A codec for signed int values.
 */
public class SignedIntegerCodec extends IntCodecBase<Integer> {
	private SignedIntegerCodec() {
		super(BigInteger.valueOf(Integer.MIN_VALUE), BigInteger.valueOf(Integer.MAX_VALUE));
	}


	/**
	 * A codec for signed int values.
	 */
	
	
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<Integer> INSTANCE = new SignedIntegerCodec();

	@Override
	public boolean isEncodedEqual(Integer x, Integer y) {
		return x.intValue() == y.intValue();
	}

	@Override
	protected Integer fromBigInt(BigInteger value) {
		return value.intValue();
	}

	@Override
	protected BigInteger toBigInt(Integer value) {
		return BigInteger.valueOf(value);
	}
}
