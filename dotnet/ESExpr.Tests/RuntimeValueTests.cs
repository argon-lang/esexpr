using System.Collections;
using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class RuntimeValueTests {
	[Test]
	public void DictUpdatesPreserveTheOriginalAndSupportTheImmutableInterface() {
		var empty = default(Dict<int>);
		Assert.That(empty.Count, Is.Zero);
		var original = empty.Add("a", 1);
		var updated = original.AddRange(new Dictionary<string, int> { ["b"] = 2 }).SetItem("a", 3).SetItems(new Dictionary<string, int> { ["c"] = 4 });
		Assert.That(empty.Count, Is.Zero);
		Assert.That(original["a"], Is.EqualTo(1));
		Assert.That(updated.Keys, Is.EquivalentTo(new[] { "a", "b", "c" }));
		Assert.That(updated.Values, Is.EquivalentTo(new[] { 3, 2, 4 }));
		Assert.That(updated.ContainsKey("a"), Is.True);
		Assert.That(updated.Contains(new KeyValuePair<string, int>("a", 3)), Is.True);
		Assert.That(updated.TryGetValue("missing", out _), Is.False);
		Assert.That(updated.TryGetValue("b", out var b), Is.True);
		Assert.That(b, Is.EqualTo(2));
		Assert.That(updated.TryGetKey("a", out var key), Is.True);
		Assert.That(key, Is.EqualTo("a"));
		Assert.That(updated.ToImmutableDictionary(), Is.EquivalentTo(updated));
		Assert.That(((IEnumerable)updated).Cast<KeyValuePair<string, int>>(), Is.EquivalentTo(updated));
		Assert.That(updated.Remove("a").ContainsKey("a"), Is.False);
		Assert.That(updated.RemoveRange(["a", "b"]).Keys, Is.EqualTo(new[] { "c" }));
		Assert.That(updated.Clear().Count, Is.Zero);
		IImmutableDictionary<string, int> dict = original;
		dict = dict.Add("b", 2).AddRange(new Dictionary<string, int> { ["c"] = 3 }).SetItem("a", 4).SetItems(new Dictionary<string, int> { ["b"] = 5 });
		Assert.That(dict["a"], Is.EqualTo(4));
		Assert.That(dict["b"], Is.EqualTo(5));
		Assert.That(dict.Remove("a").RemoveRange(["b", "c"]).Count, Is.Zero);
		Assert.That(dict.Clear().Count, Is.Zero);
		Assert.That(original.Count, Is.EqualTo(1));
	}

	[Test]
	public void TagSetsSupportFiniteAndUniversalOperations() {
		var integer = new ESExprTag.Int();
		var text = new ESExprTag.Str();
		var finite = ESExprTagSet.Empty.Add(integer);
		var other = ESExprTagSet.Create((IEnumerable<ESExprTag>)new[] { text });
		Assert.That(finite.IsAll, Is.False);
		Assert.That(finite.IsEmpty, Is.False);
		Assert.That(ESExprTagSet.Empty.IsEmpty, Is.True);
		Assert.That(finite.Contains(integer), Is.True);
		Assert.That(finite.Contains(text), Is.False);
		Assert.That(finite.IsDisjointFrom(other), Is.True);
		Assert.That(finite.IsDisjointFrom(finite), Is.False);
		var union = finite.Union(other);
		Assert.That(union.Contains(integer) && union.Contains(text), Is.True);
		Assert.That(union, Is.EqualTo(ESExprTagSet.Create(new ESExprTag[] { integer, text }.AsSpan())));
		Assert.That(finite.Equals(other), Is.False);
		Assert.That(finite.Equals("different"), Is.False);
		Assert.That(finite.Visit(() => -1, x => x.Count), Is.EqualTo(1));
		Assert.That(finite.GetHashCode(), Is.EqualTo(ESExprTagSet.Empty.Add(integer).GetHashCode()));
		Assert.That(finite.ToString(), Does.Contain("Finite"));
		var all = ESExprTagSet.All;
		Assert.That(all.IsAll, Is.True);
		Assert.That(all.IsEmpty, Is.False);
		Assert.That(all.Contains(integer), Is.True);
		Assert.That(all.Visit(() => 42, _ => -1), Is.EqualTo(42));
		Assert.That(all.Add(text), Is.EqualTo(all));
		Assert.That(all.Union(finite), Is.EqualTo(all));
		Assert.That(finite.Union(all), Is.EqualTo(all));
		Assert.That(all.IsDisjointFrom(ESExprTagSet.Empty), Is.True);
		Assert.That(ESExprTagSet.Empty.IsDisjointFrom(all), Is.True);
		Assert.That(all.IsDisjointFrom(finite), Is.False);
		Assert.That(finite.IsDisjointFrom(all), Is.False);
		Assert.That(all.Equals(finite), Is.False);
		Assert.That(all.GetHashCode(), Is.EqualTo(ESExprTagSet.All.GetHashCode()));
		Assert.That(all.ToString(), Is.EqualTo("All Tags"));
	}

	[Test]
	public void FailurePathsAppendAtTheInnermostLocation() {
		DecodeFailurePath current = new DecodeFailurePath.Current();
		DecodeFailurePath constructor = new DecodeFailurePath.Constructor("old");
		foreach(var leaf in new[] { current, constructor }) {
			Assert.That(leaf.WithConstructor("new"), Is.EqualTo(new DecodeFailurePath.Constructor("new")));
			Assert.That(leaf.Append("inner", 2), Is.EqualTo(new DecodeFailurePath.Positional("inner", 2, leaf)));
			Assert.That(leaf.Append("inner", "key"), Is.EqualTo(new DecodeFailurePath.Keyword("inner", "key", leaf)));
		}
		DecodeFailurePath nested = new DecodeFailurePath.Keyword("outer", "field", new DecodeFailurePath.Positional("middle", 1, current));
		Assert.That(nested.WithConstructor("inner"), Is.EqualTo(new DecodeFailurePath.Keyword("outer", "field", new DecodeFailurePath.Positional("middle", 1, new DecodeFailurePath.Constructor("inner")))));
		Assert.That(nested.Append("inner", 2), Is.EqualTo(new DecodeFailurePath.Keyword("outer", "field", new DecodeFailurePath.Positional("middle", 1, new DecodeFailurePath.Positional("inner", 2, current)))));
		Assert.That(nested.Append("inner", "key"), Is.EqualTo(new DecodeFailurePath.Keyword("outer", "field", new DecodeFailurePath.Positional("middle", 1, new DecodeFailurePath.Keyword("inner", "key", current)))));
	}

	[Test]
	public void NestedCollectionsReportTheFullFailurePath() {
		var codec = IESExprCodec.ListCodec(Dict.Codec(IESExprCodec.ListCodec(IESExprCodec.IntCodec)));
		var badList = new Expr.Constructor("list", [new Expr.Str("bad")], ImmutableDictionary<string, Expr>.Empty);
		var dict = new Expr.Constructor("dict", [], ImmutableDictionary<string, Expr>.Empty.Add("numbers", badList));
		var expr = new Expr.Constructor("list", [dict], ImmutableDictionary<string, Expr>.Empty);
		Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(expr))!.Path,
			Is.EqualTo(new DecodeFailurePath.Positional("list", 0, new DecodeFailurePath.Keyword("dict", "numbers", new DecodeFailurePath.Positional("list", 0, new DecodeFailurePath.Current())))));
	}

	[Test]
	public void ConstructorEqualityUsesAllFieldsAndExprCodecIsIdentity() {
		var expr = new Expr.Constructor("sample", [new Expr.Int(1)], ImmutableDictionary<string, Expr>.Empty.Add("key", new Expr.Str("value")));
		var copy = new Expr.Constructor("sample", [new Expr.Int(1)], ImmutableDictionary<string, Expr>.Empty.Add("key", new Expr.Str("value")));
		Assert.That(expr, Is.EqualTo(copy));
		Assert.That(expr.GetHashCode(), Is.EqualTo(copy.GetHashCode()));
		Assert.That(expr.ToString(), Does.StartWith("sample(").And.Contain("key:").And.Contain("value"));
		foreach(var different in new[] {
			expr with { constructor = "other" }, expr with { args = [] }, expr with { args = [new Expr.Int(2)] },
			expr with { kwargs = ImmutableDictionary<string, Expr>.Empty },
			expr with { kwargs = ImmutableDictionary<string, Expr>.Empty.Add("other", new Expr.Str("value")) },
			expr with { kwargs = expr.kwargs.SetItem("key", new Expr.Str("other")) },
		}) Assert.That(expr, Is.Not.EqualTo(different));
		Assert.That(expr.Equals(null), Is.False);
		Assert.That(Expr.Codec.Encode(expr), Is.SameAs(expr));
		Assert.That(Expr.Codec.Decode(expr), Is.SameAs(expr));
		Assert.That(Expr.Codec.IsEncodedEqual(expr, copy), Is.True);
		Assert.That(Expr.Codec.Tags, Is.EqualTo(ESExprTagSet.All));
	}

	[Test]
	public void SliceListChecksBoundsRelativeToTheSlice() {
		var slice = new SliceList<int>(new[] { 0, 1, 2, 3 }, 1, 2);
		Assert.That(slice[0], Is.EqualTo(1));
		Assert.That(((IEnumerable)slice).Cast<int>(), Is.EqualTo(new[] { 1, 2 }));
		Assert.That(slice.Slice(1), Is.EqualTo(new[] { 2 }));
		Assert.That(slice.Slice(2).Count, Is.Zero);
		Assert.Throws<IndexOutOfRangeException>(() => _ = slice[-1]);
		Assert.Throws<IndexOutOfRangeException>(() => _ = slice[2]);
		Assert.Throws<ArgumentOutOfRangeException>(() => slice.Slice(-1));
		Assert.Throws<ArgumentOutOfRangeException>(() => slice.Slice(3));
		Assert.Throws<ArgumentOutOfRangeException>(() => slice.Slice(0, -1));
		Assert.Throws<ArgumentOutOfRangeException>(() => slice.Slice(1, 2));
	}
}
