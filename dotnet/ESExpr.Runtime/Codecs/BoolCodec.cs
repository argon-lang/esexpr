using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
public sealed class BoolCodec : IESExprCodec<bool> {
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Bool() ];
	public Expr Encode(bool value) => new Expr.Bool(value);

	public bool Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Bool(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected a bool value", path);
		}
	}
}
