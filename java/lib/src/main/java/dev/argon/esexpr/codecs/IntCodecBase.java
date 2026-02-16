package dev.argon.esexpr.codecs;

import java.math.BigInteger;
import java.util.Set;

import dev.argon.esexpr.*;

/**
 * Base type for sized integer codecs.
 * @param <T> The integer type.
 */
public abstract class IntCodecBase<T> implements ESExprCodec<T> {
	IntCodecBase(BigInteger min, BigInteger max) {
		this.min = min;
		this.max = max;
	}

	private final BigInteger min;
	private final BigInteger max;

	@Override
	public final ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.INT);
	}

	@Override
	public final ESExpr encode(T value) {
		return new ESExpr.Int(toBigInt(value));
	}

	@Override
	public final T decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Int(var i)) {
			if(i.compareTo(min) < 0 || i.compareTo(max) > 0) {
				throw new DecodeException("Integer value out of range", path);
			}

			return fromBigInt(i);
		}
		else {
			throw new DecodeException("Expected an integer value", path);
		}
	}

	abstract T fromBigInt(BigInteger value);
	abstract BigInteger toBigInt(T value);
}
