using System.Collections.Immutable;
using System.Numerics;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class BinaryFormatTests : TestBase {

	private List<Expr> DecodeJson(JsonElement elem) {
		Expr DecodeConstructor(JsonElement elem, string constructor) {
			ImmutableList<Expr> args = [];
			if(elem.TryGetProperty("args", out var argsArray)) {
				args = argsArray.EnumerateArray().Select(DecodeJsonExpr).ToImmutableList();
			}

			ImmutableDictionary<string, Expr> kwargs = ImmutableDictionary<string, Expr>.Empty;
			if(elem.TryGetProperty("kwargs", out var kwargsObj)) {
				kwargs = kwargsObj.EnumerateObject().ToImmutableDictionary(
					field => field.Name,
					field => DecodeJsonExpr(field.Value)
				);
			}

			return new Expr.Constructor(constructor, args, kwargs);
		}

		Expr DecodeJsonExpr(JsonElement elem) =>
			elem.ValueKind switch {
				JsonValueKind.True => new Expr.Bool(true),
				JsonValueKind.False => new Expr.Bool(false),
				JsonValueKind.String => new Expr.Str(elem.GetString()!),
				JsonValueKind.Null => new Expr.Null(0),
				JsonValueKind.Array => new Expr.Constructor(
					"list",
					elem.EnumerateArray().Select(DecodeJsonExpr).ToImmutableList(),
					ImmutableDictionary<string, Expr>.Empty
				),
				JsonValueKind.Object when elem.TryGetProperty("constructor_name", out var constructor) =>
					DecodeConstructor(elem, constructor.GetString() ?? throw new InvalidOperationException()),

				JsonValueKind.Object when elem.TryGetProperty("int", out var intValue) =>
					new Expr.Int(BigInteger.Parse(intValue.GetString() ?? throw new InvalidOperationException())),

				JsonValueKind.Object when elem.TryGetProperty("float16", out var float16Value) =>
					float16Value.ValueKind switch {
						JsonValueKind.String => float16Value.GetString() switch {
							"+inf" => new Expr.Float16(Half.PositiveInfinity),
							"-inf" => new Expr.Float16(Half.NegativeInfinity),
							"nan" => new Expr.Float16NaN(0x7E00),
							_ => throw new InvalidOperationException(),
						},
						JsonValueKind.Number => new Expr.Float16((Half)float16Value.GetSingle()),
						_ => throw new InvalidOperationException(),
					},

				JsonValueKind.Object when elem.TryGetProperty("float32", out var float32Value) =>
					float32Value.ValueKind switch {
						JsonValueKind.String => float32Value.GetString() switch {
							"+inf" => new Expr.Float32(float.PositiveInfinity),
							"-inf" => new Expr.Float32(float.NegativeInfinity),
							"nan" => new Expr.Float32NaN(0x7FC00000),
							_ => throw new InvalidOperationException(),
						},
						JsonValueKind.Number => new Expr.Float32(float32Value.GetSingle()),
						_ => throw new InvalidOperationException(),
					},

				JsonValueKind.Object when elem.TryGetProperty("float64", out var float64Value) =>
					float64Value.ValueKind switch {
						JsonValueKind.String => float64Value.GetString() switch {
							"+inf" => new Expr.Float64(double.PositiveInfinity),
							"-inf" => new Expr.Float64(double.NegativeInfinity),
							"nan" => new Expr.Float64NaN(0x7FF8000000000000),
							_ => throw new InvalidOperationException()
						},
						JsonValueKind.Number => new Expr.Float64(float64Value.GetDouble()),
						_ => throw new InvalidOperationException()
					},

				JsonValueKind.Object when elem.TryGetProperty("base64", out var binValue) =>
					new Expr.Array8(Convert.FromBase64String(binValue.GetString() ?? throw new InvalidOperationException()).ToImmutableArray()),

				JsonValueKind.Object when elem.TryGetProperty("array8", out var array8Value) =>
					new Expr.Array8(ReadFixedArray<byte>(array8Value)),

				JsonValueKind.Object when elem.TryGetProperty("array16", out var array16Value) =>
					new Expr.Array16(ReadFixedArray<ushort>(array16Value)),

				JsonValueKind.Object when elem.TryGetProperty("array32", out var array32Value) =>
					new Expr.Array32(ReadFixedArray<uint>(array32Value)),

				JsonValueKind.Object when elem.TryGetProperty("array64", out var array64Value) =>
					new Expr.Array64(ReadFixedArray<ulong>(array64Value)),

				JsonValueKind.Object when elem.TryGetProperty("array128", out var array128Value) =>
					new Expr.Array128(ReadFixedArray<UInt128>(array128Value)),

				JsonValueKind.Object when elem.TryGetProperty("null", out var nullLevel) =>
					new Expr.Null(BigInteger.Parse(nullLevel.GetString() ?? throw new InvalidOperationException())),

				_ => throw new ArgumentException($"Unexpected JSON: {elem}", nameof(elem)),
			};

		if(elem.ValueKind == JsonValueKind.Array) {
			return elem.EnumerateArray().Select(DecodeJsonExpr).ToList();
		}
		else {
			return [DecodeJsonExpr(elem)];
		}
	}

	private ImmutableArray<T> ReadFixedArray<T>(JsonElement arrayValue)
		where T : IUnsignedNumber<T> {
		if(arrayValue.ValueKind != JsonValueKind.Array)
			throw new ArgumentException("Expected array", nameof(arrayValue));

		return arrayValue.EnumerateArray()
			.Select(e => e.ValueKind switch {
				JsonValueKind.String => T.Parse(e.GetString()!, null),
				JsonValueKind.Number => T.CreateChecked(e.GetInt64()),
				_ => throw new ArgumentException($"Unexpected value kind: {e.ValueKind}", nameof(arrayValue))
			})
			.ToImmutableArray();
	}

	private async ValueTask<List<Expr>> ReadJsonFile(string path) {
		var text = await File.ReadAllTextAsync(path);
		var doc = JsonDocument.Parse(text);
		return DecodeJson(doc.RootElement);
	}

	private async ValueTask<List<Expr>> ReadEsxbFile(string path) {
		await using var stream = File.OpenRead(path);
		return await new ESExprBinaryReader(stream).ReadAll().ToListAsync();
	}

	[TestCaseSource(nameof(ListTestJsonFiles))]
	public async Task BinaryEncoding(string file) {
		var jsonValue = await ReadJsonFile(file);
		var esxbValue = await ReadEsxbFile(Path.Join(Path.GetDirectoryName(file), Path.GetFileNameWithoutExtension(file) + ".esxb"));

		var rewrittenValue = await esxbValue.ToAsyncEnumerable()
			.SelectAwait(async expr => await ParseEsxb(await EncodeEsxb(expr)))
			.ToListAsync();

		Assert.That(esxbValue, Is.EqualTo(jsonValue));
		Assert.That(rewrittenValue, Is.EqualTo(esxbValue));
	}

	public static IEnumerable<string> ListTestJsonFiles() {
		return Directory.EnumerateFiles("../../../../../tests/", "*.json");
	}


	private ValueTask<Expr> ParseEsxb(byte[] value) =>
		new ESExprBinaryReader(new MemoryStream(value)).ReadAll().SingleAsync();

	private async ValueTask<byte[]> EncodeEsxb(Expr expr) {
		var stream = new MemoryStream();
		await new ESExprBinaryWriter(stream).Write(expr);
		return stream.ToArray();
	}
}
