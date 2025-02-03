using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Diagnostics.CodeAnalysis;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;

namespace ESExpr.SourceGenerator;

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
		int hash = 17;
		foreach (var a in ImmutableList) {
			hash = hash * 31 + (a?.GetHashCode() ?? 0);
		}
		return hash;
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
}

public static class VList {
	public static VList<T> Of<T>(params T[] items) {
		return new VList<T>(ImmutableList.Create(items));
	}

	public static VList<T> From<T>(IEnumerable<T> items) {
		return new VList<T>(ImmutableList.CreateRange(items));
	}
}
