using System.Collections.Generic;

namespace ESExpr.Runtime;

public interface IOptionalValueCodec<T> {
	ISet<ESExprTag> ElementTags { get; }
	
	Expr? EncodeOptional(T value);
	bool IsEncodedEqual(T a, T b);
	T DecodeOptional(Expr? value, DecodeFailurePath path);
}
