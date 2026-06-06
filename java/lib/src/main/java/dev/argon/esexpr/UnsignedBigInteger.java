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

	/**
	 * Returns an {@link UnsignedBigInteger} whose value is equal to the specified long.
	 * @param value The value to wrap.
	 * @return An {@link UnsignedBigInteger} instance.
	 * @throws IllegalArgumentException if the value is negative.
	 */
	public static UnsignedBigInteger valueOf(long value) {
		return valueOf(BigInteger.valueOf(value));
	}

	/**
	 * Parses a string as an {@link UnsignedBigInteger}.
	 * @param value The value to parse.
	 * @return An {@link UnsignedBigInteger} instance.
	 * @throws NumberFormatException if the value is not
	 */
	public static UnsignedBigInteger valueOf(String value) {
		return valueOf(new BigInteger(value));
	}

	/**
	 * A constant representing the value zero as an {@link UnsignedBigInteger}.
	 */
	public static final UnsignedBigInteger ZERO = valueOf(BigInteger.ZERO);

	/**
	 * A constant representing the value one as an {@link UnsignedBigInteger}.
	 */
	public static final UnsignedBigInteger ONE = valueOf(BigInteger.ONE);


	/**
	 * Adds the value of the specified {@link UnsignedBigInteger} to this instance and returns the result as a new {@link UnsignedBigInteger}.
	 *
	 * @param other The {@link UnsignedBigInteger} to be added to this instance.
	 * @return A new {@link UnsignedBigInteger} representing the sum of this instance and the specified {@link UnsignedBigInteger}.
	 */
	public UnsignedBigInteger add(UnsignedBigInteger other) {
		return valueOf(value.add(other.value));
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
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.INT })
	public static ESExprCodec<UnsignedBigInteger> CODEC = new NonNegativeBigIntegerCodec();
}
