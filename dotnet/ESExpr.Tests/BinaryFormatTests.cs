using System.Collections.Immutable;
using System.Numerics;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Serialization;
using ESExpr.Runtime;

namespace ESExpr.Tests;

public class BinaryFormatTests : TestBase {

	private Expr DecodeJson(JsonElement elem) {
		Expr DecodeConstructor(JsonElement elem, string constructor) {
			ImmutableList<Expr> args = [];
			if(elem.TryGetProperty("args", out var argsArray)) {
				args = argsArray.EnumerateArray().Select(DecodeJson).ToImmutableList();
			}
			
			ImmutableDictionary<string, Expr> kwargs = ImmutableDictionary<string, Expr>.Empty;
			if(elem.TryGetProperty("kwargs", out var kwargsObj)) {
				kwargs = kwargsObj.EnumerateObject().ToImmutableDictionary(
					field => field.Name,
					field => DecodeJson(field.Value)
				);
			}
			
			return new Expr.Constructor(constructor, args, kwargs);
		}
		
		return elem.ValueKind switch {
			JsonValueKind.True => new Expr.Bool(true),
			JsonValueKind.False => new Expr.Bool(false),
			JsonValueKind.String => new Expr.Str(elem.GetString()!),
			JsonValueKind.Null => new Expr.Null(),
			JsonValueKind.Array => new Expr.Constructor(
				"list",
				elem.EnumerateArray().Select(DecodeJson).ToImmutableList(),
				ImmutableDictionary<string, Expr>.Empty
			),
			JsonValueKind.Object when elem.TryGetProperty("constructor_name", out var constructor) =>
				DecodeConstructor(elem, constructor.GetString() ?? throw new InvalidOperationException()),
			
			JsonValueKind.Object when elem.TryGetProperty("int", out var intValue) =>
				new Expr.Int(BigInteger.Parse(intValue.GetString() ?? throw new InvalidOperationException())),
			
			JsonValueKind.Object when elem.TryGetProperty("float32", out var float32Value) =>
				new Expr.Float32(float32Value.GetSingle()),
			
			JsonValueKind.Object when elem.TryGetProperty("float64", out var float64Value) =>
				new Expr.Float64(float64Value.GetDouble()),
			
			_ => throw new ArgumentException(nameof(elem)),
		};
	}

	private async ValueTask<Expr> ReadJsonFile(string path) {
		var text = await File.ReadAllTextAsync(path);
		var doc = JsonDocument.Parse(text);
		return DecodeJson(doc.RootElement);
	}

	private async ValueTask<Expr> ReadEsxbFile(string path) {
		await using var stream = File.OpenRead(path);
		return await ESExprBinaryReader.ReadEmbeddedStringTable(stream).SingleAsync();
	}

	[TestCaseSource(nameof(ListTestJsonFiles))]
	public async Task BinaryEncoding(string file) {
		var jsonValue = await ReadJsonFile(file);
		var esxbValue = await ReadEsxbFile(Path.Join(Path.GetDirectoryName(file), Path.GetFileNameWithoutExtension(file) + ".esxb"));

		var rewrittenValue = await ParseEsxb(await EncodeEsxb(esxbValue));
		
		Assert.That(esxbValue, Is.EqualTo(jsonValue));
		Assert.That(rewrittenValue, Is.EqualTo(esxbValue));
	}
	
	public static IEnumerable<string> ListTestJsonFiles() {
		return Directory.EnumerateFiles("../../../../../tests/", "*.json");
	} 
	

	private ValueTask<Expr> ParseEsxb(byte[] value) =>
		ESExprBinaryReader.ReadEmbeddedStringTable(new MemoryStream(value)).SingleAsync();

	private async ValueTask<byte[]> EncodeEsxb(Expr expr) {
		var st = ESExprBinaryWriter.BuildSymbolTable(expr);
		var stream = new MemoryStream();

		await new ESExprBinaryWriter([], stream).Write(new StringTable.Codec().Encode(st));
		await new ESExprBinaryWriter(st.strings.ImmutableList, stream).Write(expr);

		return stream.ToArray();
	}
}
