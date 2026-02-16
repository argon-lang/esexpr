package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;

import java.util.Set;

/**
 * A codec for string values.
 */
public class StringCodec implements ESExprCodec<String> {
	private StringCodec() {}

	/**
	 * A codec for string values.
	 */
	
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.STR })
	public static final ESExprCodec<String> INSTANCE = new StringCodec();

	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.STR);
	}

	@Override
	public boolean isEncodedEqual(String x, String y) {
		return x.equals(y);
	}

	@Override
	public ESExpr encode(String value) {
		return new ESExpr.Str(value);
	}

	@Override
	public String decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Str(var s)) {
			return s;
		}
		else {
			throw new DecodeException("Expected a string value", path);
		}
	}
}
