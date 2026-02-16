package dev.argon.esexpr;

import dev.argon.esexpr.codecs.UnsignedLongCodec;

import java.math.BigInteger;

/**
 * A wrapper for an unsigned long.
 */
public class UnsignedLong {

	private UnsignedLong(long value) {
		this.value = value;
	}
	private final long value;

	/**
	 * Returns the value as a long.
	 * @return The value as a long.
	 */
	public long longValue() {
		return value;
	}

	/**
	 * Returns the value as a {@link BigInteger}.
	 * @return The value as a {@link BigInteger}.
	 */
	public BigInteger bigIntegerValue() {
		return BigInteger.valueOf(value).and(
			BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
		);
	}
	
	/**
	 * Returns an {@link UnsignedLong} whose value is equal to the specified long.
	 * @param value The value to wrap.
	 * @return An {@link UnsignedLong} instance.
	 */
	public static UnsignedLong valueOf(long value) {
		if(value >= 0 && value < 256) {
			return CACHE[(int)value];
		}
		else {
			return new UnsignedLong(value);
		}
	}
	
	private static final UnsignedLong[] CACHE = new UnsignedLong[256];
	static {
		for(int i = 0; i < 256; i++) {
			CACHE[i] = new UnsignedLong(i);
		}
	}

	@Override
	public int hashCode() {
		return Long.hashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UnsignedLong ul && longValue() == ul.longValue();
	}
	
	@Override
	public String toString() {
		return Long.toUnsignedString(value);
	}


	/**
	 * The codec for {@link UnsignedLong}.
	 */
	@TypeClassInstance
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static final ESExprCodec<UnsignedLong> INSTANCE = new UnsignedLongCodec();
}
