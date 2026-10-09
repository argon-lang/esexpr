using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.IO;
using System.Numerics;
using System.Runtime.CompilerServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ESExpr.Runtime;

/// <summary>Reads Rust-compatible ESExpr text incrementally from a TextReader.</summary>
/// <remarks>The caller owns the underlying reader. Read operations must be sequential.</remarks>
public sealed class ESExprTextReader {
	public ESExprTextReader(TextReader reader) {
		ArgumentNullException.ThrowIfNull(reader);
		this.reader = reader;
	}

	private readonly TextReader reader;
	private readonly char[] buffer = new char[4096];
	private int index;
	private int length;
	private bool eof;

	private async ValueTask<int> Peek(CancellationToken cancellationToken) {
		cancellationToken.ThrowIfCancellationRequested();
		if(index == length && !eof) {
			length = await reader.ReadAsync(buffer.AsMemory(), cancellationToken).ConfigureAwait(false);
			index = 0;
			eof = length == 0;
		}
		return eof ? -1 : buffer[index];
	}

	private async ValueTask<char> Take(CancellationToken cancellationToken) {
		int c = await Peek(cancellationToken).ConfigureAwait(false);
		if(c < 0) throw new SyntaxException("Unexpected end of text");
		++index;
		return (char)c;
	}

	private async ValueTask Expect(char c, CancellationToken cancellationToken) {
		if(await Take(cancellationToken).ConfigureAwait(false) != c) throw new SyntaxException("Expected " + c);
	}

	private static bool IsWhitespace(int c) => c is ' ' or '\t' or '\r' or '\n';

	private async ValueTask SkipWhitespace(CancellationToken cancellationToken) {
		while(true) {
			int c = await Peek(cancellationToken).ConfigureAwait(false);
			if(IsWhitespace(c)) await Take(cancellationToken).ConfigureAwait(false);
			else if(c == '/') {
				await Take(cancellationToken).ConfigureAwait(false);
				await Expect('/', cancellationToken).ConfigureAwait(false);
				// Like Rust, comments must end with a newline.
				while(await Take(cancellationToken).ConfigureAwait(false) != '\n') { }
			}
			else return;
		}
	}

	private async ValueTask<string> ReadToken(bool colons, CancellationToken cancellationToken) {
		var token = new StringBuilder();
		while(true) {
			int c = await Peek(cancellationToken).ConfigureAwait(false);
			if(c < 0 || IsWhitespace(c) || c is '(' or ')' or '[' or ']' or '"' or '\'' or '/' or '#' || (!colons && c == ':')) return token.ToString();
			token.Append(await Take(cancellationToken).ConfigureAwait(false));
		}
	}

	private async ValueTask<string> ReadQuoted(char quote, CancellationToken cancellationToken) {
		await Expect(quote, cancellationToken).ConfigureAwait(false);
		var value = new StringBuilder();
		while(true) {
			char c = await Take(cancellationToken).ConfigureAwait(false);
			if(c == quote) {
				string result = value.ToString();
				ESExprTextFormat.ValidateUnicode(result);
				return result;
			}
			if(c != '\\') {
				value.Append(c);
				continue;
			}
			char escape = await Take(cancellationToken).ConfigureAwait(false);
			switch(escape) {
				case 'f': value.Append('\f'); break;
				case 'n': value.Append('\n'); break;
				case 'r': value.Append('\r'); break;
				case 't': value.Append('\t'); break;
				case '\\': case '"': case '\'': value.Append(escape); break;
				case 'u': {
					await Expect('{', cancellationToken).ConfigureAwait(false);
					int codepoint = 0;
					bool hasDigits = false;
					while(await Peek(cancellationToken).ConfigureAwait(false) != '}') {
						int digit = ESExprTextFormat.HexDigit(await Take(cancellationToken).ConfigureAwait(false));
						if(digit < 0 || codepoint > 0x10FFFF / 16) throw new SyntaxException("Invalid Unicode codepoint");
						codepoint = codepoint * 16 + digit;
						hasDigits = true;
					}
					await Take(cancellationToken).ConfigureAwait(false);
					if(!hasDigits || !Rune.IsValid(codepoint)) throw new SyntaxException("Invalid Unicode codepoint");
					value.Append(char.ConvertFromUtf32(codepoint));
					break;
				}
				default: throw new SyntaxException("Invalid string escape");
			}
		}
	}

	private async ValueTask<string> ReadIdentifier(CancellationToken cancellationToken) {
		await SkipWhitespace(cancellationToken).ConfigureAwait(false);
		if(await Peek(cancellationToken).ConfigureAwait(false) == '\'') return await ReadQuoted('\'', cancellationToken).ConfigureAwait(false);
		string name = await ReadToken(false, cancellationToken).ConfigureAwait(false);
		if(!ESExprTextFormat.Identifier().IsMatch(name)) throw new SyntaxException("Invalid identifier: " + name);
		return name;
	}

	private async ValueTask<Expr> ReadArray(int width, CancellationToken cancellationToken) {
		await Expect('[', cancellationToken).ConfigureAwait(false);
		var values = new List<BigInteger>();
		var limit = BigInteger.One << width;
		while(true) {
			await SkipWhitespace(cancellationToken).ConfigureAwait(false);
			if(await Peek(cancellationToken).ConfigureAwait(false) == ']') {
				await Take(cancellationToken).ConfigureAwait(false);
				break;
			}
			string token = await ReadToken(false, cancellationToken).ConfigureAwait(false);
			if(!ESExprTextFormat.TryInteger(token, out var value) || value.Sign < 0 || value >= limit) throw new SyntaxException("Invalid unsigned array element");
			values.Add(value);
		}
		return width switch {
			8 => new Expr.Array8(values.ConvertAll(value => (byte)value).ToImmutableArray()),
			16 => new Expr.Array16(values.ConvertAll(value => (ushort)value).ToImmutableArray()),
			32 => new Expr.Array32(values.ConvertAll(value => (uint)value).ToImmutableArray()),
			64 => new Expr.Array64(values.ConvertAll(value => (ulong)value).ToImmutableArray()),
			128 => new Expr.Array128(values.ConvertAll(value => (UInt128)value).ToImmutableArray()),
			_ => throw new SyntaxException("Invalid array width"),
		};
	}

	private async ValueTask<Expr> ReadConstructor(CancellationToken cancellationToken) {
		await Take(cancellationToken).ConfigureAwait(false);
		string name = await ReadIdentifier(cancellationToken).ConfigureAwait(false);
		var args = ImmutableList.CreateBuilder<Expr>();
		var kwargs = ImmutableDictionary.CreateBuilder<string, Expr>(StringComparer.Ordinal);
		while(true) {
			await SkipWhitespace(cancellationToken).ConfigureAwait(false);
			int next = await Peek(cancellationToken).ConfigureAwait(false);
			if(next == ')') {
				await Take(cancellationToken).ConfigureAwait(false);
				return new Expr.Constructor(name, args.ToImmutable(), kwargs.ToImmutable());
			}
			if(next == '\'' || next is >= 'a' and <= 'z') {
				string key = await ReadIdentifier(cancellationToken).ConfigureAwait(false);
				await SkipWhitespace(cancellationToken).ConfigureAwait(false);
				await Expect(':', cancellationToken).ConfigureAwait(false);
				kwargs[key] = await ReadExpression(cancellationToken).ConfigureAwait(false);
			}
			else args.Add(await ReadExpression(cancellationToken).ConfigureAwait(false));
		}
	}

	private async ValueTask<Expr> ReadExpression(CancellationToken cancellationToken) {
		await SkipWhitespace(cancellationToken).ConfigureAwait(false);
		int c = await Peek(cancellationToken).ConfigureAwait(false);
		if(c < 0) throw new SyntaxException("Unexpected end of text");
		if(c == '"') return new Expr.Str(await ReadQuoted('"', cancellationToken).ConfigureAwait(false));
		if(c == '(') return await ReadConstructor(cancellationToken).ConfigureAwait(false);
		if(c == '#') {
			await Take(cancellationToken).ConfigureAwait(false);
			if(await Peek(cancellationToken).ConfigureAwait(false) == '"') {
				await Take(cancellationToken).ConfigureAwait(false);
				var bytes = ImmutableArray.CreateBuilder<byte>();
				while(await Peek(cancellationToken).ConfigureAwait(false) != '"') {
					int high = ESExprTextFormat.HexDigit(await Take(cancellationToken).ConfigureAwait(false));
					int low = ESExprTextFormat.HexDigit(await Take(cancellationToken).ConfigureAwait(false));
					if(high < 0 || low < 0) throw new SyntaxException("Invalid hexadecimal byte array");
					bytes.Add((byte)(high * 16 + low));
				}
				await Take(cancellationToken).ConfigureAwait(false);
				return new Expr.Array8(bytes.ToImmutable());
			}
			string token = "#" + await ReadToken(true, cancellationToken).ConfigureAwait(false);
			int width = token switch { "#u8" => 8, "#u16" => 16, "#u32" => 32, "#u64" => 64, "#u128" => 128, _ => 0 };
			if(width != 0) return await ReadArray(width, cancellationToken).ConfigureAwait(false);
			return ESExprTextFormat.ParseAtom(token);
		}
		return ESExprTextFormat.ParseAtom(await ReadToken(false, cancellationToken).ConfigureAwait(false));
	}

	/// <summary>Read an expression, or return null at the end of input.</summary>
	public async ValueTask<Expr?> TryRead(CancellationToken cancellationToken = default) {
		await SkipWhitespace(cancellationToken).ConfigureAwait(false);
		return await Peek(cancellationToken).ConfigureAwait(false) < 0 ? null : await ReadExpression(cancellationToken).ConfigureAwait(false);
	}

	/// <summary>Read an expression, throwing EndOfStreamException at the end of input.</summary>
	public async ValueTask<Expr> Read(CancellationToken cancellationToken = default) =>
		await TryRead(cancellationToken).ConfigureAwait(false) ?? throw new EndOfStreamException();

	public async IAsyncEnumerable<Expr> ReadAll([EnumeratorCancellation] CancellationToken cancellationToken = default) {
		while(await TryRead(cancellationToken).ConfigureAwait(false) is { } expr) yield return expr;
	}
}
