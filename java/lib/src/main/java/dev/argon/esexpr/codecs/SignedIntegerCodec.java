package dev.argon.esexpr.codecs;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprOverrideCodec;
import dev.argon.esexpr.Unsigned;
import org.jetbrains.annotations.NotNull;

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
	@ESExprOverrideCodec(value = int.class, excludedAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Integer.class, excludedAnnotations = Unsigned.class)
	public static final ESExprCodec<Integer> INSTANCE = new SignedIntegerCodec();


	@Override
	protected @NotNull Integer fromBigInt(@NotNull BigInteger value) {
		return value.intValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Integer value) {
		return BigInteger.valueOf(value);
	}
}
