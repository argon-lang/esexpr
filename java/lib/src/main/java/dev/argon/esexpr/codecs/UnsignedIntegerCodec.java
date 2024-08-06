package dev.argon.esexpr.codecs;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprOverrideCodec;
import dev.argon.esexpr.Unsigned;
import org.jetbrains.annotations.NotNull;

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
	public static final ESExprCodec<Integer> INSTANCE = new UnsignedIntegerCodec();

	@Override
	protected @NotNull Integer fromBigInt(@NotNull BigInteger value) {
		return value.intValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Integer value) {
		return BigInteger.valueOf(Integer.toUnsignedLong(value));
	}
}
