using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
public sealed class DoubleCodec : IESExprCodec<double> {
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Float64() ];
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
