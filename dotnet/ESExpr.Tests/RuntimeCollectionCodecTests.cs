using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class RuntimeCollectionCodecTests {
	private static Expr.Constructor Constructor(string name, params Expr[] args) => new(name, args.ToImmutableList(), ImmutableDictionary<string, Expr>.Empty);

	private static void CheckList<T>(IESExprCodec<T> codec, Func<int[], T> create) {
		var value = create([1, 2]);
		Assert.That(codec.Encode(value), Is.EqualTo(Constructor("list", new Expr.Int(1), new Expr.Int(2))));
		CheckCollection(codec, create, "list");
		var badKeywords = Constructor("list") with { kwargs = ImmutableDictionary<string, Expr>.Empty.Add("x", new Expr.Int(1)) };
		Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(badKeywords))!.Path, Is.EqualTo(new DecodeFailurePath.Constructor("list")));
		Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(Constructor("list", new Expr.Int(1), new Expr.Str("bad"))))!.Path,
			Is.EqualTo(new DecodeFailurePath.Positional("list", 1, new DecodeFailurePath.Current())));
	}

	private static void CheckCollection<T>(IESExprCodec<T> codec, Func<int[], T> create, string name) {
		Assert.That(codec.Tags.Contains(new ESExprTag.Constructor(name)), Is.True);
		foreach(var items in new[] { Array.Empty<int>(), new[] { 1, 2 } }) {
			var value = create(items);
			Assert.That(codec.IsEncodedEqual(value, codec.Decode(codec.Encode(value))), Is.True);
		}
		Assert.That(codec.IsEncodedEqual(create([1]), create([1, 2])), Is.False);
		Assert.That(codec.IsEncodedEqual(create([1]), create([2])), Is.False);
		Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Bool(false)));
		Assert.Throws<DecodeException>(() => codec.Decode(Constructor("wrong")));
	}

	[Test]
	public void ListVariantsRoundTripAndRejectMalformedValues() {
		CheckList(IESExprCodec.ListCodec(IESExprCodec.IntCodec), x => x.ToList());
		CheckList(IESExprCodec.ImmutableListCodec(IESExprCodec.IntCodec), x => x.ToImmutableList());
		CheckList(IESExprCodec.InterfaceListCodec(IESExprCodec.IntCodec), x => x.ToList());
		CheckList(IESExprCodec.ReadOnlyListCodec(IESExprCodec.IntCodec), x => x.ToList());
		CheckList(IESExprCodec.InterfaceImmutableListCodec(IESExprCodec.IntCodec), x => x.ToImmutableList());
		CheckList(IESExprCodec.VListCodec(IESExprCodec.IntCodec), x => new VList<int>(x.ToImmutableList()));
	}

	private static void CheckDictionary<T>(IESExprCodec<T> codec, Func<Dictionary<string, int>, T> create) {
		var value = create(new() { ["a"] = 1, ["b"] = 2 });
		var expr = new Expr.Constructor("dict", [], ImmutableDictionary<string, Expr>.Empty.Add("a", new Expr.Int(1)).Add("b", new Expr.Int(2)));
		Assert.That(codec.Encode(value), Is.EqualTo(expr));
		Assert.That(codec.IsEncodedEqual(value, codec.Decode(expr)), Is.True);
		Assert.That(codec.IsEncodedEqual(create(new()), codec.Decode(Constructor("dict"))), Is.True);
		Assert.That(codec.IsEncodedEqual(value, create(new() { ["a"] = 1 })), Is.False);
		Assert.That(codec.IsEncodedEqual(value, create(new() { ["a"] = 1, ["c"] = 2 })), Is.False);
		Assert.That(codec.IsEncodedEqual(value, create(new() { ["a"] = 1, ["b"] = 3 })), Is.False);
		Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Bool(false)));
		Assert.Throws<DecodeException>(() => codec.Decode(Constructor("dict", new Expr.Int(1))));
		var bad = expr with { kwargs = expr.kwargs.SetItem("b", new Expr.Str("bad")) };
		Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(bad))!.Path, Is.EqualTo(new DecodeFailurePath.Keyword("dict", "b", new DecodeFailurePath.Current())));
	}

	[Test]
	public void DictionaryVariantsRoundTripAndReportInvalidEntries() {
		CheckDictionary(IESExprCodec.DictionaryCodec(IESExprCodec.IntCodec), x => x);
		CheckDictionary(IESExprCodec.ImmutableDictionaryCodec(IESExprCodec.IntCodec), x => x.ToImmutableDictionary());
		CheckDictionary(IESExprCodec.InterfaceDictionaryCodec(IESExprCodec.IntCodec), x => x);
		CheckDictionary(IESExprCodec.ReadOnlyDictionaryCodec(IESExprCodec.IntCodec), x => x);
		CheckDictionary(IESExprCodec.InterfaceImmutableDictionaryCodec(IESExprCodec.IntCodec), x => x.ToImmutableDictionary());
		CheckDictionary(Dict.Codec(IESExprCodec.IntCodec), x => new Dict<int>(x.ToImmutableDictionary()));
	}

	private static void CheckDictArguments<T>(IDictCodec<T, int> codec, Func<Dictionary<string, int>, T> create) {
		Assert.That(codec.ElementTags.Contains(new ESExprTag.Int()), Is.True);
		var value = create(new() { ["a"] = 1 });
		var encoded = codec.EncodeDict(value).ToDictionary();
		Assert.That(encoded["a"], Is.EqualTo(new Expr.Int(1)));
		Assert.That(codec.IsEncodedEqual(value, codec.DecodeDict(encoded, k => new DecodeFailurePath.Keyword("outer", k, new DecodeFailurePath.Current()))), Is.True);
		Assert.That(codec.IsEncodedEqual(value, create(new())), Is.False);
		Assert.That(codec.IsEncodedEqual(value, create(new() { ["b"] = 1 })), Is.False);
		Assert.That(codec.IsEncodedEqual(value, create(new() { ["a"] = 2 })), Is.False);
		encoded["a"] = new Expr.Str("bad");
		Assert.That(Assert.Throws<DecodeException>(() => codec.DecodeDict(encoded, k => new DecodeFailurePath.Constructor(k)))!.Path, Is.EqualTo(new DecodeFailurePath.Constructor("a")));
	}

	[Test]
	public void DictArgumentVariantsUseProvidedPaths() {
		CheckDictArguments(IDictCodec.DictionaryDictCodec(IESExprCodec.IntCodec), x => x);
		CheckDictArguments(IDictCodec.ImmutableDictionaryDictCodec(IESExprCodec.IntCodec), x => x.ToImmutableDictionary());
		CheckDictArguments(IDictCodec.InterfaceDictionaryDictCodec(IESExprCodec.IntCodec), x => x);
		CheckDictArguments(IDictCodec.ReadOnlyDictionaryDictCodec(IESExprCodec.IntCodec), x => x);
		CheckDictArguments(IDictCodec.InterfaceImmutableDictionaryDictCodec(IESExprCodec.IntCodec), x => x.ToImmutableDictionary());
		CheckDictArguments(Dict.DictCodec(IESExprCodec.IntCodec), x => new Dict<int>(x.ToImmutableDictionary()));
	}

	private static void CheckVarargs<T>(IVarargCodec<T, int> codec, Func<int[], T> create) {
		var value = create([1, 2]);
		Assert.That(codec.EncodeVararg(value), Is.EqualTo(new Expr[] { new Expr.Int(1), new Expr.Int(2) }));
		var remaining = new SliceList<Expr>(new Expr[] { new Expr.Int(1), new Expr.Int(2), new Expr.Str("stop"), new Expr.Int(3) });
		var decoded = codec.DecodeVararg(ref remaining, i => new DecodeFailurePath.Positional("outer", i, new DecodeFailurePath.Current()));
		Assert.That(codec.IsEncodedEqual(value, decoded), Is.True);
		Assert.That(remaining, Is.EqualTo(new Expr[] { new Expr.Str("stop"), new Expr.Int(3) }));
		Assert.That(codec.IsEncodedEqual(create([]), codec.DecodeVararg(ref remaining, _ => new DecodeFailurePath.Current())), Is.True);
		Assert.That(remaining.Count, Is.EqualTo(2));
		Assert.That(codec.IsEncodedEqual(value, create([1])), Is.False);
		Assert.That(codec.IsEncodedEqual(value, create([1, 3])), Is.False);
		var overflow = new SliceList<Expr>(new Expr[] { new Expr.Int(1), new Expr.Int((System.Numerics.BigInteger)int.MaxValue + 1) });
		Assert.That(Assert.Throws<DecodeException>(() => codec.DecodeVararg(ref overflow, i => new DecodeFailurePath.Constructor(i.ToString())))!.Path, Is.EqualTo(new DecodeFailurePath.Constructor("1")));
	}

	[Test]
	public void VarargsConsumeOnlyTheMatchingPrefix() {
		CheckVarargs(IVarargCodec.ListCodec(IESExprCodec.IntCodec), x => x.ToList());
		CheckVarargs(IVarargCodec.ImmutableListCodec(IESExprCodec.IntCodec), x => x.ToImmutableList());
		CheckVarargs(IVarargCodec.InterfaceListCodec(IESExprCodec.IntCodec), x => x.ToList());
		CheckVarargs(IVarargCodec.ReadOnlyListCodec(IESExprCodec.IntCodec), x => x.ToList());
		CheckVarargs(IVarargCodec.VListCodec(IESExprCodec.IntCodec), x => new VList<int>(x.ToImmutableList()));
	}

	private static void CheckSet<T>(IESExprCodec<T> codec, Func<int[], T> create) {
		CheckCollection(codec, create, "set");
		Assert.That(codec.IsEncodedEqual(create([1, 2]), create([2, 1])), Is.True);
		Assert.That(codec.IsEncodedEqual(create([1]), codec.Decode(Constructor("set", new Expr.Int(1), new Expr.Int(1)))), Is.True);
		Assert.Throws<DecodeException>(() => codec.Decode(Constructor("set") with { kwargs = ImmutableDictionary<string, Expr>.Empty.Add("x", new Expr.Int(1)) }));
		Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(Constructor("set", new Expr.Str("bad"))))!.Path, Is.EqualTo(new DecodeFailurePath.Positional("set", 0, new DecodeFailurePath.Current())));
	}

	[Test]
	public void SetVariantsIgnoreOrderAndRejectMalformedValues() {
		CheckSet(IESExprCodec.SetCodec(IESExprCodec.IntCodec), x => x.ToHashSet());
		CheckSet(IESExprCodec.ImmutableHashSetCodec(IESExprCodec.IntCodec), x => x.ToImmutableHashSet());
		CheckSet(IESExprCodec.ReadOnlySetCodec(IESExprCodec.IntCodec), x => x.ToHashSet());
		CheckSet(IESExprCodec.InterfaceImmutableSetCodec(IESExprCodec.IntCodec), x => x.ToImmutableHashSet());
	}

	private static void CheckMap<T>(IESExprCodec<T> codec, Func<Dictionary<int, string>, T> create) {
		var value = create(new() { [1] = "a" });
		Assert.That(codec.Tags.Contains(new ESExprTag.Constructor("map")), Is.True);
		Assert.That(codec.Encode(value), Is.EqualTo(Constructor("map", new Expr.Int(1), new Expr.Str("a"))));
		Assert.That(codec.IsEncodedEqual(value, codec.Decode(codec.Encode(value))), Is.True);
		Assert.That(codec.IsEncodedEqual(create(new()), codec.Decode(Constructor("map"))), Is.True);
		Assert.That(codec.IsEncodedEqual(value, create(new())), Is.False);
		Assert.That(codec.IsEncodedEqual(value, create(new() { [2] = "a" })), Is.False);
		Assert.That(codec.IsEncodedEqual(value, create(new() { [1] = "b" })), Is.False);
		Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Bool(false)));
		Assert.Throws<DecodeException>(() => codec.Decode(Constructor("map", new Expr.Int(1))));
		Assert.Throws<DecodeException>(() => codec.Decode(Constructor("map") with { kwargs = ImmutableDictionary<string, Expr>.Empty.Add("x", new Expr.Int(1)) }));
		foreach(var index in new[] { 0, 1 }) {
			Expr[] args = [new Expr.Int(1), new Expr.Str("a")]; args[index] = new Expr.Bool(false);
			Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(Constructor("map", args)))!.Path, Is.EqualTo(new DecodeFailurePath.Positional("map", index, new DecodeFailurePath.Current())));
		}
	}

	[Test]
	public void MapVariantsRoundTripAndReportKeyAndValuePaths() {
		CheckMap(IESExprCodec.MapCodec(IESExprCodec.IntCodec, IESExprCodec.StrCodec), x => x);
		CheckMap(IESExprCodec.ImmutableMapCodec(IESExprCodec.IntCodec, IESExprCodec.StrCodec), x => x.ToImmutableDictionary());
		CheckMap(IESExprCodec.ReadOnlyMapCodec(IESExprCodec.IntCodec, IESExprCodec.StrCodec), x => x);
		CheckMap(IESExprCodec.InterfaceImmutableMapCodec(IESExprCodec.IntCodec, IESExprCodec.StrCodec), x => x.ToImmutableDictionary());
	}
}
