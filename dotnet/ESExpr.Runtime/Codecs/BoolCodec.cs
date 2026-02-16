using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

internal sealed class BoolCodec : IESExprCodec<bool> {
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Bool()]);
	
	public bool IsEncodedEqual(bool a, bool b) => a == b;
	
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
