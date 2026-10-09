using System;
using System.Collections.Generic;
using System.Text;

namespace ESExpr.Runtime;

/// <summary>Records expression strings to build a compact, frequency-ordered binary string table.</summary>
public sealed class StringTableBuilder {
	private sealed class Usage {
		public long Required;
		public long Literal;
		public long Total => Required > long.MaxValue - Literal ? long.MaxValue : Required + Literal;
	}

	private readonly Dictionary<string, Usage> strings = new(StringComparer.Ordinal);

	/// <summary>Records constructor names, keyword names, and literals recursively.</summary>
	/// <param name="expr">The expression to record.</param>
	public void Record(Expr expr) {
		switch(expr) {
			case Expr.Constructor(var name, var args, var kwargs):
				if(!IsDedicatedConstructor(name)) RecordString(name, required: true);
				foreach(var arg in args) Record(arg);
				foreach(var (key, value) in kwargs) {
					RecordString(key, required: true);
					Record(value);
				}
				break;
			case Expr.Str(var value):
				RecordString(value, required: false);
				break;
		}
	}

	private void RecordString(string value, bool required) {
		if(!strings.TryGetValue(value, out var usage)) {
			usage = new Usage();
			strings.Add(value, usage);
		}
		if(required) {
			if(usage.Required < long.MaxValue) ++usage.Required;
		}
		else if(usage.Literal < long.MaxValue) ++usage.Literal;
	}

	/// <summary>
	/// Builds a table by decreasing combined frequency, with Unicode scalar ordering for ties.
	/// Constructor and keyword entries are mandatory. Literal-only entries must occur at least
	/// twice and save more bytes than their inline table-entry cost at their prospective index.
	/// Shared framing and effects on later indices are excluded from this heuristic.
	/// Recorded usage is retained and the returned list is independent.
	/// </summary>
	/// <returns>The selected strings in table-index order.</returns>
	public IList<string> Build() {
		var candidates = new List<string>();
		foreach(var (value, usage) in strings) {
			if(usage.Required != 0 || usage.Literal > 1) candidates.Add(value);
		}
		candidates.Sort((a, b) => {
			int order = strings[b].Total.CompareTo(strings[a].Total);
			return order != 0 ? order : CompareStrings(a, b);
		});
		int accepted = 0;
		for(int i = 0; i < candidates.Count; ++i) {
			var value = candidates[i];
			var usage = strings[value];
			long inline = InlineStringSize(value);
			long saving = inline - TaggedIntSize(accepted);
			// Division avoids overflowing count * saving.
			if(usage.Required != 0 || (saving > 0 && usage.Literal > inline / saving)) {
				candidates[accepted++] = value;
			}
		}
		candidates.RemoveRange(accepted, candidates.Count - accepted);
		return candidates;
	}

	private static int CompareStrings(string a, string b) {
		var left = a.EnumerateRunes().GetEnumerator();
		var right = b.EnumerateRunes().GetEnumerator();
		while(true) {
			bool hasLeft = left.MoveNext();
			bool hasRight = right.MoveNext();
			if(!hasLeft || !hasRight) return hasLeft.CompareTo(hasRight);
			int diff = left.Current.Value.CompareTo(right.Current.Value);
			if(diff != 0) return diff;
		}
	}

	internal static bool IsDedicatedConstructor(string name) => name is "list" or "map" or "set" or "string-table";

	internal static int TaggedIntSize(int value) {
		int size = 1;
		value >>= 4;
		while(value != 0) {
			++size;
			value >>= 7;
		}
		return size;
	}

	internal static long InlineStringSize(string value) {
		int size = Encoding.UTF8.GetByteCount(value);
		return (long)size + TaggedIntSize(size);
	}
}
