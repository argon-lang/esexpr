using System.Collections.Immutable;
using System.Globalization;
using System.Numerics;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class TextFormatTests {
	internal static async Task<Expr[]> Decode(string source, int maxRead = 1) {
		using var input = new ShortReadTextReader(source, maxRead);
		return await new ESExprTextReader(input).ReadAll().ToArrayAsync();
	}

	internal static async Task<string> Encode(IEnumerable<Expr> exprs, string? indentation = null) {
		using var output = new StringWriter(CultureInfo.InvariantCulture);
		await new ESExprTextWriter(output, indentation).WriteAll(exprs);
		return output.ToString();
	}

	private static async Task<string> Format(Expr expr, string? indentation = null) {
		using var output = new StringWriter(CultureInfo.InvariantCulture);
		await new ESExprTextWriter(output, indentation).Write(expr);
		return output.ToString();
	}

	private static IEnumerable<string> TextFixtures() => Directory.EnumerateFiles("../../../../../tests/", "*.esx");

	[TestCaseSource(nameof(TextFixtures))]
	public async Task SharedRustTextFixturesMatchBinaryFixtures(string path) {
		var source = await File.ReadAllTextAsync(path);
		using var binary = File.OpenRead(Path.ChangeExtension(path, ".esxb"));
		var expected = await new ESExprBinaryReader(binary).ReadAll().ToArrayAsync();
		var actual = await Decode(source);
		Assert.That(actual, Is.EqualTo(expected));
		for(int i = 0; i < expected.Length; ++i) ExprAssertions.AssertFloatBits(expected[i], actual[i]);
		Assert.That(await Decode(await Encode(actual)), Is.EqualTo(expected));
	}

	[TestCase("(example 42 z: #true a: (nested))", "(example 42 a: (nested) z: #true)")]
	[TestCase("('' '': \"\")", "('' '': \"\")")]
	[TestCase("#u8[0 255 16]", "#\"00ff10\"")]
	[TestCase("#null0", "#null")]
	[TestCase("-0.0f16", "-0.0f16")]
	[TestCase("-0.0f", "-0.0f")]
	[TestCase("-0.0d", "-0.0d")]
	[TestCase("1.0f", "1.0f")]
	[TestCase("1.0", "1.0d")]
	[TestCase("#float64:-nan:1", "#float64:-nan:1")]
	[TestCase("\"\\\"\\\\\\n\\r\\t\\f\\u{0}λ\"", "\"\\\"\\\\\\n\\r\\t\\f\\u{0}λ\"")]
	public async Task CanonicalOutput(string input, string expected) {
		Assert.That(await Format((await Decode(input)).Single()), Is.EqualTo(expected));
	}

	[TestCase("  ", "(outer\n  (inner\n    1\n  )\n  a: (empty)\n  z: #true\n)")]
	[TestCase("", "(outer\n(inner\n1\n)\na: (empty)\nz: #true\n)")]
	[TestCase("\t", "(outer\n\t(inner\n\t\t1\n\t)\n\ta: (empty)\n\tz: #true\n)")]
	public async Task IndentationMatchesRust(string indentation, string expected) {
		var expr = (await Decode("(outer (inner 1) z: #true a: (empty))")).Single();
		Assert.That(await Format(expr, indentation), Is.EqualTo(expected));
		Assert.That(await Format((await Decode("(empty)")).Single(), indentation), Is.EqualTo("(empty)"));
		Assert.That(await Format(new Expr.Array16([1, 2]), indentation), Is.EqualTo("#u16[1 2]"));
	}

	[Test]
	public async Task IntegerRadicesAndLargeValues() {
		Assert.That(await Decode("0XFF -0b11 +0o377 -00012"), Is.EqualTo(new Expr[] { new Expr.Int(255), new Expr.Int(-3), new Expr.Int(255), new Expr.Int(-12) }));
		Assert.That(await Decode("0x100000000000000000000000000000000"), Is.EqualTo(new Expr[] { new Expr.Int(BigInteger.One << 128) }));
		Assert.That(await Decode("#u128[340282366920938463463374607431768211455]"), Is.EqualTo(new Expr[] { new Expr.Array128([UInt128.MaxValue]) }));
	}

	[Test]
	public async Task FloatSuffixesSignsAndExponents() {
		foreach(var (decimalValue, hex, value) in new[] { ("1.5", "0x1.8p0", 1.5), ("-1.5e+1", "-0x1.ep3", -15.0), ("+1.25E-1", "+0x1.0P-3", 0.125) }) {
			foreach(string suffix in new[] { "", "d", "D", "f", "F", "f16", "F16" }) {
				Expr expected = suffix.ToLowerInvariant() switch { "f16" => new Expr.Float16((Half)value), "f" => new Expr.Float32((float)value), _ => new Expr.Float64(value) };
				Assert.That(await Decode(decimalValue + suffix), Is.EqualTo(new[] { expected }));
				Assert.That(await Decode(hex + suffix), Is.EqualTo(new[] { expected }));
			}
		}
	}

	[Test]
	public async Task HexFloatsPreserveSubnormalsAndRoundTiesToEven() {
		var actual = await Decode("0x0.1p-1070 0x1.fffffffffffffp1023 0x1.0p999999999999999999999 -0x1.0p-999999999999999999999 0x0.0p999999999999999999999");
		var expected = new Expr[] { new Expr.Float64(double.Epsilon), new Expr.Float64(double.MaxValue), new Expr.Float64(double.PositiveInfinity), new Expr.Float64(-0.0), new Expr.Float64(0) };
		Assert.That(actual, Is.EqualTo(expected));
		for(int i = 0; i < expected.Length; ++i) ExprAssertions.AssertFloatBits(expected[i], actual[i]);
		Assert.That(await Decode("0x1.000001p0f 0x1.000003p0f 0x1.0p-150f 0x1.8p-149f"), Is.EqualTo(new Expr[] {
			new Expr.Float32(1), new Expr.Float32(1 + MathF.Pow(2, -22)), new Expr.Float32(0), new Expr.Float32(MathF.Pow(2, -148)),
		}));
	}

	[Test]
	public async Task Float16ParsesThroughFloat32LikeRust() {
		Assert.That(await Decode("1.0004882812500000000001f16 0x1.0020000000000001p0f16"), Is.EqualTo(new Expr[] { new Expr.Float16((Half)1), new Expr.Float16((Half)1) }));
		Assert.That(await Decode("1.00000005960464477539062500000000001f"), Is.EqualTo(new Expr[] { new Expr.Float32(1 + MathF.Pow(2, -23)) }));
	}

	[Test]
	public async Task NaNPayloadsPreserveSignsAndSignalingBits() {
		foreach(var (width, sign, exponent, mask) in new[] { (16, 0x8000UL, 0x7C00UL, 0x3FFUL), (32, 0x80000000UL, 0x7F800000UL, 0x7FFFFFUL), (64, 0x8000000000000000UL, 0x7FF0000000000000UL, 0xFFFFFFFFFFFFFUL) }) {
			foreach(bool negative in new[] { false, true }) {
				foreach(ulong payload in new[] { 1UL, mask, (mask + 1) >> 1 }) {
					ulong bits = exponent | payload | (negative ? sign : 0);
					Expr expected = width switch { 16 => new Expr.Float16NaN((ushort)bits), 32 => new Expr.Float32NaN((uint)bits), _ => new Expr.Float64NaN(bits) };
					Assert.That(await Decode($"#float{width}:{(negative ? "-" : "+")}nan:{payload}"), Is.EqualTo(new[] { expected }));
					Assert.That(await Decode(await Format(expected)), Is.EqualTo(new[] { expected }));
				}
			}
			Assert.That(await Format((await Decode($"#float{width}:nan")).Single()), Is.EqualTo($"#float{width}:+nan:{(mask + 1) >> 1}"));
			Assert.That(await Format((await Decode($"#float{width}:-nan:0")).Single()), Is.EqualTo($"#float{width}:-inf"));
			Assert.ThrowsAsync<SyntaxException>(async () => await Decode($"#float{width}:nan:{mask + 1}"));
		}
	}

	[Test]
	public async Task CommentsDuplicateKeywordsAndUnicode() {
		Assert.That(await Decode("// first\n(box key: 1 // middle\n #true key: 2) // last\n"), Is.EqualTo(new Expr[] {
			new Expr.Constructor("box", [new Expr.Bool(true)], ImmutableDictionary<string, Expr>.Empty.Add("key", new Expr.Int(2))),
		}));
		var expr = new Expr.Constructor("box", [], ImmutableDictionary<string, Expr>.Empty.Add("𐀀", new Expr.Int(1)).Add("\uE000", new Expr.Int(2)));
		Assert.That(await Format(expr), Is.EqualTo("(box '\uE000': 2 '𐀀': 1)"));
		Assert.That(await Decode("\"😊\\u{1f60a}\\'\""), Is.EqualTo(new Expr[] { new Expr.Str("😊😊'") }));
		Assert.That(await Decode("1#true#false\"x\"(list#null)"), Is.EqualTo(new Expr[] { new Expr.Int(1), new Expr.Bool(true), new Expr.Bool(false), new Expr.Str("x"), new Expr.Constructor("list", [new Expr.Null(0)], ImmutableDictionary<string, Expr>.Empty) }));
	}

	private static IEnumerable<string> InvalidInputs() => [
		"(list", "\"unterminated", "('unterminated", "\"\\u{110000}\"", "\"\\u{d800}\"", "\"\\u{}\"", "\"\\u{xyz}\"", "\"\\q\"",
		"1.0 trailing", "0x1.0", "1.", ".5", "1e3", "#truex", "#null1x", "#float32:inf", "#float32:nan:4294967296",
		"(Upper)", "(trailing-)", "(list key)", "(list key:)", ")", "]", "#u8[-1]", "#u8[256]", "#u16[65536]",
		"#u32[4294967296]", "#u64[18446744073709551616]", "#u128[340282366920938463463374607431768211456]",
		"#u8[1.0]", "#u16[1", "#\"0\"", "#\"gg\"", "#\"\\u{30}0\"", "// no newline", "\"\uD800\"", "\"\\u{1000000000000}\"",
	];

	[TestCaseSource(nameof(InvalidInputs))]
	public void MalformedTextThrowsSyntaxException(string input) => Assert.ThrowsAsync<SyntaxException>(async () => await Decode(input));

	[Test]
	public async Task EmptyInputAndEof() {
		using var input = new StringReader(" \t\r\n");
		var reader = new ESExprTextReader(input);
		Assert.That(await reader.ReadAll().ToArrayAsync(), Is.Empty);
		Assert.That(await reader.TryRead(), Is.Null);
		Assert.ThrowsAsync<EndOfStreamException>(async () => await reader.Read());
	}

	[Test]
	public async Task ReaderYieldsWithoutRequiringTheRestOfTheInput() {
		using var input = new GatedTextReader();
		var reader = new ESExprTextReader(input);
		Assert.That(await reader.Read(), Is.EqualTo(new Expr.Constructor("first", [], ImmutableDictionary<string, Expr>.Empty)));
		input.Continue = true;
		Assert.That(await reader.ReadAll().ToArrayAsync(), Is.EqualTo(new Expr[] { new Expr.Constructor("second", [], ImmutableDictionary<string, Expr>.Empty) }));
	}

	[Test]
	public async Task CancellationStopsBufferedReadsAndWrites() {
		using var cancellation = new CancellationTokenSource();
		using var input = new StringReader("#true #false");
		var reader = new ESExprTextReader(input);
		Assert.That(await reader.Read(), Is.EqualTo(new Expr.Bool(true)));
		cancellation.Cancel();
		Assert.ThrowsAsync<OperationCanceledException>(async () => await reader.Read(cancellation.Token));
		Assert.That(await reader.Read(), Is.EqualTo(new Expr.Bool(false)));
		using var output = new StringWriter();
		var writer = new ESExprTextWriter(output);
		Assert.ThrowsAsync<OperationCanceledException>(async () => await writer.Write(new Expr.Int(1), cancellation.Token));
		Assert.That(output.ToString(), Is.Empty);
	}

	[Test]
	public async Task CultureDoesNotAffectNumbersOrKeywordOrder() {
		var original = CultureInfo.CurrentCulture;
		try {
			CultureInfo.CurrentCulture = CultureInfo.GetCultureInfo("fr-FR");
			var expr = new Expr.Constructor("example", [new Expr.Float64(1.5)], ImmutableDictionary<string, Expr>.Empty.Add("z", new Expr.Int(2)).Add("a", new Expr.Int(1)));
			Assert.That(await Format(expr), Is.EqualTo("(example 1.5d a: 1 z: 2)"));
			Assert.That(await Decode("1.5d"), Is.EqualTo(new Expr[] { new Expr.Float64(1.5) }));
		}
		finally { CultureInfo.CurrentCulture = original; }
	}

	[Test]
	public async Task AsyncExpressionInputAndCallerOwnedReadersAndWriters() {
		using var output = new StringWriter();
		await new ESExprTextWriter(output).WriteAll(new Expr[] { new Expr.Int(1), new Expr.Int(2) }.ToAsyncEnumerable());
		Assert.That(output.ToString(), Is.EqualTo("1\n2\n"));
		await output.WriteAsync("#true");
		using var input = new StringReader(output.ToString());
		var reader = new ESExprTextReader(input);
		Assert.That(await reader.Read(), Is.EqualTo(new Expr.Int(1)));
		Assert.DoesNotThrow(() => input.Peek());
		Assert.That(await reader.ReadAll().CountAsync(), Is.EqualTo(2));
	}

	[Test]
	public void WriterRejectsInvalidRepresentations() {
		foreach(Expr expr in new Expr[] { new Expr.Str("\uD800"), new Expr.Null(-1), new Expr.Float16NaN(0), new Expr.Float32NaN(0x7F800000), new Expr.Float64NaN(0) }) {
			Assert.ThrowsAsync<SyntaxException>(async () => await Format(expr));
		}
		using var output = new StringWriter();
		Assert.Throws<ArgumentException>(() => new ESExprTextWriter(output, "x"));
		Assert.Throws<ArgumentException>(() => new ESExprTextWriter(output, " \t"));
		Assert.Throws<ArgumentNullException>(() => new ESExprTextReader(null!));
		Assert.Throws<ArgumentNullException>(() => new ESExprTextWriter(null!));
	}

	[Test]
	public void InputAndOutputErrorsPropagate() {
		using var input = new FailingReader();
		using var output = new FailingWriter();
		Assert.ThrowsAsync<IOException>(async () => await new ESExprTextReader(input).Read());
		Assert.ThrowsAsync<IOException>(async () => await new ESExprTextWriter(output).Write(new Expr.Bool(true)));
	}

	private sealed class ShortReadTextReader(string source, int maxRead) : StringReader(source) {
		public override ValueTask<int> ReadAsync(Memory<char> buffer, CancellationToken cancellationToken = default) =>
			base.ReadAsync(buffer[..Math.Min(buffer.Length, maxRead)], cancellationToken);
	}

	private sealed class GatedTextReader : TextReader {
		internal bool Continue;
		private int reads;
		public override ValueTask<int> ReadAsync(Memory<char> buffer, CancellationToken cancellationToken = default) {
			if(reads != 0 && !Continue) throw new InvalidOperationException("Read ahead before yielding the first expression");
			string text = reads++ switch { 0 => "(first)", 1 => "(second)", _ => "" };
			text.AsMemory().CopyTo(buffer);
			return ValueTask.FromResult(text.Length);
		}
	}

	private sealed class FailingReader : TextReader {
		public override ValueTask<int> ReadAsync(Memory<char> buffer, CancellationToken cancellationToken = default) => throw new IOException("read failed");
	}

	private sealed class FailingWriter : TextWriter {
		public override System.Text.Encoding Encoding => System.Text.Encoding.UTF8;
		public override Task WriteAsync(ReadOnlyMemory<char> buffer, CancellationToken cancellationToken = default) => throw new IOException("write failed");
	}
}
