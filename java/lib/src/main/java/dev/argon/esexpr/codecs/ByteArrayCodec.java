package dev.argon.esexpr.codecs;

import dev.argon.esexpr.*;
import org.eclipse.collections.api.factory.primitive.ByteLists;

import java.util.Arrays;

/**
 * A codec for Array8 values.
 */
public class ByteArrayCodec extends ESExprCodec<byte[]> {
	private ByteArrayCodec() {}

	/**
	 * A codec for binary values.
	 */
	@ESExprOverrideCodec(byte[].class)
	@ESExprCodecTags(scalar = { ESExprTag.Scalar.ARRAY8 })
	public static final ESExprCodec<byte[]> INSTANCE = new ByteArrayCodec();


	@Override
	public ESExprTagSet tags() {
		return ESExprTagSet.of(ESExprTag.ARRAY8);
	}

	@Override
	public boolean isEncodedEqual(byte[] x, byte[] y) {
		return Arrays.equals(x, y);
	}

	@Override
	public ESExpr encode(byte[] value) {
		return new ESExpr.Array8(ByteLists.immutable.of(value));
	}

	@Override
	public byte[] decode(ESExpr expr, FailurePath path) throws DecodeException {
		if(expr instanceof ESExpr.Array8(var b)) {
			return b.toArray();
		}
		else {
			throw new DecodeException("Expected an array8 value", path);
		}
	}
}
