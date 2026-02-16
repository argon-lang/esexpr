package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

/**
 * A codec for boolean values.
 */
public class BooleanCodec implements ESExprCodec<Boolean> {
	private BooleanCodec() {}

	/**
	 * A codec for boolean values.
	 */
	
	
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.BOOL })
	public static final ESExprCodec<Boolean> INSTANCE = new BooleanCodec();

	@Override
	public boolean isEncodedEqual(Boolean x, Boolean y) {
		return x.booleanValue() == y.booleanValue();
	}

	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.BOOL);
	}

	@Override
	public ESExpr encode(Boolean value) {
		return new ESExpr.Bool(value);
	}

	@Override
	public Boolean decode(ESExpr expr, ESExprCodec.FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Bool(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected a boolean value", path);
		}
	}
}
