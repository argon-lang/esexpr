using System;
using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IVarargCodec<T> {
	bool IsEncodedEqual(T a, T b);
	IEnumerable<Expr> EncodeVararg(T value);
	T DecodeVararg(IReadOnlyList<Expr> value, Func<int, DecodeFailurePath> pathBuilder);
}
