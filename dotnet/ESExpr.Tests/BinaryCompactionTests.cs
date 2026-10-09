using System.Collections.Immutable;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class BinaryCompactionTests {
	private static Expr Constructor(string name) => new Expr.Constructor(name, [], ImmutableDictionary<string, Expr>.Empty);

	[Test]
	public void RecordsNestedMixedRolesAndMandatorySingleUses() {
		var builder = new StringTableBuilder();
		Assert.That(builder.Build(), Is.Empty);
		builder.Record(new Expr.Constructor("outer", [new Expr.Constructor("inner", [new Expr.Str("shared"), new Expr.Str("once")], ImmutableDictionary<string, Expr>.Empty)], ImmutableDictionary<string, Expr>.Empty.Add("shared", new Expr.Str("shared"))));
		Assert.That(builder.Build(), Is.EqualTo(new[] { "shared", "inner", "outer" }));
		builder.Build().Clear();
		Assert.That(builder.Build(), Is.EqualTo(new[] { "shared", "inner", "outer" }));
	}

	[Test]
	public void DedicatedNamesOnlyCountAsKeywordsAndLiterals() {
		var builder = new StringTableBuilder();
		foreach(var name in new[] { "list", "map", "set", "string-table" }) builder.Record(Constructor(name));
		Assert.That(builder.Build(), Is.Empty);
		builder.Record(new Expr.Constructor("list", [new Expr.Str("map"), new Expr.Str("map")], ImmutableDictionary<string, Expr>.Empty.Add("set", new Expr.Bool(true))));
		Assert.That(builder.Build(), Is.EqualTo(new[] { "map", "set" }));
	}

	[Test]
	public void LiteralSelectionUsesUtf8SavingsAndExcludesSingleUsesAndBreakEven() {
		var builder = new StringTableBuilder();
		foreach(var value in new[] { "", "x", "ab", "é", "a long single-use string", "", "x", "ab", "é", "x" }) builder.Record(new Expr.Str(value));
		Assert.That(builder.Build(), Is.EqualTo(new[] { "x", "ab", "é" }));
		var breakEven = new StringTableBuilder();
		breakEven.Record(new Expr.Str("x"));
		breakEven.Record(new Expr.Str("x"));
		Assert.That(breakEven.Build(), Is.Empty);
	}

	[Test]
	public void TiesUseUnicodeScalarOrderRegardlessOfRecordingOrder() {
		var first = new StringTableBuilder();
		var second = new StringTableBuilder();
		string[] names = ["a", "b", "c", "\uE000", "\U00010000"];
		foreach(var name in names) first.Record(Constructor(name));
		foreach(var name in names.Reverse()) second.Record(Constructor(name));
		Assert.That(first.Build(), Is.EqualTo(names));
		Assert.That(second.Build(), Is.EqualTo(names));
	}

	[TestCase(15, "zz", true)]
	[TestCase(16, "zz", false)]
	[TestCase(2047, "zzz", true)]
	[TestCase(2048, "zzz", false)]
	public void SelectionAccountsForIndexWidthsAndRemovedCandidates(int prefix, string value, bool included) {
		var builder = new StringTableBuilder();
		for(int i = 0; i < 8; ++i) builder.Record(new Expr.Str(""));
		for(int i = 0; i < prefix; ++i) {
			for(int j = 0; j < 5; ++j) builder.Record(Constructor($"a{i}"));
		}
		for(int i = 0; i < 3; ++i) builder.Record(new Expr.Str(value));
		Assert.That(builder.Build().Contains(value), Is.EqualTo(included));
	}

	[Test]
	public async Task CompactedStreamDecodesWithFreshReaderAndSavesBytes() {
		var expr = new Expr.Constructor("custom", [new Expr.Str("repeated long value"), new Expr.Str("repeated long value")], ImmutableDictionary<string, Expr>.Empty.Add("key", new Expr.Str("once")));
		var builder = new StringTableBuilder();
		builder.Record(expr);
		using var stream = new MemoryStream();
		var writer = new ESExprBinaryWriter(stream);
		await writer.AddStrings(builder.Build());
		await writer.Write(expr);
		stream.Position = 0;
		Assert.That(await new ESExprBinaryReader(stream).Read(), Is.EqualTo(expr));
		using var ordinary = new MemoryStream();
		await new ESExprBinaryWriter(ordinary).Write(expr);
		Assert.That(stream.Length, Is.LessThan(ordinary.Length));
	}

	[Test]
	public async Task AdditionsSkipDuplicatesAndPreserveIndices() {
		using var stream = new MemoryStream();
		var writer = new ESExprBinaryWriter(stream);
		await writer.AddStrings([]);
		Assert.That(stream.Length, Is.Zero);
		await writer.AddStrings(["first", "first"]);
		await writer.Write(new Expr.Str("first"));
		await writer.AddStrings(["first", "second", "third", "second"]);
		await writer.AddStrings(["third"]);
		foreach(var value in new[] { "first", "second", "third" }) await writer.Write(new Expr.Str(value));
		Assert.That(stream.ToArray(), Is.EqualTo(new byte[] {
			0xEB, 0x65, 102, 105, 114, 115, 116, 0x80,
			0xEB, 0xE6, 0x66, 115, 101, 99, 111, 110, 100, 0x65, 116, 104, 105, 114, 100, 0xE0,
			0x80, 0x81, 0x82,
		}));
		stream.Position = 0;
		Assert.That(await new ESExprBinaryReader(stream).ReadAll().ToArrayAsync(), Is.EqualTo(new Expr[] { new Expr.Str("first"), new Expr.Str("first"), new Expr.Str("second"), new Expr.Str("third") }));
	}

	[Test]
	public async Task AdditionsPreserveAutomaticAndPreseededEntries() {
		using var stream = new MemoryStream();
		var writer = new ESExprBinaryWriter(ImmutableList.Create("known"), stream);
		await writer.Write(Constructor("first"));
		await writer.AddStrings(["known", "first", "second", "second"]);
		foreach(var value in new[] { "known", "first", "second" }) await writer.Write(new Expr.Str(value));
		stream.Position = 0;
		Assert.That(await new ESExprBinaryReader(ImmutableList.Create("known"), stream).ReadAll().ToArrayAsync(), Is.EqualTo(new Expr[] { Constructor("first"), new Expr.Str("known"), new Expr.Str("first"), new Expr.Str("second") }));
	}

	[Test]
	public async Task DedicatedConstructorsNeedNoAppends() {
		using var stream = new MemoryStream();
		var writer = new ESExprBinaryWriter(stream);
		foreach(var name in new[] { "list", "map", "set", "string-table" }) await writer.Write(Constructor(name));
		Assert.That(stream.ToArray(), Is.EqualTo(new byte[] { 0xE7, 0xE0, 0xF1, 0xE0, 0xF2, 0xE0, 0xE6, 0xE0 }));
	}

	[TestCase(15, "zz", new byte[] { 0x8F })]
	[TestCase(16, "zz", new byte[] { 0x90, 1 })]
	[TestCase(2047, "zz", new byte[] { 0x9F, 0x7F })]
	[TestCase(2048, "zz", new byte[] { 0x90, 0x80, 1 })]
	[TestCase(2048, "x", new byte[] { 0x61, 120 })]
	public async Task ReferencesUseActualIndexWidthAndInlineFallback(int index, string value, byte[] expected) {
		var pool = Enumerable.Range(0, index).Select(i => $"entry{i}").Append(value).ToImmutableList();
		using var stream = new MemoryStream();
		await new ESExprBinaryWriter(pool, stream).Write(new Expr.Str(value));
		Assert.That(stream.ToArray(), Is.EqualTo(expected));
	}

	[TestCase(15)]
	[TestCase(16)]
	[TestCase(2047)]
	[TestCase(2048)]
	public async Task StringLengthBoundariesRoundTrip(int length) {
		var expr = new Expr.Str(new string('z', length));
		var builder = new StringTableBuilder();
		builder.Record(expr);
		builder.Record(expr);
		Assert.That(builder.Build(), Is.EqualTo(new[] { expr.value }));
		using var stream = new MemoryStream();
		var writer = new ESExprBinaryWriter(stream);
		await writer.AddStrings(builder.Build());
		await writer.Write(expr);
		stream.Position = 0;
		Assert.That(await new ESExprBinaryReader(stream).Read(), Is.EqualTo(expr));
	}

	[TestCase(false)]
	[TestCase(true)]
	public async Task AdditionFailuresPropagateAtEveryByte(bool cancel) {
		foreach(var strings in new string[][] { [], ["single"], ["first", "second", "first"] }) {
			using var baseline = new MemoryStream();
			await new ESExprBinaryWriter(baseline).AddStrings(strings);
			for(int offset = 0; offset <= baseline.Length; ++offset) {
				using var stream = new FaultingStream(offset, cancel);
				var writer = new ESExprBinaryWriter(stream);
				if(offset == baseline.Length) await writer.AddStrings(strings);
				else if(cancel) Assert.ThrowsAsync<OperationCanceledException>(async () => await writer.AddStrings(strings));
				else Assert.ThrowsAsync<IOException>(async () => await writer.AddStrings(strings));
				Assert.That(stream.ToArray(), Is.EqualTo(baseline.ToArray()[..offset]));
			}
		}
	}

	[Test]
	public void PreCancelledAdditionWritesNothing() {
		using var stream = new MemoryStream();
		var writer = new ESExprBinaryWriter(stream);
		using var cancellation = new CancellationTokenSource();
		cancellation.Cancel();
		Assert.ThrowsAsync<OperationCanceledException>(async () => await writer.AddStrings(["value"], cancellation.Token));
		Assert.That(stream.Length, Is.Zero);
	}

	private sealed class FaultingStream(int limit, bool cancel) : MemoryStream {
		public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default) {
			cancellationToken.ThrowIfCancellationRequested();
			int allowed = Math.Min(buffer.Length, limit - (int)Length);
			Write(buffer.Span[..allowed]);
			if(allowed < buffer.Length) {
				if(cancel) throw new OperationCanceledException();
				throw new IOException("injected write failure");
			}
			return ValueTask.CompletedTask;
		}
	}
}
