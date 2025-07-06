package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
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
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<Short> INSTANCE = new UnsignedShortCodec();

	@Override
	public boolean isEncodedEqual(Short x, Short y) {
		return x.shortValue() == y.shortValue();
	}

	@Override
	protected @NotNull Short fromBigInt(@NotNull BigInteger value) {
		return value.shortValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Short value) {
		return BigInteger.valueOf(Short.toUnsignedLong(value));
	}
}
