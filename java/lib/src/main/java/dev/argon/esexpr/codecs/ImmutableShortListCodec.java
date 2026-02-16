package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.list.primitive.ImmutableShortList;

import java.util.Arrays;

/**
 * A codec for Array16 values.
 */
public class ImmutableShortListCodec implements ESExprCodec<ImmutableShortList> {
	private ImmutableShortListCodec() {}

	/**
	 * A codec for binary values.
	 */
	
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY16 })
	public static final ESExprCodec<ImmutableShortList> INSTANCE = new ImmutableShortListCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY16);
	}

	@Override
	public boolean isEncodedEqual(ImmutableShortList x, ImmutableShortList y) {
		return x.equals(y);
	}

	@Override
	public ESExpr encode(ImmutableShortList value) {
		return new ESExpr.Array16(value);
	}

	@Override
	public ImmutableShortList decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array16(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array16 value", path);
		}
	}
}
