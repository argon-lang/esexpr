package dev.argon.esexpr.codecs;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprOverrideCodec;
import dev.argon.esexpr.Unsigned;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

/**
 * A codec for signed long values.
 */
public class SignedLongCodec extends IntCodecBase<Long> {
	private SignedLongCodec() {
		super(BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE));
	}

	/**
	 * A codec for signed long values.
	 */
	@ESExprOverrideCodec(value = long.class, excludedAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Long.class, excludedAnnotations = Unsigned.class)
	public static final ESExprCodec<Long> INSTANCE = new SignedLongCodec();

	@Override
	protected @NotNull Long fromBigInt(@NotNull BigInteger value) {
		return value.longValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Long value) {
		return BigInteger.valueOf(value);
	}
}
