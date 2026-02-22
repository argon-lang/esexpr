using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Diagnostics.CodeAnalysis;
using System.Linq;

namespace ESExpr.Runtime;

public struct Dict<T> : IImmutableDictionary<string, T> {
    public Dict(IImmutableDictionary<string, T> dictionary) {
        this.dictionary = dictionary;
    }

    private readonly IImmutableDictionary<string, T> dictionary;
    private IImmutableDictionary<string, T> Dictionary => dictionary ?? ImmutableDictionary<string, T>.Empty;
    
    public ImmutableDictionary<string, T> ToImmutableDictionary() => Dictionary.ToImmutableDictionary();

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

    public IEnumerator<KeyValuePair<string, T>> GetEnumerator() => Dictionary.GetEnumerator();

    public int Count => Dictionary.Count;
    
    public bool ContainsKey(string key) => Dictionary.ContainsKey(key);

    public bool TryGetValue(string key, [MaybeNullWhen(false)] out T value) {
        return Dictionary.TryGetValue(key, out value);
    }

    public T this[string key] => Dictionary[key];

    public IEnumerable<string> Keys => Dictionary.Keys;
    public IEnumerable<T> Values => Dictionary.Values;

    public Dict<T> Add(string key, T value) =>
        new Dict<T>(Dictionary.Add(key, value));

    IImmutableDictionary<string, T> IImmutableDictionary<string, T>.Add(string key, T value) =>
        Dictionary.Add(key, value);
    
    public Dict<T> AddRange(IEnumerable<KeyValuePair<string, T>> pairs) =>
        new Dict<T>(Dictionary.AddRange(pairs));

    IImmutableDictionary<string, T> IImmutableDictionary<string, T>.AddRange(IEnumerable<KeyValuePair<string, T>> pairs) =>
		AddRange(pairs);

    
    public Dict<T> Clear() => new(Dictionary.Clear());

    IImmutableDictionary<string, T> IImmutableDictionary<string, T>.Clear() => Clear();

    public bool Contains(KeyValuePair<string, T> pair) => Dictionary.Contains(pair);

    public Dict<T> Remove(string key) => new(Dictionary.Remove(key));

    IImmutableDictionary<string, T> IImmutableDictionary<string, T>.Remove(string key) => Remove(key);

    public Dict<T> RemoveRange(IEnumerable<string> keys) => new(Dictionary.RemoveRange(keys));
    
    IImmutableDictionary<string, T> IImmutableDictionary<string, T>.RemoveRange(IEnumerable<string> keys) =>
        RemoveRange(keys);
    
    public Dict<T> SetItem(string key, T value) => new(Dictionary.SetItem(key, value));

    IImmutableDictionary<string, T> IImmutableDictionary<string, T>.SetItem(string key, T value) =>
        SetItem(key, value);

    public Dict<T> SetItems(IEnumerable<KeyValuePair<string, T>> items) => new(Dictionary.SetItems(items));
    
    IImmutableDictionary<string, T> IImmutableDictionary<string, T>.SetItems(IEnumerable<KeyValuePair<string, T>> items) =>
        SetItems(items);

    public bool TryGetKey(string equalKey, out string actualKey) =>
        Dictionary.TryGetKey(equalKey, out actualKey);
    
}

public static class Dict {
    [TypeClassInstance]
    public static IESExprCodec<Dict<T>> Codec<T>(IESExprCodec<T> elementCodec) => new DictExprCodec<T>(elementCodec);

    private sealed class DictExprCodec<T> : IESExprCodec<Dict<T>> {
        public DictExprCodec(IESExprCodec<T> elementCodec) {
            this.elementCodec = elementCodec;
        }
        
        private readonly IESExprCodec<T> elementCodec;

        public ESExprTagSet Tags { get; } = ESExprTagSet.Create([ new ESExprTag.Constructor("dict") ]);
        
        public bool IsEncodedEqual(Dict<T> a, Dict<T> b) {
            if(a.Count != b.Count) return false;
            
            foreach(var pair in a) {
                if(!b.TryGetValue(pair.Key, out var bValue)) return false;
                if(!elementCodec.IsEncodedEqual(pair.Value, bValue)) return false;
            }
            
            return true;
        }

        public Expr Encode(Dict<T> value) {
            var kwargs = ImmutableDictionary.CreateBuilder<string, Expr>();
            foreach(var (k, v) in value) {
                kwargs.Add(k, elementCodec.Encode(v));
            }
            return new Expr.Constructor("dict", [], kwargs.ToImmutable());
        }

        public Dict<T> Decode(Expr expr, DecodeFailurePath path) {
	        if(expr is not Expr.Constructor { constructor: "dict", args: var args, kwargs: var kwargs }) {
		        throw new DecodeException("Expected a dict constructor", path);
	        }

	        if(!args.IsEmpty) {
		        throw new DecodeException("Unexpected positional arguments to dict constructor", path);
	        }
	        
	        return new Dict<T>(kwargs.ToImmutableDictionary(
		        kv => kv.Key,
		        kv => elementCodec.Decode(kv.Value, path.Append("dict", kv.Key))
		    ));
        }
    }
    
    [TypeClassInstance]
    public static IDictCodec<Dict<T>, T> DictCodec<T>(IESExprCodec<T> elementCodec) => new DictDictCodec<T>(elementCodec);

    private sealed class DictDictCodec<T> : IDictCodec<Dict<T>, T> {
	    public DictDictCodec(IESExprCodec<T> elementCodec) {
		    this.elementCodec = elementCodec;
	    }
	    
	    private readonly IESExprCodec<T> elementCodec;

	    public ESExprTagSet ElementTags => elementCodec.Tags;
	    
	    public bool IsEncodedEqual(Dict<T> a, Dict<T> b) {
		    return Codec(elementCodec).IsEncodedEqual(a, b);
	    }

	    public IEnumerable<KeyValuePair<string, Expr>> EncodeDict(Dict<T> value) {
		    return value.Select(kv => new KeyValuePair<string, Expr>(kv.Key, elementCodec.Encode(kv.Value)));
	    }

	    public Dict<T> DecodeDict(IReadOnlyDictionary<string, Expr> exprs, Func<string, DecodeFailurePath> pathBuilder) {
		    return new Dict<T>(exprs.ToImmutableDictionary(
			    kv => kv.Key,
			    kv => elementCodec.Decode(kv.Value, pathBuilder(kv.Key))
		    ));
	    }
    }
}
