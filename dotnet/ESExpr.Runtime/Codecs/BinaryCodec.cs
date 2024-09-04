using System;
using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
public sealed class BinaryCodec : IESExprCodec<Binary> {
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Binary() ];
	public Expr Encode(Binary value) => new Expr.Binary(value);

	public Binary Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Binary(var b)) {
			return b;
		}
		else {
			throw new DecodeException("Expected a binary value", path);
		}
	}
}
