using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
public sealed class FloatCodec : IESExprCodec<float> {
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Float32() ];
	public Expr Encode(float value) => new Expr.Float32(value);

	public float Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Float32(var f)) {
			return f;
		}
		else {
			throw new DecodeException("Expected a float32 value", path);
		}
	}
}
