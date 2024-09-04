using System;
using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IVarargCodec<T> {
	IEnumerable<Expr> EncodeVararg(T value);
	T DecodeVararg(IReadOnlyList<Expr> value, Func<int, DecodeFailurePath> pathBuilder);
}
