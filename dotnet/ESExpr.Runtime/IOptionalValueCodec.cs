namespace ESExpr.Runtime;

public interface IOptionalValueCodec<T> {
	Expr? EncodeOptional(T value);
	T DecodeOptional(Expr? value, DecodeFailurePath path);
}
