package dev.argon.esexpr.codecs;

import dev.argon.esexpr.ESExprCodec;
import dev.argon.esexpr.ESExprOverrideCodec;
import dev.argon.esexpr.Unsigned;
import org.jetbrains.annotations.NotNull;

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
	public static final ESExprCodec<Byte> INSTANCE = new SignedByteCodec();


	@Override
	protected @NotNull Byte fromBigInt(@NotNull BigInteger value) {
		return value.byteValue();
	}

	@Override
	protected @NotNull BigInteger toBigInt(@NotNull Byte value) {
		return BigInteger.valueOf(value);
	}
}
