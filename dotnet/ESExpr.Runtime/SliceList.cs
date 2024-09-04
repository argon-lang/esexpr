using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;

namespace ESExpr.Runtime;

public class SliceList<T> : IReadOnlyList<T> {
	public SliceList(IReadOnlyList<T> inner) {
		this.inner = inner;
		start = 0;
		count = inner.Count;
	}

	public SliceList(IReadOnlyList<T> inner, int start, int count) {
		this.inner = inner;
		this.start = start;
		this.count = count;
	}

	private readonly IReadOnlyList<T> inner;
	private readonly int start;
	private readonly int count;

	IEnumerator IEnumerable.GetEnumerator() {
		return GetEnumerator();
	}

	public IEnumerator<T> GetEnumerator() {
		return inner.Skip(start).Take(count).GetEnumerator();
	}

	public int Count => count;

	public T this[int index] {
		get {
			if(index < 0 || index >= count) {
				throw new IndexOutOfRangeException();
			}

			return inner[start + index];
		}
	}

	public SliceList<T> Slice(int start, int count) {
		if(start < 0 || start > this.count) {
			throw new ArgumentOutOfRangeException(nameof(start));
		}

		if(count < 0 || start + count > this.count) {
			throw new ArgumentOutOfRangeException(nameof(count));
		}

		return new SliceList<T>(inner, this.start + start, count);
	}

	public SliceList<T> Slice(int start) => Slice(start, count - start);
}
