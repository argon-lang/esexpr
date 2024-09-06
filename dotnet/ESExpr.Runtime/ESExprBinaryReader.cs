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

public class ESExprBinaryReader {
	public ESExprBinaryReader(IImmutableList<string> symbolTable, Stream stream) {
		this.symbolTable = symbolTable;
		this.stream = stream;
	}
	
	private readonly IImmutableList<string> symbolTable;
	private readonly Stream stream;

	private readonly byte[] byteBuffer = new byte[1];
	private int nextByte = -1;

	public async ValueTask<Expr?> Read(CancellationToken cancellationToken = default) {
		if(await PeekNext(cancellationToken).ConfigureAwait(false) < 0) {
			return null;
		}

		return await ReadExpr(cancellationToken).ConfigureAwait(false);
	}

	public async IAsyncEnumerable<Expr> ReadAll([EnumeratorCancellation] CancellationToken cancellationToken = default) {
		while(true) {
			cancellationToken.ThrowIfCancellationRequested();
			
			var expr = await Read(cancellationToken).ConfigureAwait(false);
			if(expr == null) {
				break;
			}

			yield return expr;
		}
	}

	public static async IAsyncEnumerable<Expr> ReadEmbeddedStringTable(Stream stream, [EnumeratorCancellation] CancellationToken cancellationToken = default) {
		IESExprCodec<StringTable> stCodec = new StringTable.Codec();
		
		var stExpr = await new ESExprBinaryReader(ImmutableList<string>.Empty, stream).ReadExpr(cancellationToken).ConfigureAwait(false);
		var stringTable = stCodec.Decode(stExpr);

		await foreach(var expr in new ESExprBinaryReader(stringTable.strings.ImmutableList, stream).ReadAll(cancellationToken).ConfigureAwait(false)) {
			yield return expr;	
		}
	}

	private string LookupStringTable(BigInteger index) {
		if(index < 0 || index >= symbolTable.Count) {
			throw new SyntaxException();
		}

		return symbolTable[(int)index];
	}


	private async ValueTask<int> Next(CancellationToken cancellationToken = default) {
		if(nextByte >= 0) {
			int res = nextByte;
			nextByte = -1;
			return res;
		}
		
		int bytesRead = await stream.ReadAsync(byteBuffer, cancellationToken).ConfigureAwait(false);
		if(bytesRead == 0) {
			return -1;
		}
		
		return byteBuffer[0];
	}

	private async ValueTask<int> PeekNext(CancellationToken cancellationToken = default) {
		if(nextByte >= 0) {
			return nextByte;
		}

		int bytesRead = await stream.ReadAsync(byteBuffer, cancellationToken).ConfigureAwait(false);
		if(bytesRead == 0) {
			return -1;
		}
		
		nextByte = byteBuffer[0];
		return nextByte;
	}

	private async ValueTask<Expr> ReadExpr(CancellationToken cancellationToken = default) {
		var token = await NextToken(cancellationToken).ConfigureAwait(false);
		return await ReadExprWith(token, cancellationToken).ConfigureAwait(false);
	}

	private async ValueTask<Expr> ReadExprWith(BinToken token, CancellationToken cancellationToken = default) {
		switch(token.BinTokenType) {
			case BinToken.TokenType.Constructor:
				return await ReadConstructor(LookupStringTable(token.IntValue ?? throw new SyntaxException()), cancellationToken).ConfigureAwait(false);
			
			case BinToken.TokenType.Int:
				return new Expr.Int(token.IntValue ?? throw new SyntaxException());
			
			case BinToken.TokenType.NegInt:
				return new Expr.Int(-((token.IntValue ?? throw new SyntaxException()) + 1));

			case BinToken.TokenType.String:
			{
				var bytes = await ReadBytes(token.IntValue ?? throw new SyntaxException());
				return new Expr.Str(Encoding.UTF8.GetString(bytes));
			}

			case BinToken.TokenType.StringPoolIndex:
				return new Expr.Str(LookupStringTable(token.IntValue ?? throw new SyntaxException()));
			
			case BinToken.TokenType.Binary:
				return new Expr.Binary(await ReadBytes(token.IntValue ?? throw new SyntaxException()));
			
			case BinToken.TokenType.Null0:
				return new Expr.Null(0);
			
			case BinToken.TokenType.Null1:
				return new Expr.Null(1);
			
			case BinToken.TokenType.Null2:
				return new Expr.Null(2);

			case BinToken.TokenType.NullN:
			{
				var level = await ReadInt(0, 0, cancellationToken).ConfigureAwait(false);
				return new Expr.Null(level + 3);	
			}
				
			
			case BinToken.TokenType.True:
				return new Expr.Bool(true);
			
			case BinToken.TokenType.False:
				return new Expr.Bool(false);

			case BinToken.TokenType.Float32:
			{
				int bits = 0;
				for(int i = 0; i < sizeof(int); ++i) {
					int b = await Next(cancellationToken);
					if(b < 0) {
						throw new SyntaxException();
					}

					bits |= b << (i * 8);
				}

				return new Expr.Float32(BitConverter.Int32BitsToSingle(bits));
			}

			case BinToken.TokenType.Float64:
			{
				long bits = 0;
				for(int i = 0; i < sizeof(long); ++i) {
					long b = await Next(cancellationToken);
					if(b < 0) {
						throw new SyntaxException();
					}

					bits |= b << (i * 8);
				}

				return new Expr.Float64(BitConverter.Int64BitsToDouble(bits));
			}
			
			case BinToken.TokenType.ConstructorStartStringTable:
				return await ReadConstructor(StringTable.Codec.StringTableConstructor, cancellationToken).ConfigureAwait(false);
				
			case BinToken.TokenType.ConstructorStartList:
				return await ReadConstructor(VList<int>.Codec.ListConstructor, cancellationToken).ConfigureAwait(false);
			
			default:
				throw new SyntaxException();
		}
	}

	private async Task<Expr> ReadConstructor(string constructor, CancellationToken cancellationToken = default) {
		var args = new List<Expr>();
		var kwargs = new Dictionary<string, Expr>();

		while(true) {
			cancellationToken.ThrowIfCancellationRequested();
			
			var token = await NextToken(cancellationToken).ConfigureAwait(false);

			if(token.BinTokenType == BinToken.TokenType.ConstructorEnd) {
				break;
			}
			else if(token.BinTokenType == BinToken.TokenType.Keyword) {
				var kw = LookupStringTable(token.IntValue ?? throw new SyntaxException());
				var value = await ReadExpr(cancellationToken);
				kwargs.Add(kw, value);
			}
			else {
				var value = await ReadExprWith(token, cancellationToken);
				args.Add(value);
			}
		}
		
		return new Expr.Constructor(constructor, args, kwargs);
	}

	private async ValueTask<byte[]> ReadBytes(BigInteger tokenIntValue) {
		int length = (int)tokenIntValue;
		// Assume nextByte is -1 here.
		byte[] buff = new byte[length];
		await stream.ReadExactlyAsync(buff);
		return buff;
	}


	private async ValueTask<BinToken> NextToken(CancellationToken cancellationToken = default) {
		int b = await Next(cancellationToken).ConfigureAwait(false);
		if(b < 0) {
			throw new SyntaxException();
		}

		BinToken.TokenType? tokenType = (b & 0xE0) switch {
			0x00 => BinToken.TokenType.Constructor,
			0x20 => BinToken.TokenType.Int,
			0x40 => BinToken.TokenType.NegInt,
			0x60 => BinToken.TokenType.String,
			0x80 => BinToken.TokenType.StringPoolIndex,
			0xA0 => BinToken.TokenType.Binary,
			0xC0 => BinToken.TokenType.Keyword,
			_ => null,
		};

		BigInteger? intValue;
		if(tokenType is { } tt) {
			BigInteger i = b & 0x0F;
			if((b & 0x10) == 0x10) {
				i = await ReadInt(i, 4, cancellationToken).ConfigureAwait(false);
			}

			intValue = i;
		}
		else {
			tt = b switch {
				0xE0 => BinToken.TokenType.ConstructorEnd,
				0xE1 => BinToken.TokenType.True,
				0xE2 => BinToken.TokenType.False,
				0xE3 => BinToken.TokenType.Null0,
				0xE4 => BinToken.TokenType.Float32,
				0xE5 => BinToken.TokenType.Float64,
				0xE6 => BinToken.TokenType.ConstructorStartStringTable,
				0xE7 => BinToken.TokenType.ConstructorStartList,
				0xE8 => BinToken.TokenType.Null1,
				0xE9 => BinToken.TokenType.Null2,
				0xEA => BinToken.TokenType.NullN,
				_ => throw new SyntaxException(),
			};

			intValue = null;
		}

		return new BinToken(tt, intValue);
	}

	private async ValueTask<BigInteger> ReadInt(BigInteger acc, int bits, CancellationToken cancellationToken = default) {
		while(true) {
			int b = await Next(cancellationToken).ConfigureAwait(false);
			if(b < 0) {
				throw new SyntaxException();
			}

			acc |= (BigInteger)(b & 0x7F) << bits;
			bits += 7;

			if((b & 0x80) == 0) {
				return acc;
			}
		}
	}
}
