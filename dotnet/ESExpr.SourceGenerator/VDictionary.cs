using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Diagnostics.CodeAnalysis;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text;

namespace ESExpr.SourceGenerator;

public sealed class VDictionary<K, V> : IReadOnlyDictionary<K, V>, IEquatable<VDictionary<K, V>> where K : notnull {
	public VDictionary(IImmutableDictionary<K, V> values) {
		this.values = values;
	}

	private readonly IImmutableDictionary<K, V> values;
	
	public IImmutableDictionary<K, V> ImmutableDictionary => values ?? ImmutableDictionary<K, V>.Empty;


	IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

	public IEnumerator<KeyValuePair<K, V>> GetEnumerator() => ImmutableDictionary.GetEnumerator();

	public int Count => ImmutableDictionary.Count;


	public bool ContainsKey(K key) =>
		values.ContainsKey(key);

	public bool TryGetValue(K key, out V value) =>
		values.TryGetValue(key, out value);

	public V this[K key] => values[key];

	public IEnumerable<K> Keys => ImmutableDictionary.Keys;
	public IEnumerable<V> Values => ImmutableDictionary.Values;


	public bool Equals(VDictionary<K, V> other) {
		foreach(var k in other.values.Keys) {
			if(!values.ContainsKey(k)) {
				return false;
			}
		}

		foreach(var kvp in values) {
			if(!other.values.TryGetValue(kvp.Key, out var otherValue)) {
				return false;
			}
			
			if(!object.Equals(kvp.Value, otherValue)) {
				return false;
			}
		}

		return true;
	}

	public override bool Equals([NotNullWhen(true)] object? obj) {
		if(obj is VDictionary<K, V> other) {
			return Equals(other);
		}
		else {
			return false;
		}
	}

	public override int GetHashCode() {
		int hash = 17;
		foreach (var kvp in ImmutableDictionary) {
			hash = hash * 31 + kvp.Key.GetHashCode();
			hash = hash * 31 + (kvp.Value?.GetHashCode() ?? 0);
		}
		return hash;
	}
	
	public static bool operator ==(VDictionary<K, V> left, VDictionary<K, V> right) => left.Equals(right);
	public static bool operator !=(VDictionary<K, V> left, VDictionary<K, V> right) => !(left == right);


	public override string ToString() {
		var sb = new StringBuilder();

		sb.Append("{");

		int i = 0;
		foreach(var item in this) {
			if(i > 0) {
				sb.Append(",");
			}

			sb.Append(item);
			++i;
		}
		
		sb.Append("}");

		return sb.ToString();
	}
}

public static class VDictionary {
	public static VDictionary<K, V> From<K, V>(IEnumerable<KeyValuePair<K, V>> items) where K : notnull {
		return new VDictionary<K, V>(ImmutableDictionary.CreateRange(items));
	}
}
