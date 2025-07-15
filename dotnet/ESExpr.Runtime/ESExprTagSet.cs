using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Collections.ObjectModel;
using System.Runtime.CompilerServices;

#if ESEXPR_SOURCE_GENERATOR
namespace ESExpr.SourceGenerator;
#else
namespace ESExpr.Runtime;
#endif

#if ESEXPR_SOURCE_GENERATOR
internal
#else
public
#endif
abstract class ESExprTagSet {

	private sealed class Finite(ImmutableHashSet<ESExprTag> tags) : ESExprTagSet {
		private ImmutableHashSet<ESExprTag> Tags => tags;

		public override bool IsAll => false;
		public override bool IsEmpty => tags.Count == 0;

		public override T Visit<T>(Func<T> visitAll, Func<ImmutableHashSet<ESExprTag>, T> visitFinite) =>
			visitFinite(tags);


		public override bool Contains(ESExprTag tag) => tags.Contains(tag);

		
		public override bool IsDisjointFrom(ESExprTagSet other) => other.IsDisjointFromSet(tags);
		protected override bool IsDisjointFromSet(ImmutableHashSet<ESExprTag> other) => !tags.Overlaps(other);

		public override ESExprTagSet Add(ESExprTag tag) =>
			new Finite(tags.Add(tag));

		public override ESExprTagSet Union(ESExprTagSet other) => other.UnionFromSet(tags);

		protected override ESExprTagSet UnionFromSet(ImmutableHashSet<ESExprTag> other) =>
			new Finite(tags.Union(other));
		

		public override bool Equals(object? obj) =>
			obj is Finite other && tags.SetEquals(other.Tags);

		public override int GetHashCode() {
#if ESEXPR_SOURCE_GENERATOR
			int hash = 0;
			foreach(var t in tags) {
				hash = hash * 31 + t.GetHashCode();
			}
			return hash;
#else
			var hash = new HashCode();
			foreach(var t in tags) {
				hash.Add(t);
			}
			return hash.ToHashCode();
#endif
		}

		public override string ToString() {
			return $"Finite({string.Join(", ", tags)})";
		}
	}
	
	private sealed class AllTags : ESExprTagSet {
		public override bool IsAll => true;
		public override bool IsEmpty => false;
		
		public override T Visit<T>(Func<T> visitAll, Func<ImmutableHashSet<ESExprTag>, T> visitFinite) =>
			visitAll();

		public override bool Contains(ESExprTag tag) => true;

		public override bool IsDisjointFrom(ESExprTagSet other) => other.IsEmpty;
		protected override bool IsDisjointFromSet(ImmutableHashSet<ESExprTag> other) => other.Count == 0;

		public override ESExprTagSet Add(ESExprTag tag) => this;

		public override ESExprTagSet Union(ESExprTagSet other) => this;
		protected override ESExprTagSet UnionFromSet(ImmutableHashSet<ESExprTag> other) => this;

		public override bool Equals(object? obj) {
			return obj is AllTags;
		}

		public override int GetHashCode() {
			return 7;
		}

		public override string ToString() {
			return "All Tags";
		}
	}
	
	private static readonly ESExprTagSet allTags = new AllTags(); 
	public static ESExprTagSet All => allTags;
	
	private static readonly ESExprTagSet emptyTags = new Finite(ImmutableHashSet<ESExprTag>.Empty);
	public static ESExprTagSet Empty => emptyTags;
	
	public static ESExprTagSet Create(IEnumerable<ESExprTag> tags) => new Finite(tags.ToImmutableHashSet());

#if !ESEXPR_SOURCE_GENERATOR
	public static ESExprTagSet Create(ReadOnlySpan<ESExprTag> tags) {
		return new Finite(ImmutableHashSet.Create(tags));
	}
#endif

	public abstract T Visit<T>(Func<T> visitAll, Func<ImmutableHashSet<ESExprTag>, T> visitFinite);
	
	public abstract bool IsAll { get;  }
	public abstract bool IsEmpty { get; }
	
	public abstract bool Contains(ESExprTag tag);
	
	public abstract bool IsDisjointFrom(ESExprTagSet other);
	protected abstract bool IsDisjointFromSet(ImmutableHashSet<ESExprTag> other);
	
	public abstract ESExprTagSet Add(ESExprTag tag);
	
	public abstract ESExprTagSet Union(ESExprTagSet other);
	protected abstract ESExprTagSet UnionFromSet(ImmutableHashSet<ESExprTag> other);

}
