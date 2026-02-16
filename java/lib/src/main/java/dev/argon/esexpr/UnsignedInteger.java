package dev.argon.esexpr;

import dev.argon.esexpr.codecs.UnsignedIntegerCodec;

/**
 * A wrapper for an unsigned integer.
 */
public class UnsignedInteger {

	private UnsignedInteger(int value) {
		this.value = value;
	}
	private final int value;

	/**
	 * Returns the value as an int.
	 * @return The value as an int.
	 */
	public int intValue() {
		return value;
	}

	/**
	 * Returns the value as a long.
	 * @return The value as a long.
	 */
	public long longValue() {
		return Integer.toUnsignedLong(value);
	}
	
	/**
	 * Returns an {@link UnsignedInteger} whose value is equal to the specified int.
	 * @param value The value to wrap.
	 * @return An {@link UnsignedInteger} instance.
	 */
	public static UnsignedInteger valueOf(int value) {
		if(value >= 0 && value < 256) {
			return CACHE[value];
		}
		else {
			return new UnsignedInteger(value);
		}
	}

	private static final UnsignedInteger[] CACHE = new UnsignedInteger[256];
	static {
		for(int i = 0; i < 256; i++) {
			CACHE[i] = new UnsignedInteger(i);
		}
	}

	@Override
	public int hashCode() {
		return Integer.hashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UnsignedInteger ui && intValue() == ui.intValue();
	}
	
	@Override
	public String toString() {
		return Integer.toUnsignedString(value);
	}

	/**
	 * The codec for {@link UnsignedInteger}.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = {ESExprTag.Scalar.INT})
	public static final ESExprCodec<UnsignedInteger> INSTANCE = new UnsignedIntegerCodec();
}
