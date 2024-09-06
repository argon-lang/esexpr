using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Diagnostics.CodeAnalysis;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;

namespace ESExpr.Runtime;

[CollectionBuilder(typeof(VList), nameof(VList.Create))]
public readonly struct VList<T> : IReadOnlyList<T>, IEquatable<VList<T>> {
	public VList(IImmutableList<T> list) {
		this.list = list;
	}

	private readonly IImmutableList<T>? list;
	
	
	public static VList<T> Empty => default;
	
	
	
	public IImmutableList<T> ImmutableList => list ?? ImmutableList<T>.Empty;


	IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

	public IEnumerator<T> GetEnumerator() => ImmutableList.GetEnumerator();

	public int Count => ImmutableList.Count;

	public T this[int index] => ImmutableList[index];

	public bool Equals(VList<T> other) => ImmutableList.SequenceEqual(other);

	public override bool Equals([NotNullWhen(true)] object? obj) {
		if(obj is VList<T> other) {
			return Equals(other);
		}
		else {
			return false;
		}
	}

	public override int GetHashCode() {
		var hash = new HashCode();
		foreach(var a in ImmutableList) {
			hash.Add(a);
		}
		return hash.ToHashCode();
	}
	
	public static bool operator ==(VList<T> left, VList<T> right) => left.Equals(right);
	public static bool operator !=(VList<T> left, VList<T> right) => !(left == right);
	
	public static implicit operator VList<T>(ImmutableList<T> list) => new VList<T>(list);


	public override string ToString() {
		var sb = new StringBuilder();

		sb.Append("[");

		int i = 0;
		foreach(var item in this) {
			if(i > 0) {
				sb.Append(",");
			}

			sb.Append(item);
			++i;
		}
		
		sb.Append("]");

		return sb.ToString();
	}


	public sealed class Codec : IESExprCodec<VList<T>> {
		public Codec(IESExprCodec<T> itemCodec) {
			this.itemCodec = itemCodec;
		}

		internal const string ListConstructor = "list";
	
		private readonly IESExprCodec<T> itemCodec;

		public ISet<ESExprTag> Tags => (HashSet<ESExprTag>) [new ESExprTag.Constructor(ListConstructor)];

		public Expr Encode(VList<T> value) {
			return new Expr.Constructor(ListConstructor, value.Select(itemCodec.Encode).ToImmutableList(), ImmutableDictionary<string, Expr>.Empty);
		}

		public VList<T> Decode(Expr expr, DecodeFailurePath path) {
			if(expr is Expr.Constructor(ListConstructor, var args, var kwargs)) {
				if(kwargs.Count != 0) {
					throw new DecodeException("Unexpected keyword arguments for list.", path.WithConstructor("list"));
				}

				return args.Select((arg, i) => itemCodec.Decode(arg, path.Append("list", i))).ToImmutableList();
			}
			else {
				throw new DecodeException("Expected a list constructor", path);
			}
		}
	}
	
	public sealed class VarargCodec : IVarargCodec<VList<T>> {
		public VarargCodec(IESExprCodec<T> itemCodec) {
			this.itemCodec = itemCodec;
		}
	
		private readonly IESExprCodec<T> itemCodec;
	
		public IEnumerable<Expr> EncodeVararg(VList<T> value) {
			return value.Select(itemCodec.Encode);
		}

		public VList<T> DecodeVararg(IReadOnlyList<Expr> value, Func<int, DecodeFailurePath> pathBuilder) {
			return value.Select((arg, index) => itemCodec.Decode(arg, pathBuilder(index))).ToImmutableList();
		}
	}
}

public static class VList {
	public static VList<T> Create<T>(ReadOnlySpan<T> values) => new VList<T>(ImmutableList.Create(values));
}
