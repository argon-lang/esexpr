using System;
using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IDictCodec<T> {
	IReadOnlyDictionary<string, Expr> EncodeDict(T value);
	T DecodeDict(IReadOnlyDictionary<string, Expr> exprs, Func<string, DecodeFailurePath> pathBuilder);
}
