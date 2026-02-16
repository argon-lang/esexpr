package dev.argon.esexpr;

import dev.argon.esexpr.codecs.UnsignedShortCodec;

/**
 * A wrapper for an unsigned short.
 */
public class UnsignedShort {

	private UnsignedShort(short value) {
		this.value = value;
	}
	private final short value;

	/**
	 * Returns the value as a short.
	 * @return The value as a short.
	 */
	public short shortValue() {
		return value;
	}

	/**
	 * Returns the value as an int.
	 * @return The value as an int.
	 */
	public int intValue() {
		return Short.toUnsignedInt(value);
	}
	
	/**
	 * Returns an {@link UnsignedShort} whose value is equal to the specified short.
	 * @param value The value to wrap.
	 * @return An {@link UnsignedShort} instance.
	 */
	public static UnsignedShort valueOf(short value) {
		if(value >= 0 && value < 256) {
			return CACHE[value];
		}
		else {
			return new UnsignedShort(value);
		}
	}

	private static final UnsignedShort[] CACHE = new UnsignedShort[256];
	static {
		for(int i = 0; i < 256; i++) {
			CACHE[i] = new UnsignedShort((short)i);
		}
	}
	/**
	 * Returns an {@link UnsignedShort} whose value is equal to the specified int.
	 * @param value The value to wrap.
	 * @return An {@link UnsignedShort} instance.
	 */
	public static UnsignedShort valueOf(int value) {
		return CACHE[value & 0xFF];
	}

	@Override
	public int hashCode() {
		return Short.hashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UnsignedShort us && shortValue() == us.shortValue();
	}
	
	@Override
	public String toString() {
		return Integer.toString(Short.toUnsignedInt(value));
	}

	/**
	 * The codec for {@link UnsignedShort}.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = {ESExprTag.Scalar.INT})
	public static final ESExprCodec<UnsignedShort> INSTANCE = new UnsignedShortCodec();
}
