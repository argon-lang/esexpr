package dev.argon.esexpr.codecs;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprOverrideCodec;
import dev.argon.esexpr.Unsigned;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

/**
 * A codec for unsigned byte values.
 */
public class UnsignedByteCodec extends IntCodecBase<Byte> {
	private UnsignedByteCodec() {
		super(BigInteger.ZERO, BigInteger.valueOf(0xFF));
	}

	/**
	 * A codec for unsigned byte values.
	 */
	@ESExprOverrideCodec(value = byte.class, requiredAnnotations = Unsigned.class)
	@ESExprOverrideCodec(value = Byte.class, requiredAnnotations = Unsigned.class)
	public static final ESExprCodec<Byte> INSTANCE = new UnsignedByteCodec();


	@Override
	protected @NotNull Byte fromBigInt(@NotNull BigInteger value) {
		return value.byteValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Byte value) {
		return BigInteger.valueOf(Byte.toUnsignedLong(value));
	}
}
