using System.Collections.Immutable;
using CsCheck;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class BinaryRoundTripPropertyTests {
	private static IEnumerable<TestCaseData> PrimitiveCases() {
		foreach(var (name, generator) in ExprGenerators.Primitives) {
			yield return new TestCaseData(generator).SetName($"BinaryRoundTrip_{name}");
		}
	}

	[TestCaseSource(nameof(PrimitiveCases))]
	public Task PrimitiveRoundTrips(Gen<Expr> generator) => generator.SampleAsync(
		async expr => await RoundTrip([expr], [], 1), iter: 200);

	[TestCase(false, false)]
	[TestCase(false, true)]
	[TestCase(true, false)]
	[TestCase(true, true)]
	public Task RecursiveStreamsRoundTrip(bool preseeded, bool singleByteReads) {
		var symbols = preseeded ? ImmutableList.Create("custom", "key", "", "list", "map", "set", "string-table") : [];
		return Gen.Select(ExprGenerators.Expression.Array[0, 16], Gen.Int[1, 128]).SampleAsync(
			async (exprs, maxRead) => await RoundTrip(exprs, symbols, singleByteReads ? 1 : maxRead), iter: 500);
	}

	[Test]
	public Task RepeatedConstructorsShareSymbolTables() => Gen.Select(
		ExprGenerators.Name, ExprGenerators.Name, ExprGenerators.Expression, ExprGenerators.Expression
	).SampleAsync(async (name, key, first, second) => {
		var a = new Expr.Constructor(name, [first], ImmutableDictionary<string, Expr>.Empty.Add(key, second));
		var b = new Expr.Constructor(name, [second], ImmutableDictionary<string, Expr>.Empty.Add(key, first));
		await RoundTrip([a, b, a], [], 1);
		await RoundTrip([a, b, a], ImmutableList.Create(name, key).Distinct().ToImmutableList(), 7);
	}, iter: 200);

	private static async Task RoundTrip(Expr[] expected, ImmutableList<string> symbols, int maxRead) {
		using var encoded = new MemoryStream();
		var writer = new ESExprBinaryWriter(symbols, encoded);
		foreach(var expr in expected) await writer.Write(expr);
		using var input = new ShortReadStream(encoded.ToArray(), maxRead);
		var reader = new ESExprBinaryReader(symbols, input);
		var actual = await reader.ReadAll().ToArrayAsync();
		Assert.That(actual, Is.EqualTo(expected));
		for(int i = 0; i < expected.Length; ++i) ExprAssertions.AssertFloatBits(expected[i], actual[i]);
		Assert.That(await reader.TryRead(), Is.Null);
	}

	// A non-seekable transport may return fewer bytes than requested, even when
	// more data remains. Exercise ReadExactlyAsync across those boundaries.
	private sealed class ShortReadStream(byte[] bytes, int maxRead) : Stream {
		private readonly MemoryStream input = new(bytes, writable: false);
		public override bool CanRead => true;
		public override bool CanSeek => false;
		public override bool CanWrite => false;
		public override long Length => throw new NotSupportedException();
		public override long Position {
			get => throw new NotSupportedException();
			set => throw new NotSupportedException();
		}
		public override int Read(byte[] buffer, int offset, int count) => input.Read(buffer, offset, Math.Min(count, maxRead));
		public override Task<int> ReadAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken) =>
			input.ReadAsync(buffer, offset, Math.Min(count, maxRead), cancellationToken);
		public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default) =>
			input.ReadAsync(buffer[..Math.Min(buffer.Length, maxRead)], cancellationToken);
		public override void Flush() => throw new NotSupportedException();
		public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
		public override void SetLength(long value) => throw new NotSupportedException();
		public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
		protected override void Dispose(bool disposing) {
			if(disposing) input.Dispose();
			base.Dispose(disposing);
		}
	}
}
