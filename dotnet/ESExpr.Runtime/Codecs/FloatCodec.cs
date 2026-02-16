using System;
using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

internal sealed class FloatCodec : IESExprCodec<float> {
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Float32()]);

	public bool IsEncodedEqual(float a, float b) =>
		BitConverter.SingleToUInt32Bits(a) == BitConverter.SingleToUInt32Bits(b);

	public Expr Encode(float value) => new Expr.Float32(value);

	public float Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Float32(var f)) {
			return f;
		}
		else if(expr is Expr.Float32NaN(var bits)) {
			return BitConverter.UInt32BitsToSingle(bits);
		}
		else {
			throw new DecodeException("Expected a float32 value", path);
		}
	}
}
