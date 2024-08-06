package dev.argon.esexpr.codecs;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprOverrideCodec;
import dev.argon.esexpr.Unsigned;
import org.jetbrains.annotations.NotNull;

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
	public static final ESExprCodec<Long> INSTANCE = new UnsignedLongCodec();

	@Override
	protected @NotNull Long fromBigInt(@NotNull BigInteger value) {
		return value.longValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Long value) {
		return BigInteger.valueOf(value).and(
			BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
		);
	}
}
