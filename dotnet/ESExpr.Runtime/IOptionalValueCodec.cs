using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IOptionalValueCodec<T, TE> {
	ESExprTagSet ElementTags { get; }
	
	Expr? EncodeOptional(T value);
	bool IsEncodedEqual(T a, T b);
	T DecodeOptional(Expr? value, DecodeFailurePath path);
}
