package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.factory.primitive.LongLists;

import java.util.Arrays;

/**
 * A codec for Array64 values.
 */
public class LongArrayCodec extends ESExprCodec<long[]> {
	private LongArrayCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(long[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY64 })
	public static final ESExprCodec<long[]> INSTANCE = new LongArrayCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY64);
	}

	@Override
	public boolean isEncodedEqual(long[] x, long[] y) {
		return Arrays.equals(x, y);
	}

	@Override
	public ESExpr encode(long[] value) {
		return new ESExpr.Array64(LongLists.immutable.of(value));
	}

	@Override
	public long[] decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array64(var b)) {
			return b.toArray();
		}
		else {
			throw new DecodeException("Expected an array64 value", path);
		}
	}
}
