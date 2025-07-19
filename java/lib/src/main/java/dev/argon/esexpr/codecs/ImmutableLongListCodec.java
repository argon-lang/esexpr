package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

import java.util.Arrays;

/**
 * A codec for Array64 values.
 */
public class ImmutableLongListCodec extends ESExprCodec<ImmutableLongList> {
	private ImmutableLongListCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(ImmutableLongList.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY64 })
	public static final ESExprCodec<ImmutableLongList> INSTANCE = new ImmutableLongListCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY64);
	}

	@Override
	public boolean isEncodedEqual(ImmutableLongList x, ImmutableLongList y) {
		return x.equals(y);
	}

	@Override
	public ESExpr encode(ImmutableLongList value) {
		return new ESExpr.Array64(value);
	}

	@Override
	public ImmutableLongList decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array64(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array64 value", path);
		}
	}
}
