package dev.argon.esexpr;

import dev.argon.esexpr.codecs.NonNegativeBigIntegerCodec;

import java.math.BigInteger;

/**
 * A wrapper for a non-negative {@link BigInteger}.
 */
public class UnsignedBigInteger {
	private UnsignedBigInteger(BigInteger value) {
		if(value == null) {
			throw new IllegalArgumentException("Value cannot be null");
		}

		if(value.signum() < 0) {
			throw new IllegalArgumentException("Value cannot be negative");
		}

		this.value = value;
	}

	private final BigInteger value;

	/**
	 * Converts the value to a {@link BigInteger}.
	 * @return The value as a {@link BigInteger}.
	 */
	public BigInteger toBigInteger() {
		return value;
	}

	/**
	 * Returns an {@link UnsignedBigInteger} whose value is equal to the specified {@link BigInteger}.
	 * @param value The value to wrap.
	 * @return An {@link UnsignedBigInteger} instance.
	 * @throws IllegalArgumentException if the value is negative.
	 */
	public static UnsignedBigInteger valueOf(BigInteger value) {
		return new UnsignedBigInteger(value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof UnsignedBigInteger ubi && value.equals(ubi.value);
	}

	@Override
	public String toString() {
		return value.toString();
	}

	/**
	 * The codec for {@link UnsignedBigInteger}.
	 */
	@TypeClassInstance
	public static ESExprCodec<UnsignedBigInteger> CODEC = new NonNegativeBigIntegerCodec();
}
