using System;
using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IVarargCodec<T, TE> {
	ESExprTagSet ElementTags { get; }

	bool IsEncodedEqual(T a, T b);
	IEnumerable<Expr> EncodeVararg(T value);
	T DecodeVararg(ref SliceList<Expr> value, Func<int, DecodeFailurePath> pathBuilder);
}
