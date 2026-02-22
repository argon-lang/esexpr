using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Linq;
using System.Runtime.CompilerServices;
using ESExpr.Runtime.Codecs;

namespace ESExpr.Runtime;

[CollectionBuilder(typeof(VList), nameof(VList.Create))]
public readonly struct VList<T>(IImmutableList<T> list) : IImmutableList<T>, IEquatable<VList<T>>, IComparable<VList<T>> {
    public static VList<T> Empty { get; } = new(ImmutableList<T>.Empty);

    private readonly IImmutableList<T> List => list ?? ImmutableList<T>.Empty;

    public int Count => List.Count;

    public T this[int index] => List[index];

    public VList<T> Add(T value) => new(List.Add(value));

    public VList<T> AddRange(IEnumerable<T> items) => new(List.AddRange(items));

    public VList<T> Clear() => Empty;

    public int IndexOf(T item, int index, int count, IEqualityComparer<T>? equalityComparer) =>
        List.IndexOf(item, index, count, equalityComparer);

    public int IndexOf(T item) => List.IndexOf(item);

    public VList<T> Insert(int index, T element) => new(List.Insert(index, element));

    public VList<T> InsertRange(int index, IEnumerable<T> items) => new(List.InsertRange(index, items));

    public int LastIndexOf(T item, int index, int count, IEqualityComparer<T>? equalityComparer) =>
        List.LastIndexOf(item, index, count, equalityComparer);

    public int LastIndexOf(T item) => List.LastIndexOf(item);

    public VList<T> Remove(T value, IEqualityComparer<T>? equalityComparer) => new(List.Remove(value, equalityComparer));

    public VList<T> RemoveAll(Predicate<T> match) => new(List.RemoveAll(match));

    public VList<T> RemoveAt(int index) => new(List.RemoveAt(index));

    public VList<T> Remove(T value) => Remove(value, null);

    public VList<T> RemoveRange(IEnumerable<T> items, IEqualityComparer<T>? equalityComparer) =>
        new(List.RemoveRange(items, equalityComparer));

    public VList<T> RemoveRange(int index, int count) => new(List.RemoveRange(index, count));

    public VList<T> RemoveRange(IEnumerable<T> items) => RemoveRange(items, null);

    public VList<T> Replace(T oldValue, T newValue, IEqualityComparer<T>? equalityComparer) =>
        new(List.Replace(oldValue, newValue, equalityComparer));

    public VList<T> Replace(T oldValue, T newValue) => Replace(oldValue, newValue, null);

    public VList<T> SetItem(int index, T value) => new(List.SetItem(index, value));

    IImmutableList<T> IImmutableList<T>.Add(T value) => Add(value);

    IImmutableList<T> IImmutableList<T>.AddRange(IEnumerable<T> items) => AddRange(items);

    IImmutableList<T> IImmutableList<T>.Clear() => Clear();

    IImmutableList<T> IImmutableList<T>.Insert(int index, T element) => Insert(index, element);

    IImmutableList<T> IImmutableList<T>.InsertRange(int index, IEnumerable<T> items) => InsertRange(index, items);

    IImmutableList<T> IImmutableList<T>.Remove(T value, IEqualityComparer<T>? equalityComparer) =>
        Remove(value, equalityComparer);

    IImmutableList<T> IImmutableList<T>.RemoveAll(Predicate<T> match) => RemoveAll(match);

    IImmutableList<T> IImmutableList<T>.RemoveAt(int index) => RemoveAt(index);

    IImmutableList<T> IImmutableList<T>.RemoveRange(IEnumerable<T> items, IEqualityComparer<T>? equalityComparer) =>
        RemoveRange(items, equalityComparer);

    IImmutableList<T> IImmutableList<T>.RemoveRange(int index, int count) => RemoveRange(index, count);

    IImmutableList<T> IImmutableList<T>.Replace(T oldValue, T newValue, IEqualityComparer<T>? equalityComparer) =>
        Replace(oldValue, newValue, equalityComparer);

    IImmutableList<T> IImmutableList<T>.SetItem(int index, T value) => SetItem(index, value);

    public IEnumerator<T> GetEnumerator() => List.GetEnumerator();

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

    public bool Equals(VList<T> other) {
        if (Count != other.Count) return false;
        using var e1 = GetEnumerator();
        using var e2 = other.GetEnumerator();
        while(e1.MoveNext() && e2.MoveNext()) {
            if (!EqualityComparer<T>.Default.Equals(e1.Current, e2.Current)) return false;
        }
        return true;
    }

    public override bool Equals(object? obj) => obj is VList<T> other && Equals(other);

    public override int GetHashCode() {
        var hash = new HashCode();
        foreach (var item in this) {
            hash.Add(item);
        }
        return hash.ToHashCode();
    }

    public int CompareTo(VList<T> other) {
        using var e1 = GetEnumerator();
        using var e2 = other.GetEnumerator();
        while (true) {
            var m1 = e1.MoveNext();
            var m2 = e2.MoveNext();
            if(m1 && m2) {
                var cmp = Comparer<T>.Default.Compare(e1.Current, e2.Current);
                if(cmp != 0) return cmp;
            }
            else if(m1) {
                return 1;
            }
            else if(m2) {
                return -1;
            }
            else {
                return 0;
            }
        }
    }

    public static bool operator ==(VList<T> left, VList<T> right) => left.Equals(right);

    public static bool operator !=(VList<T> left, VList<T> right) => !left.Equals(right);

    public static bool operator <(VList<T> left, VList<T> right) => left.CompareTo(right) < 0;

    public static bool operator <=(VList<T> left, VList<T> right) => left.CompareTo(right) <= 0;

    public static bool operator >(VList<T> left, VList<T> right) => left.CompareTo(right) > 0;

    public static bool operator >=(VList<T> left, VList<T> right) => left.CompareTo(right) >= 0;
}

public static class VList {
    public static VList<T> Create<T>(ReadOnlySpan<T> items) {
        var builder = ImmutableList.CreateBuilder<T>();
        foreach (var item in items) {
            builder.Add(item);
        }
        return new VList<T>(builder.ToImmutable());
    }
    
    [TypeClassInstance]
    [ESExprTags(Constructors = [ "list" ])]
    public static IESExprCodec<VList<T>> Codec<T>(IESExprCodec<T> elementCodec) => new VListCodec<T>(elementCodec);
    
    [TypeClassInstance]
    public static IVarargCodec<VList<T>, T> VListCodec<T>(IESExprCodec<T> elementCodec) => new VListVarargCodec<T>(elementCodec);
    
}
