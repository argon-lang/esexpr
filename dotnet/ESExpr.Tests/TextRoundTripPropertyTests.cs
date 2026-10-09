using CsCheck;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class TextRoundTripPropertyTests {
	private static IEnumerable<TestCaseData> PrimitiveCases() {
		foreach(var (name, generator) in ExprGenerators.Primitives) yield return new TestCaseData(generator).SetName($"TextRoundTrip_{name}");
	}

	[TestCaseSource(nameof(PrimitiveCases))]
	public Task PrimitiveRoundTrips(Gen<Expr> generator) => generator.SampleAsync(
		async expr => await RoundTrip([expr], null, 1), iter: 200);

	[TestCase(null)]
	[TestCase("")]
	[TestCase("  ")]
	[TestCase("\t")]
	public Task RecursiveStreamsRoundTrip(string? indentation) => Gen.Select(
		ExprGenerators.Expression.Array[0, 16], Gen.Int[1, 128]
	).SampleAsync(async (exprs, maxRead) => await RoundTrip(exprs, indentation, maxRead), iter: 500);

	private static async Task RoundTrip(Expr[] expected, string? indentation, int maxRead) {
		string source = await TextFormatTests.Encode(expected, indentation);
		var actual = await TextFormatTests.Decode(source, maxRead);
		Assert.That(actual, Is.EqualTo(expected));
		for(int i = 0; i < expected.Length; ++i) ExprAssertions.AssertFloatBits(expected[i], actual[i]);
	}

	[Test]
	public async Task EveryFloat16BitPatternRoundTrips() {
		var expected = Enumerable.Range(0, 65536).Select(bits => ExprGenerators.Float16((ushort)bits)).ToArray();
		await RoundTrip(expected, null, 256);
	}
}
