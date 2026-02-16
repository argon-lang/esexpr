package dev.argon.esexpr;

import dev.argon.esexpr.codecs.UnsignedByteCodec;

/**
 * A wrapper for an unsigned byte.
 */
public class UnsignedByte {

	private UnsignedByte(byte value) {
		this.value = value;
	}
	private final byte value;

	/**
	 * Returns the value as a byte.
	 * @return The value as a byte.
	 */
	public byte byteValue() {
		return value;
	}

	/**
	 * Returns the value as a short.
	 * @return The value as a short.
	 */
	public short shortValue() {
		return (short)intValue();
	}

	/**
	 * Returns the value as an int.
	 * @return The value as an int.
	 */
	public int intValue() {
		return Byte.toUnsignedInt(value);
	}

	/**
	 * Returns an {@link UnsignedByte} whose value is equal to the specified byte.
	 * @param value The value to wrap.
	 * @return An {@link UnsignedByte} instance.
	 */
	public static UnsignedByte valueOf(byte value) {
		return CACHE[Byte.toUnsignedInt(value)];
	}

	private static final UnsignedByte[] CACHE = new UnsignedByte[256];
	static {
		for(int i = 0; i < 256; i++) {
			CACHE[i] = new UnsignedByte((byte)i);
		}
	}

	@Override
	public int hashCode() {
		return Byte.hashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UnsignedByte ub && byteValue() == ub.byteValue();
	}

	@Override
	public String toString() {
		return Integer.toString(Byte.toUnsignedInt(value));
	}

	/**
	 * The codec for {@link UnsignedByte}.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = {ESExprTag.Scalar.INT})
	public static final ESExprCodec<UnsignedByte> INSTANCE = new UnsignedByteCodec();
}
