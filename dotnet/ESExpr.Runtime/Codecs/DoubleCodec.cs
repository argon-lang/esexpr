using System;
using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Float64 ])]
public sealed class DoubleCodec : IESExprCodec<double> {
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Float64()]);
	
	public bool IsEncodedEqual(double a, double b) =>
		BitConverter.DoubleToUInt64Bits(a) == BitConverter.DoubleToUInt64Bits(b);
	
	public Expr Encode(double value) => new Expr.Float64(value);

	public double Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Float64(var d)) {
			return d;
		}
		else {
			throw new DecodeException("Expected a bool value", path);
		}
	}
}
