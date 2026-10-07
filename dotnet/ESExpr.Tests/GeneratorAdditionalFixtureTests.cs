using System.Collections.Immutable;
using System.Numerics;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class GeneratorAdditionalFixtureTests : TestBase {
	private static IEnumerable<TestCaseData> ScalarCases() {
		yield return new TestCaseData(new GeneratorScalarUnion.Boolean { Value = true }, new Expr.Bool(true));
		yield return new TestCaseData(new GeneratorScalarUnion.Integer { Value = 7 }, new Expr.Int(7));
		yield return new TestCaseData(new GeneratorScalarUnion.Text { Value = "text" }, new Expr.Str("text"));
		yield return new TestCaseData(new GeneratorScalarUnion.HalfValue { Value = (Half)1.5 }, new Expr.Float16((Half)1.5));
		yield return new TestCaseData(new GeneratorScalarUnion.Single { Value = 1.5f }, new Expr.Float32(1.5f));
		yield return new TestCaseData(new GeneratorScalarUnion.DoubleValue { Value = 1.5 }, new Expr.Float64(1.5));
		yield return new TestCaseData(new GeneratorScalarUnion.Bytes { Value = [1, 2] }, new Expr.Array8([1, 2]));
		yield return new TestCaseData(new GeneratorScalarUnion.Words { Value = [1, 2] }, new Expr.Array16([1, 2]));
		yield return new TestCaseData(new GeneratorScalarUnion.DWords { Value = [1, 2] }, new Expr.Array32([1, 2]));
		yield return new TestCaseData(new GeneratorScalarUnion.QWords { Value = [1, 2] }, new Expr.Array64([1, 2]));
		yield return new TestCaseData(new GeneratorScalarUnion.WideWords { Value = [1, 2] }, new Expr.Array128([1, 2]));
	}

	[TestCaseSource(nameof(ScalarCases))]
	public void InlineScalarCasesRoundTrip(GeneratorScalarUnion value, Expr expr) {
		AssertCodecMatch(GeneratorScalarUnion.Codec, expr, value);
		Assert.That(GeneratorScalarUnion.Codec.Tags.Contains(expr.Tag), Is.True);
	}

	[Test]
	public void GlobalMethodAccessorUsesExplicitConstructorAndKeywordDefaults() {
		var codec = GeneratorGlobalRecord.CreateCodec();
		Assert.That(codec.Decode(new Expr.Constructor("global-fixture", [], ImmutableDictionary<string, Expr>.Empty)).Value, Is.EqualTo(42));
		AssertCodecMatch(codec, new Expr.Constructor("global-fixture", [], ImmutableDictionary<string, Expr>.Empty), new GeneratorGlobalRecord { Value = 42 });
		AssertCodecMatch(codec, new Expr.Constructor("global-fixture", [], ImmutableDictionary<string, Expr>.Empty.Add("answer", new Expr.Int(7))), new GeneratorGlobalRecord { Value = 7 });
	}

	[Test]
	public void NullableReferenceFieldPreservesItsValue() {
		AssertCodecMatch(GeneratorNullableField.Codec,
			new Expr.Constructor("generator-nullable-field", [new Expr.Str("value")], ImmutableDictionary<string, Expr>.Empty),
			new GeneratorNullableField { Value = "value" });
	}

	[Test]
	public void NestedNamespaceFixtureRoundTrips() {
		AssertCodecMatch(GeneratorFixtures.Nested.NestedRecord.Codec,
			new Expr.Constructor("nested-record", [new Expr.Str("value")], ImmutableDictionary<string, Expr>.Empty),
			new GeneratorFixtures.Nested.NestedRecord { Value = "value" });
	}

	[Test]
	public void UniversalInlineUnionAcceptsScalarsAndConstructors() {
		Assert.That(GeneratorAnyUnion.Codec.Tags, Is.EqualTo(ESExprTagSet.All));
		foreach(var expr in new Expr[] { new Expr.Null(2), new Expr.Int(7), new Expr.Constructor("arbitrary", [], ImmutableDictionary<string, Expr>.Empty) })
			AssertCodecMatch(GeneratorAnyUnion.Codec, expr, new GeneratorAnyUnion.ValueCase { Value = expr });
	}

	[Test]
	public void NullableInlineUnionAdvertisesNullAndIntegerTags() {
		Assert.That(GeneratorNullableUnion.Codec.Tags, Is.EqualTo(ESExprTagSet.Create([new ESExprTag.Null(), new ESExprTag.Int()])));
		AssertCodecMatch(GeneratorNullableUnion.Codec, new Expr.Null(0), new GeneratorNullableUnion.ValueCase { Value = Option<int>.Empty });
		AssertCodecMatch(GeneratorNullableUnion.Codec, new Expr.Int(7), new GeneratorNullableUnion.ValueCase { Value = new Option<int>(7) });
	}

	[Test]
	public void DictFollowingNamedKeywordsConsumesTheRemainingKeywords() {
		AssertCodecMatch(GeneratorKeywordThenDict.Codec,
			new Expr.Constructor("generator-keyword-then-dict", [], ImmutableDictionary<string, Expr>.Empty.Add("first", new Expr.Int(1)).Add("second", new Expr.Int(2))),
			new GeneratorKeywordThenDict { First = 1, Rest = ImmutableDictionary<string, int>.Empty.Add("second", 2) });
	}

	[Test]
	public void FlagMasksLargerThanUInt64RoundTrip() {
		AssertCodecMatch(GeneratorWideFlags.Codec, new Expr.Int(BigInteger.One << 64), new GeneratorWideFlags { High = true, Mode = GeneratorWideFlags.Choice.None });
		AssertCodecMatch(GeneratorWideFlags.Codec, new Expr.Int(BigInteger.One << 65), new GeneratorWideFlags { High = false, Mode = GeneratorWideFlags.Choice.High });
		AssertCodecMatch(GeneratorWideFlags.Codec, new Expr.Int((BigInteger.One << 64) | (BigInteger.One << 65)), new GeneratorWideFlags { High = true, Mode = GeneratorWideFlags.Choice.High });
		Assert.Throws<DecodeException>(() => GeneratorWideFlags.Codec.Decode(new Expr.Bool(false)));
	}
}
