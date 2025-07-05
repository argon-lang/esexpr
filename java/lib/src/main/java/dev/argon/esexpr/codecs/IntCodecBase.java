package dev.argon.esexpr.codecs;

import java.math.BigInteger;
import java.util.Set;

import dev.argon.esexpr.*;
import org.jetbrains.annotations.NotNull;

/**
 * Base type for sized integer codecs.
 * @param <T> The integer type.
 */
public abstract class IntCodecBase<T> extends ESExprCodec<T> {
	IntCodecBase(BigInteger min, BigInteger max) {
		this.min = min;
		this.max = max;
	}

	private final BigInteger min;
	private final BigInteger max;

	@Override
	public final @NotNull ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.INT);
	}

	@Override
	public final @NotNull ESExpr encode(@NotNull T value) {
		return new ESExpr.Int(toBigInt(value));
	}

	@Override
	public final @NotNull T decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
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

	abstract @NotNull T fromBigInt(@NotNull BigInteger value);
	abstract @NotNull BigInteger toBigInt(@NotNull T value);
}
