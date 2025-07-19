package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.factory.primitive.ShortLists;

import java.util.Arrays;

/**
 * A codec for Array16 values.
 */
public class ShortArrayCodec extends ESExprCodec<short[]> {
	private ShortArrayCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(short[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY16 })
	public static final ESExprCodec<short[]> INSTANCE = new ShortArrayCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY16);
	}

	@Override
	public boolean isEncodedEqual(short[] x, short[] y) {
		return Arrays.equals(x, y);
	}

	@Override
	public ESExpr encode(short[] value) {
		return new ESExpr.Array16(ShortLists.immutable.of(value));
	}

	@Override
	public short[] decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array16(var b)) {
			return b.toArray();
		}
		else {
			throw new DecodeException("Expected an array16 value", path);
		}
	}
}
