using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

public sealed class StringCodec : IESExprCodec<string> {
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Str()]);

	public bool IsEncodedEqual(string a, string b) => a == b;

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
