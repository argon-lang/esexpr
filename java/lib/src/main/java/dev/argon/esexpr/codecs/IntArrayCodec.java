package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.util.Arrays;

/**
 * A codec for Array32 values.
 */
public class IntArrayCodec extends ESExprCodec<int[]> {
	private IntArrayCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(int[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY32 })
	public static final ESExprCodec<int[]> INSTANCE = new IntArrayCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY32);
	}

	@Override
	public boolean isEncodedEqual(int[] x, int[] y) {
		return Arrays.equals(x, y);
	}

	@Override
	public ESExpr encode(int[] value) {
		return new ESExpr.Array32(IntLists.immutable.of(value));
	}

	@Override
	public int[] decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array32(var b)) {
			return b.toArray();
		}
		else {
			throw new DecodeException("Expected an array8 value", path);
		}
	}
}
