using System;
using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IDictCodec<T, TE> {
	ESExprTagSet ElementTags { get; }

	bool IsEncodedEqual(T a, T b);
	IEnumerable<KeyValuePair<string, Expr>> EncodeDict(T value);
	T DecodeDict(IReadOnlyDictionary<string, Expr> exprs, Func<string, DecodeFailurePath> pathBuilder);
}
