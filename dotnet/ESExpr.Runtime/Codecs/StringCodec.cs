using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
public sealed class StringCodec : IESExprCodec<string> {
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Str() ];
	public Expr Encode(string value) => new Expr.Str(value);

	public string Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Str(var s)) {
			return s;
		}
		else {
			throw new DecodeException("Expected a string value", path);
		}
	}
}
