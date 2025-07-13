using System;
using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IDictCodec<T> {
	bool IsEncodedEqual(T a, T b);
	IReadOnlyDictionary<string, Expr> EncodeDict(T value);
	T DecodeDict(IReadOnlyDictionary<string, Expr> exprs, Func<string, DecodeFailurePath> pathBuilder);
}
