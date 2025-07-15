using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IESExprCodec<T> {
	ESExprTagSet Tags { get; }
	bool IsEncodedEqual(T a, T b);
	Expr Encode(T value);
	T Decode(Expr expr, DecodeFailurePath path);

	sealed T Decode(Expr expr) => Decode(expr, new DecodeFailurePath.Current());
}
