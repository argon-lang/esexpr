package dev.argon.esexpr.codecs;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprOverrideCodec;
import dev.argon.esexpr.Unsigned;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

/**
 * A codec for unsigned short values.
 */
public class UnsignedShortCodec extends IntCodecBase<Short> {
	private UnsignedShortCodec() {
		super(BigInteger.ZERO, BigInteger.valueOf(0xFFFF));
	}

	/**
	 * A codec for unsigned short values.
	 */
	@ESExprOverrideCodec(value = short.class, requiredAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Short.class, requiredAnnotations = Unsigned.class)
	public static final ESExprCodec<Short> INSTANCE = new UnsignedShortCodec();

	@Override
	protected @NotNull Short fromBigInt(@NotNull BigInteger value) {
		return value.shortValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Short value) {
		return BigInteger.valueOf(Short.toUnsignedLong(value));
	}
}
