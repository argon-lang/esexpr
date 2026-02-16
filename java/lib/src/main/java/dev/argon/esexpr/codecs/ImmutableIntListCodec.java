package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.util.Arrays;

/**
 * A codec for Array32 values.
 */
public class ImmutableIntListCodec implements ESExprCodec<ImmutableIntList> {
	private ImmutableIntListCodec() {}

	/**
	 * A codec for binary values.
	 */
	
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY32 })
	public static final ESExprCodec<ImmutableIntList> INSTANCE = new ImmutableIntListCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY32);
	}

	@Override
	public boolean isEncodedEqual(ImmutableIntList x, ImmutableIntList y) {
		return x.equals(y);
	}

	@Override
	public ESExpr encode(ImmutableIntList value) {
		return new ESExpr.Array32(value);
	}

	@Override
	public ImmutableIntList decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array32(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array8 value", path);
		}
	}
}
