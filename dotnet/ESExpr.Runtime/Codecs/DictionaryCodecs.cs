using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Diagnostics.CodeAnalysis;
using System.Linq;

namespace ESExpr.Runtime.Codecs;

public abstract class DictionaryCodecBase<T, TDict> : IESExprCodec<TDict>
	where TDict : IEnumerable<KeyValuePair<string, T>>
{
	private protected DictionaryCodecBase(IESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}
	
	private readonly IESExprCodec<T> itemCodec;
	
	protected abstract TDict CreateDictionary(IEnumerable<KeyValuePair<string, T>> items);
	protected abstract int GetCount(TDict dict);
	protected abstract bool TryGetValue(TDict dict, string key, [MaybeNullWhen(false)] out T value);
	
	
	internal const string DictConstructor = "dict";

	public ESExprTagSet Tags => itemCodec.Tags;
	
	public bool IsEncodedEqual(TDict a, TDict b) =>
		GetCount(a) == GetCount(b) &&
		a.All(kvp => {
			if(!TryGetValue(b, kvp.Key, out var bValue)) {
				return false;
			}
					
			return itemCodec.IsEncodedEqual(kvp.Value, bValue);
		});

	public Expr Encode(TDict value) {
		return new Expr.Constructor(
			DictConstructor,
			[],
			value.ToImmutableDictionary(
				entry => entry.Key,
				entry => itemCodec.Encode(entry.Value)
			)
		);
	}

	public TDict Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Constructor(DictConstructor, var args, var kwargs)) {
			if(args.Count != 0) {
				throw new DecodeException("Invalid positional arguments for dict", path.WithConstructor(DictConstructor));
			}

			return CreateDictionary(kwargs.Select(kvp =>
				new KeyValuePair<string, T>(
					kvp.Key,
					itemCodec.Decode(kvp.Value, path.Append(DictConstructor, kvp.Key))
				)
			));
		}
		else {
			throw new DecodeException("Expected a dict constructor.", path);
		}
	}
}

internal class DictionaryCodec<T> : DictionaryCodecBase<T, Dictionary<string, T>> {
	internal DictionaryCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override Dictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToDictionary();
	}

	protected override int GetCount(Dictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(Dictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class ImmutableDictionaryCodec<T> : DictionaryCodecBase<T, ImmutableDictionary<string, T>> {
	public ImmutableDictionaryCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override ImmutableDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToImmutableDictionary();
	}

	protected override int GetCount(ImmutableDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(ImmutableDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class InterfaceDictionaryCodec<T> : DictionaryCodecBase<T, IDictionary<string, T>> {
	public InterfaceDictionaryCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToDictionary();
	}

	protected override int GetCount(IDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(IDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class ReadOnlyDictionaryCodec<T> : DictionaryCodecBase<T, IReadOnlyDictionary<string, T>> {
	public ReadOnlyDictionaryCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IReadOnlyDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToImmutableDictionary();
	}

	protected override int GetCount(IReadOnlyDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(IReadOnlyDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class InterfaceImmutableDictionaryCodec<T> : DictionaryCodecBase<T, IImmutableDictionary<string, T>> {
	public InterfaceImmutableDictionaryCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IImmutableDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToImmutableDictionary();
	}

	protected override int GetCount(IImmutableDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(IImmutableDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

public abstract class DictionaryDictCodecBase<T, TDict> : IDictCodec<TDict, T>
	where TDict : IEnumerable<KeyValuePair<string, T>>
{
	private protected DictionaryDictCodecBase(IESExprCodec<T> itemCodec) {
		this.itemCodec = itemCodec;
	}
	
	private readonly IESExprCodec<T> itemCodec;
	
	protected abstract TDict CreateDictionary(IEnumerable<KeyValuePair<string, T>> items);
	protected abstract int GetCount(TDict dict);
	protected abstract bool TryGetValue(TDict dict, string key, [MaybeNullWhen(false)] out T value);
	
	
	internal const string DictConstructor = "dict";

	public ESExprTagSet ElementTags => itemCodec.Tags;
	
	public bool IsEncodedEqual(TDict a, TDict b) =>
		GetCount(a) == GetCount(b) &&
		a.All(kvp => {
			if(!TryGetValue(b, kvp.Key, out var bValue)) {
				return false;
			}
					
			return itemCodec.IsEncodedEqual(kvp.Value, bValue);
		});

	public IEnumerable<KeyValuePair<string, Expr>> EncodeDict(TDict value) {
		return value.Select(kvp => new KeyValuePair<string, Expr>(kvp.Key, itemCodec.Encode(kvp.Value)));
	}

	public TDict DecodeDict(IReadOnlyDictionary<string, Expr> exprs, Func<string, DecodeFailurePath> pathBuilder) {
		return CreateDictionary(exprs.Select(kvp => new KeyValuePair<string, T>(kvp.Key, itemCodec.Decode(kvp.Value, pathBuilder(kvp.Key)))));
	}
}

internal class DictionaryDictCodec<T> : DictionaryDictCodecBase<T, Dictionary<string, T>> {
	public DictionaryDictCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override Dictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToDictionary();
	}

	protected override int GetCount(Dictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(Dictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class ImmutableDictionaryDictCodec<T> : DictionaryDictCodecBase<T, ImmutableDictionary<string, T>> {
	public ImmutableDictionaryDictCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override ImmutableDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToImmutableDictionary();
	}

	protected override int GetCount(ImmutableDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(ImmutableDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class InterfaceDictionaryDictCodec<T> : DictionaryDictCodecBase<T, IDictionary<string, T>> {
	public InterfaceDictionaryDictCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToDictionary();
	}

	protected override int GetCount(IDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(IDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class ReadOnlyDictionaryDictCodec<T> : DictionaryDictCodecBase<T, IReadOnlyDictionary<string, T>> {
	public ReadOnlyDictionaryDictCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IReadOnlyDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToImmutableDictionary();
	}

	protected override int GetCount(IReadOnlyDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(IReadOnlyDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

internal class InterfaceImmutableDictionaryDictCodec<T> : DictionaryDictCodecBase<T, IImmutableDictionary<string, T>> {
	public InterfaceImmutableDictionaryDictCodec(IESExprCodec<T> itemCodec) : base(itemCodec) {
	}

	protected override IImmutableDictionary<string, T> CreateDictionary(IEnumerable<KeyValuePair<string, T>> items) {
		return items.ToImmutableDictionary();
	}

	protected override int GetCount(IImmutableDictionary<string, T> dict) {
		return dict.Count;
	}

	protected override bool TryGetValue(IImmutableDictionary<string, T> dict, string key, [MaybeNullWhen(false)] out T value) {
		return dict.TryGetValue(key, out value);
	}
}

