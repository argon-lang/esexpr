using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Diagnostics.CodeAnalysis;
using System.Linq;
using System.Text;

namespace ESExpr.Runtime;

public readonly struct VDict<T> : IReadOnlyDictionary<string, T>, IEquatable<VDict<T>> {
	public VDict(IImmutableDictionary<string, T> dict) {
		this.dict = dict;
	}
	
	private readonly IImmutableDictionary<string, T>? dict;
	
	public IImmutableDictionary<string, T> ImmutableDictionary => dict ?? ImmutableDictionary<string, T>.Empty;

	IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

	public IEnumerator<KeyValuePair<string, T>> GetEnumerator() => ImmutableDictionary.GetEnumerator();

	public int Count => ImmutableDictionary.Count;
	public bool ContainsKey(string key) => ImmutableDictionary.ContainsKey(key);

	public bool TryGetValue(string key, [MaybeNullWhen(false)] out T value) =>
		ImmutableDictionary.TryGetValue(key, out value);

	public T this[string key] => ImmutableDictionary[key];

	public IEnumerable<string> Keys => ImmutableDictionary.Keys;
	public IEnumerable<T> Values => ImmutableDictionary.Values;

	public bool Equals(VDict<T> other) {
		if(Count != other.Count) return false;
		
		var valueCmp = EqualityComparer<T>.Default;
		return this.All(leftKvp =>
			other.TryGetValue(leftKvp.Key, out var rightValue) &&
				valueCmp.Equals(rightValue, leftKvp.Value)
		);
	}

	public override bool Equals([NotNullWhen(true)] object? obj) {
		if(obj is VDict<T> other) return Equals(other);
		else return false;
	}

	public override int GetHashCode() {
		var hash = new HashCode();
		foreach(var a in ImmutableDictionary) {
			hash.Add(a.Key);
			hash.Add(a.Value);
		}
		return hash.ToHashCode();
	}

	public static bool operator ==(VDict<T> left, VDict<T> right) => left.Equals(right);
	public static bool operator !=(VDict<T> left, VDict<T> right) => !(left == right);
	public static implicit operator VDict<T>(ImmutableDictionary<string, T> dict) => new VDict<T>(dict);
	
	public override string ToString() {
		var sb = new StringBuilder();

		sb.Append("[");

		int i = 0;
		foreach(var kvp in this) {
			if(i > 0) {
				sb.Append(",");
			}

			sb.Append(kvp.Key);
			sb.Append(" = ");
			sb.Append(kvp.Value);
			++i;
		}
		
		sb.Append("]");

		return sb.ToString();
	}
	
	public class Codec : IESExprCodec<VDict<T>> {
	
		public Codec(IESExprCodec<T> itemCodec) {
			this.itemCodec = itemCodec;
		}
		
		private readonly IESExprCodec<T> itemCodec;

		private const string DictConstructor = "dict";
		
		public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Constructor(DictConstructor) ];
		public Expr Encode(VDict<T> value) {
			var map = new DictCodec(itemCodec).EncodeDict(value);
			return new Expr.Constructor(
				DictConstructor,
				[],
				map.ToImmutableDictionary()
			);
		}

		public VDict<T> Decode(Expr expr, DecodeFailurePath path) {
			if(expr is Expr.Constructor(DictConstructor, var args, var kwargs)) {
				if(args.Count != 0) {
					throw new DecodeException("Invalid positional arguments for dict", path.WithConstructor(DictConstructor));
				}

				return new DictCodec(itemCodec).DecodeDict(kwargs, kw => path.Append(DictConstructor, kw));
			}
			else {
				throw new DecodeException("Expected a dict constructor.", path);
			}
		}
	}
	
	public sealed class DictCodec : IDictCodec<VDict<T>> {
		public DictCodec(IESExprCodec<T> itemCodec) {
			this.itemCodec = itemCodec;
		}
		
		private readonly IESExprCodec<T> itemCodec;
		
		public IReadOnlyDictionary<string, Expr> EncodeDict(VDict<T> value) {
			return value.ToImmutableDictionary(
				entry => entry.Key,
				entry => itemCodec.Encode(entry.Value)
			);
		}

		public VDict<T> DecodeDict(IReadOnlyDictionary<string, Expr> exprs, Func<string, DecodeFailurePath> pathBuilder) {
			return exprs.ToImmutableDictionary(
				entry => entry.Key,
				entry => itemCodec.Decode(entry.Value, pathBuilder(entry.Key))
			);
		}
	
	}

}
