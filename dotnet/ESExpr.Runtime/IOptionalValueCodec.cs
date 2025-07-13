namespace ESExpr.Runtime;

public interface IOptionalValueCodec<T> {
	Expr? EncodeOptional(T value);
	bool IsEncodedEqual(T a, T b);
	T DecodeOptional(Expr? value, DecodeFailurePath path);
}
