using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using ESExpr.Runtime.Codecs;

namespace ESExpr.Runtime;

public interface IVarargCodec<T, TE> {
	ESExprTagSet ElementTags { get; }

	bool IsEncodedEqual(T a, T b);
	IEnumerable<Expr> EncodeVararg(T value);
	T DecodeVararg(ref SliceList<Expr> value, Func<int, DecodeFailurePath> pathBuilder);
}

public static class IVarargCodec {

	[TypeClassInstance]
	public static IVarargCodec<List<T>, T> ListCodec<T>(IESExprCodec<T> itemCodec) =>
		new ListVarargCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IVarargCodec<ImmutableList<T>, T> ImmutableListCodec<T>(IESExprCodec<T> itemCodec) =>
		new ImmutableListVarargCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IVarargCodec<IList<T>, T> InterfaceListCodec<T>(IESExprCodec<T> itemCodec) =>
		new InterfaceListVarargCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IVarargCodec<IReadOnlyList<T>, T> ReadOnlyListCodec<T>(IESExprCodec<T> itemCodec) =>
		new ReadOnlyListVarargCodec<T>(itemCodec);

}
