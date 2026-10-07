using System.Collections.Immutable;
using System.Globalization;
using System.Numerics;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class RuntimeRemainingCoverageTests {
	private enum UnannotatedEnum { Value }

	[Test]
	public void OptionEqualityDistinguishesPresenceAndValues() {
		var values = new[] { Option<int>.Empty, new Option<int>(0), new Option<int>(1), new Option<int>(1) };
		for(int i = 0; i < values.Length; ++i) {
			for(int j = 0; j < values.Length; ++j) {
				bool equal = i == j || i >= 2 && j >= 2;
				Assert.That(values[i].Equals(values[j]), Is.EqualTo(equal), $"typed equality: {i}, {j}");
				Assert.That(values[i].Equals((object)values[j]), Is.EqualTo(equal));
				Assert.That(values[i] == values[j], Is.EqualTo(equal));
				Assert.That(values[i] != values[j], Is.EqualTo(!equal));
				if(equal) Assert.That(values[i].GetHashCode(), Is.EqualTo(values[j].GetHashCode()));
			}
		}
		Assert.That(values[0].Equals((object)"other"), Is.False);
		Assert.That(values[0].Equals((object?)null), Is.False);
		Assert.That(values[0].GetHashCode(), Is.Zero);
		Assert.That(values[2].GetHashCode(), Is.EqualTo(1.GetHashCode()));
		Assert.That(new Option<string?>(null).GetHashCode(), Is.Zero);
		Assert.That(new HashSet<Option<int>>(values).Count, Is.EqualTo(3));
		Assert.That(values[0].ToString(), Is.EqualTo("None"));
		Assert.That(values[2].ToString(), Is.EqualTo("Some(1)"));
		Assert.That(values[0].TryGetValue(out var missing), Is.False);
		Assert.That(missing, Is.Zero);
		Assert.That(values[2].TryGetValue(out var present), Is.True);
		Assert.That(present, Is.EqualTo(1));
	}

	[Test]
	public void OptionCodecsCompareAllPresenceCombinations() {
		var codec = Option.Codec(IESExprCodec.IntCodec);
		var optional = Option.OptionOptionalValueCodec(IESExprCodec.IntCodec);
		var values = new[] { Option<int>.Empty, new Option<int>(1), new Option<int>(2) };
		for(int i = 0; i < values.Length; ++i) {
			for(int j = 0; j < values.Length; ++j) {
				Assert.That(codec.IsEncodedEqual(values[i], values[j]), Is.EqualTo(i == j));
				Assert.That(optional.IsEncodedEqual(values[i], values[j]), Is.EqualTo(i == j));
			}
		}
		Assert.That(codec.Tags, Is.EqualTo(ESExprTagSet.Create([new ESExprTag.Int(), new ESExprTag.Null()])));
		Assert.That(optional.ElementTags, Is.EqualTo(IESExprCodec.IntCodec.Tags));
		Assert.That(optional.EncodeOptional(values[0]), Is.Null);
		Assert.That(optional.EncodeOptional(values[1]), Is.EqualTo(new Expr.Int(1)));
		var path = new DecodeFailurePath.Constructor("outer");
		Assert.That(optional.DecodeOptional(null, path).IsSome, Is.False);
		Assert.That(optional.DecodeOptional(new Expr.Int(1), path), Is.EqualTo(values[1]));
		Assert.That(Assert.Throws<DecodeException>(() => optional.DecodeOptional(new Expr.Str("bad"), path))!.Path, Is.EqualTo(path));
		Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Str("bad"), path))!.Path, Is.EqualTo(path));
	}

	[Test]
	public void NestedOptionsPreserveEveryLevelOfAbsence() {
		var codec = Option.Codec(Option.Codec(IESExprCodec.IntCodec));
		var none = Option<Option<int>>.Empty;
		var someNone = new Option<Option<int>>(Option<int>.Empty);
		var someSome = new Option<Option<int>>(new Option<int>(7));
		Assert.That(codec.Encode(none), Is.EqualTo(new Expr.Null(0)));
		Assert.That(codec.Encode(someNone), Is.EqualTo(new Expr.Null(1)));
		Assert.That(codec.Encode(someSome), Is.EqualTo(new Expr.Int(7)));
		Assert.That(codec.Decode(new Expr.Null(0)), Is.EqualTo(none));
		Assert.That(codec.Decode(new Expr.Null(1)), Is.EqualTo(someNone));
		Assert.That(codec.Decode(new Expr.Int(7)), Is.EqualTo(someSome));
		Assert.That(codec.IsEncodedEqual(none, someNone), Is.False);
		var path = new DecodeFailurePath.Constructor("outer");
		Assert.That(Assert.Throws<DecodeException>(() => codec.Decode(new Expr.Null(2), path))!.Path, Is.EqualTo(path));
	}

	[Test]
	public void DictInterfaceOperationsPreserveTheOriginal() {
		IImmutableDictionary<string, int> original = new Dict<int>(ImmutableDictionary<string, int>.Empty.Add("a", 1).Add("b", 2));
		Assert.That(original.AddRange(new Dictionary<string, int> { ["c"] = 3 }), Is.EquivalentTo(new Dictionary<string, int> { ["a"] = 1, ["b"] = 2, ["c"] = 3 }));
		Assert.That(original.SetItem("a", 3)["a"], Is.EqualTo(3));
		Assert.That(original.SetItems(new Dictionary<string, int> { ["a"] = 4, ["c"] = 5 })["c"], Is.EqualTo(5));
		Assert.That(original.Remove("a"), Is.EquivalentTo(new Dictionary<string, int> { ["b"] = 2 }));
		Assert.That(original.RemoveRange(["a", "b"]).Count, Is.Zero);
		Assert.That(original.Clear().Count, Is.Zero);
		Assert.That(original, Is.EquivalentTo(new Dictionary<string, int> { ["a"] = 1, ["b"] = 2 }));
	}

	private static IEnumerable<TestCaseData> ScalarTags() {
		yield return new TestCaseData(new Expr.Float16((Half)1), new ESExprTag.Float16());
		yield return new TestCaseData(new Expr.Float16NaN(0x7E01), new ESExprTag.Float16());
		yield return new TestCaseData(new Expr.Float32(1), new ESExprTag.Float32());
		yield return new TestCaseData(new Expr.Float32NaN(0x7FC00001), new ESExprTag.Float32());
		yield return new TestCaseData(new Expr.Float64(1), new ESExprTag.Float64());
		yield return new TestCaseData(new Expr.Float64NaN(0x7FF8000000000001), new ESExprTag.Float64());
		yield return new TestCaseData(new Expr.Null(0), new ESExprTag.Null());
		yield return new TestCaseData(new Expr.Null(3), new ESExprTag.Null());
	}

	[TestCaseSource(nameof(ScalarTags))]
	public void ScalarExpressionsReportTheirTags(Expr expr, ESExprTag expected) {
		Assert.That(expr.Tag, Is.EqualTo(expected));
	}

	[Test]
	public void StringTableCodecReportsItsTagsAndRejectsKeywordArguments() {
		var codec = StringTable.Codec;
		Assert.That(codec.Tags, Is.EqualTo(ESExprTagSet.Create([new ESExprTag.Constructor("string-table")])));
		var strings = ImmutableList.Create("a", "b");
		Assert.That(codec.IsEncodedEqual(new StringTable(strings), new StringTable(strings)), Is.True);
		Assert.That(codec.IsEncodedEqual(new StringTable(strings), new StringTable(ImmutableList.Create("other"))), Is.False);
		var path = new DecodeFailurePath.Keyword("outer", "table", new DecodeFailurePath.Current());
		var expr = new Expr.Constructor("string-table", [], ImmutableDictionary<string, Expr>.Empty.Add("invalid", new Expr.Str("a")));
		var error = Assert.Throws<DecodeException>(() => codec.Decode(expr, path))!;
		Assert.That(error.Path, Is.EqualTo(new DecodeFailurePath.Keyword("outer", "table", new DecodeFailurePath.Constructor("string-table"))));
	}

	[Test]
	public void EnumCodecRequiresTheCodecAttribute() {
		var error = Assert.Throws<TypeInitializationException>(() => IESExprCodec.EnumCodec<UnannotatedEnum>())!;
		Assert.That(error.InnerException, Is.TypeOf<InvalidOperationException>());
		Assert.That(error.InnerException!.Message, Does.Contain(nameof(ESExprCodecAttribute)));
	}

	[Test]
	public void DictionaryCodecsAdvertiseTheirEncodedConstructor() {
		CheckDictionaryTags(IESExprCodec.DictionaryCodec(IESExprCodec.IntCodec), new Dictionary<string, int> { ["a"] = 1 });
		CheckDictionaryTags(IESExprCodec.ImmutableDictionaryCodec(IESExprCodec.IntCodec), ImmutableDictionary<string, int>.Empty.Add("a", 1));
		CheckDictionaryTags(IESExprCodec.InterfaceDictionaryCodec(IESExprCodec.IntCodec), new Dictionary<string, int> { ["a"] = 1 });
		CheckDictionaryTags(IESExprCodec.ReadOnlyDictionaryCodec(IESExprCodec.IntCodec), new Dictionary<string, int> { ["a"] = 1 });
		CheckDictionaryTags(IESExprCodec.InterfaceImmutableDictionaryCodec(IESExprCodec.IntCodec), ImmutableDictionary<string, int>.Empty.Add("a", 1));
	}

	private static void CheckDictionaryTags<T>(IESExprCodec<T> codec, T value) {
		Assert.That(codec.Tags, Is.EqualTo(ESExprTagSet.Create([new ESExprTag.Constructor("dict")])));
		Assert.That(codec.Tags.Contains(codec.Encode(value).Tag), Is.True);
	}

	[Test]
	public void VarargCodecsAdvertiseTheElementTags() {
		CheckVarargTags(IVarargCodec.ListCodec(IESExprCodec.IntCodec));
		CheckVarargTags(IVarargCodec.ImmutableListCodec(IESExprCodec.IntCodec));
		CheckVarargTags(IVarargCodec.InterfaceListCodec(IESExprCodec.IntCodec));
		CheckVarargTags(IVarargCodec.ReadOnlyListCodec(IESExprCodec.IntCodec));
		CheckVarargTags(IVarargCodec.VListCodec(IESExprCodec.IntCodec));
		Assert.That(IVarargCodec.ListCodec(Expr.Codec).ElementTags, Is.EqualTo(ESExprTagSet.All));
		Assert.That(IVarargCodec.ListCodec(IESExprCodec.ListCodec(IESExprCodec.IntCodec)).ElementTags,
			Is.EqualTo(ESExprTagSet.Create([new ESExprTag.Constructor("list")])));
	}

	private static void CheckVarargTags<T>(IVarargCodec<T, int> codec) {
		Assert.That(codec.ElementTags, Is.EqualTo(IESExprCodec.IntCodec.Tags));
		Assert.That(codec.ElementTags.Contains(new Expr.Int(1).Tag), Is.True);
		Assert.That(codec.ElementTags.Contains(new Expr.Str("stop").Tag), Is.False);
	}

	[Test]
	public void FlagBitsSupportsUnsignedAndArbitraryPrecisionValues() {
		Assert.That(new FlagBitsAttribute(ulong.MaxValue).Bits, Is.EqualTo(new BigInteger(ulong.MaxValue)));
		var large = BigInteger.One << 256;
		Assert.That(new FlagBitsAttribute(large.ToString(CultureInfo.InvariantCulture)).Bits, Is.EqualTo(large));
		Assert.That(new FlagBitsAttribute("0").Bits, Is.EqualTo(BigInteger.Zero));
		Assert.Throws<FormatException>(() => new FlagBitsAttribute("not a number"));
	}
}
