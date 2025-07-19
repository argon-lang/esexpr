package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.list.primitive.ImmutableByteList;

/**
 * A codec for Array8 values.
 */
public class ImmutableByteListCodec extends ESExprCodec<ImmutableByteList> {
	private ImmutableByteListCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(ImmutableByteList.class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY8 })
	public static final ESExprCodec<ImmutableByteList> INSTANCE = new ImmutableByteListCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY8);
	}

	@Override
	public boolean isEncodedEqual(ImmutableByteList x, ImmutableByteList y) {
		return x.equals(y);
	}

	@Override
	public ESExpr encode(ImmutableByteList value) {
		return new ESExpr.Array8(value);
	}

	@Override
	public ImmutableByteList decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array8(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected an array8 value", path);
		}
	}
}
