using System;
using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Float16 ])]
public sealed class HalfCodec : IESExprCodec<Half> {
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Float16()]);

	public bool IsEncodedEqual(Half a, Half b) =>
		BitConverter.HalfToUInt16Bits(a) == BitConverter.HalfToUInt16Bits(b);

	public Expr Encode(Half value) => new Expr.Float16(value);

	public Half Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Float16(var f)) {
			return f;
		}
		else if(expr is Expr.Float16NaN(var bits)) {
			return BitConverter.UInt16BitsToHalf(bits);
		}
		else {
			throw new DecodeException("Expected a float16 value", path);
		}
	}
}
