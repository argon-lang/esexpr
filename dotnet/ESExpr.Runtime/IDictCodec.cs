using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using ESExpr.Runtime.Codecs;

namespace ESExpr.Runtime;

public interface IDictCodec<T, TE> {
	ESExprTagSet ElementTags { get; }

	bool IsEncodedEqual(T a, T b);
	IEnumerable<KeyValuePair<string, Expr>> EncodeDict(T value);
	T DecodeDict(IReadOnlyDictionary<string, Expr> exprs, Func<string, DecodeFailurePath> pathBuilder);
}

public static class IDictCodec {
	[TypeClassInstance]
	public static IDictCodec<Dictionary<string, T>, T> DictionaryDictCodec<T>(IESExprCodec<T> itemCodec) =>
		new DictionaryDictCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IDictCodec<ImmutableDictionary<string, T>, T>
		ImmutableDictionaryDictCodec<T>(IESExprCodec<T> itemCodec) => new ImmutableDictionaryDictCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IDictCodec<IDictionary<string, T>, T> InterfaceDictionaryDictCodec<T>(IESExprCodec<T> itemCodec) =>
		new InterfaceDictionaryDictCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IDictCodec<IReadOnlyDictionary<string, T>, T>
		ReadOnlyDictionaryDictCodec<T>(IESExprCodec<T> itemCodec) => new ReadOnlyDictionaryDictCodec<T>(itemCodec);

	[TypeClassInstance]
	public static IDictCodec<IImmutableDictionary<string, T>, T>
		InterfaceImmutableDictionaryDictCodec<T>(IESExprCodec<T> itemCodec) =>
		new InterfaceImmutableDictionaryDictCodec<T>(itemCodec);

}
