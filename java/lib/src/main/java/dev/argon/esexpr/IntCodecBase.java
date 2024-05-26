package dev.argon.esexpr;

import java.math.BigInteger;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

abstract class IntCodecBase<T> implements ESExprCodec<T> {
	IntCodecBase(BigInteger min, BigInteger max) {
		this.min = min;
		this.max = max;
	}

	private final BigInteger min;
	private final BigInteger max;

	@Override
	public final @NotNull Set<@NotNull ESExprTag> tags() {
		return Set.of(new ESExprTag.Int());
	}

	@Override
	public final @NotNull ESExpr encode(@NotNull T value) {
		return new ESExpr.Int(toBigInt(value));
	}

	@Override
	public final @NotNull T decode(@NotNull ESExpr expr) throws DecodeException {
		if(expr instanceof ESExpr.Int(var i)) {
			if(i.compareTo(min) < 0 || i.compareTo(max) > 0) {
				throw new DecodeException("Integer value out of range");
			}

			return fromBigInt(i);
		}
		else {
			throw new DecodeException("Expected an integer value");
		}
	}

	protected abstract @NotNull T fromBigInt(@NotNull BigInteger value);
	protected abstract @NotNull BigInteger toBigInt(@NotNull T value);
}
