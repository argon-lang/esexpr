using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class RuntimeBinaryEdgeTests {
	[TestCase("map", 0xF1)]
	[TestCase("set", 0xF2)]
	[TestCase("string-table", 0xE6)]
	[TestCase("list", 0xE7)]
	public async Task SpecialConstructorsUseDedicatedTokens(string name, int token) {
		using var stream = new MemoryStream();
		var expr = new Expr.Constructor(name, [], ImmutableDictionary<string, Expr>.Empty);
		await new ESExprBinaryWriter(ImmutableList.Create(name), stream).Write(expr);
		Assert.That(stream.ToArray(), Is.EqualTo(new byte[] { (byte)token, 0xE0 }));
		stream.Position = 0;
		Assert.That(await new ESExprBinaryReader(stream).Read(), Is.EqualTo(expr));
	}

	[Test]
	public async Task InitialSymbolTableIsSharedAndExtendedAcrossExpressions() {
		var symbols = ImmutableList.Create("known", "key");
		using var stream = new MemoryStream();
		var writer = new ESExprBinaryWriter(symbols, stream);
		var first = new Expr.Constructor("known", [], ImmutableDictionary<string, Expr>.Empty.Add("key", new Expr.Int(1)));
		var second = new Expr.Constructor("new", [], ImmutableDictionary<string, Expr>.Empty);
		await writer.Write(first); await writer.Write(second); await writer.Write(first);
		Assert.That(stream.ToArray()[0], Is.EqualTo(0));
		stream.Position = 0;
		var reader = new ESExprBinaryReader(symbols, stream);
		Assert.That(await reader.Read(), Is.EqualTo(first));
		Assert.That(await reader.Read(), Is.EqualTo(second));
		Assert.That(await reader.Read(), Is.EqualTo(first));
		Assert.That(await reader.TryRead(), Is.Null);
		Assert.ThrowsAsync<EndOfStreamException>(async () => await reader.Read());
	}

	[Test]
	public async Task StringPoolReferencesResolveAgainstTheInitialTable() {
		using var stream = new MemoryStream(new byte[] { 0x80, 0x81 });
		var reader = new ESExprBinaryReader(ImmutableList.Create("first", "second"), stream);
		Assert.That(await reader.Read(), Is.EqualTo(new Expr.Str("first")));
		Assert.That(await reader.Read(), Is.EqualTo(new Expr.Str("second")));
		Assert.That(await reader.TryRead(), Is.Null);
	}

	private static IEnumerable<TestCaseData> InvalidStreams() {
		yield return new TestCaseData(new byte[] { 0x80 }).SetName("UnknownStringPoolIndex");
		yield return new TestCaseData(new byte[] { 0x00 }).SetName("UnknownConstructorIndex");
		yield return new TestCaseData(new byte[] { 0xFF }).SetName("IllegalToken");
		yield return new TestCaseData(new byte[] { 0x30 }).SetName("TruncatedInteger");
		yield return new TestCaseData(new byte[] { 0xE0 }).SetName("UnexpectedConstructorEnd");
		yield return new TestCaseData(new byte[] { 0xC0 }).SetName("UnexpectedKeyword");
		yield return new TestCaseData(new byte[] { 0xEB, 0xE1 }).SetName("InvalidAppendedStringTable");
		yield return new TestCaseData(new byte[] { 0x7F, 0xFF, 0xFF, 0xFF, 0x3F }).SetName("OversizedStringLength");
	}

	[TestCaseSource(nameof(InvalidStreams))]
	public void InvalidBinaryInputThrowsSyntaxException(byte[] bytes) {
		using var stream = new MemoryStream(bytes);
		Assert.ThrowsAsync<SyntaxException>(async () => await new ESExprBinaryReader(stream).Read());
	}

	[Test]
	public void TruncatedConstructorAndPayloadThrowEndOfStream() {
		foreach(var bytes in new[] { new byte[] { 0xE7 }, new byte[] { 0xE4, 0x00 }, new byte[] { 0x62, 0x61 } }) {
			using var stream = new MemoryStream(bytes);
			Assert.ThrowsAsync<EndOfStreamException>(async () => await new ESExprBinaryReader(stream).Read());
		}
	}

	[Test]
	public void CancelledReadStopsBeforeConsumingInput() {
		using var stream = new MemoryStream(new byte[] { 0xE1 });
		using var cancellation = new CancellationTokenSource(); cancellation.Cancel();
		Assert.ThrowsAsync<OperationCanceledException>(async () => await new ESExprBinaryReader(stream).Read(cancellation.Token));
		Assert.That(stream.Position, Is.Zero);
	}
}
