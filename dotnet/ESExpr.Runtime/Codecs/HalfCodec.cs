using System;
using System.Collections.Generic;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
public sealed class HalfCodec : IESExprCodec<Half> {
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[new ESExprTag.Float16()];
	public Expr Encode(Half value) => new Expr.Float16(value);

	public Half Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Float16(var f)) {
			return f;
		}
		else {
			throw new DecodeException("Expected a float16 value", path);
		}
	}
}
