using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.IO;
using System.Numerics;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ESExpr.Runtime;

public class ESExprBinaryWriter {
	
	public ESExprBinaryWriter(IImmutableList<string> symbolTable, Stream stream) {
		this.symbolTable = symbolTable;
		this.stream = stream;
	}
	
	private readonly IImmutableList<string> symbolTable;
	private readonly Stream stream;


	public async Task Write(Expr expr, CancellationToken cancellationToken = default) {
		switch(expr) {
			case Expr.Constructor(var constructor, var args, var kwargs):
				switch(constructor) {
					case StringTable.Codec.StringTableConstructor:
						await WriteToken(new BinToken(BinToken.TokenType.ConstructorStartStringTable, null), cancellationToken).ConfigureAwait(false);
						break;
					
					case VList<int>.Codec.ListConstructor:
						await WriteToken(new BinToken(BinToken.TokenType.ConstructorStartList, null), cancellationToken).ConfigureAwait(false);
						break;
						
					default:
						var index = GetSymbolIndex(constructor);
						await WriteToken(new BinToken(BinToken.TokenType.Constructor, index), cancellationToken).ConfigureAwait(false);
						break;
				}

				foreach(var arg in args) {
					await Write(arg, cancellationToken).ConfigureAwait(false);
				}

				foreach(var kvp in kwargs) {
					await WriteToken(new BinToken(BinToken.TokenType.Keyword, GetSymbolIndex(kvp.Key)), cancellationToken).ConfigureAwait(false);
					await Write(kvp.Value, cancellationToken).ConfigureAwait(false);
				}
				
				await WriteToken(new BinToken(BinToken.TokenType.ConstructorEnd, null), cancellationToken).ConfigureAwait(false);
				
				break;
			
			case Expr.Bool(true):
				await WriteToken(new BinToken(BinToken.TokenType.True, null), cancellationToken).ConfigureAwait(false);
				break;
			
			case Expr.Bool(false):
				await WriteToken(new BinToken(BinToken.TokenType.False, null), cancellationToken).ConfigureAwait(false);
				break;
			
			case Expr.Int(var i):
				if(i.Sign >= 0) {
					await WriteToken(new BinToken(BinToken.TokenType.Int, i), cancellationToken).ConfigureAwait(false);
				}
				else {
					await WriteToken(new BinToken(BinToken.TokenType.NegInt, (-i) - 1), cancellationToken).ConfigureAwait(false);
				}
				break;

			case Expr.Str(var s):
			{
				var b = Encoding.UTF8.GetBytes(s);
				await WriteToken(new BinToken(BinToken.TokenType.String, b.Length), cancellationToken).ConfigureAwait(false);
				await stream.WriteAsync(b, cancellationToken).ConfigureAwait(false);
				break;
			}
				
			case Expr.Binary(var b):
				await WriteToken(new BinToken(BinToken.TokenType.Binary, b.Length), cancellationToken).ConfigureAwait(false);
				await stream.WriteAsync(b, cancellationToken).ConfigureAwait(false);
				break;

			case Expr.Float32(var f):
			{
				await WriteToken(new BinToken(BinToken.TokenType.Float32, null), cancellationToken).ConfigureAwait(false);	
				
				uint bits = unchecked((uint)BitConverter.SingleToInt32Bits(f));
				for(int i = 0; i < sizeof(uint); ++i) {
					byte b = unchecked((byte)bits);
					await stream.WriteAsync(new byte[] { b }, cancellationToken).ConfigureAwait(false);
					bits >>= 8;
				}
				
				break;
			}
				
			case Expr.Float64(var d):
			{
				await WriteToken(new BinToken(BinToken.TokenType.Float64, null), cancellationToken).ConfigureAwait(false);	
				
				ulong bits = unchecked((ulong)BitConverter.DoubleToInt64Bits(d));
				for(int i = 0; i < sizeof(ulong); ++i) {
					byte b = unchecked((byte)bits);
					await stream.WriteAsync(new byte[] { b }, cancellationToken).ConfigureAwait(false);
					bits >>= 8;
				}
				
				break;
			}
			
			case Expr.Null:
				await WriteToken(new BinToken(BinToken.TokenType.Null, null), cancellationToken).ConfigureAwait(false);
				break;
				
			default:
				throw new InvalidOperationException();
		}
	}

	private BigInteger GetSymbolIndex(string constructor) {
		int index = symbolTable.IndexOf(constructor);
		if(index < 0) {
			throw new SyntaxException();
		}

		return index;
	}

	private async Task WriteToken(BinToken binToken, CancellationToken cancellationToken = default) {
		byte b = binToken.BinTokenType switch {
			BinToken.TokenType.Constructor => 0x00,
			BinToken.TokenType.Int => 0x20,
			BinToken.TokenType.NegInt => 0x40,
			BinToken.TokenType.String => 0x60,
			BinToken.TokenType.StringPoolIndex => 0x80,
			BinToken.TokenType.Binary => 0xA0,
			BinToken.TokenType.Keyword => 0xC0,
			BinToken.TokenType.ConstructorEnd => 0xE0,
			BinToken.TokenType.True => 0xE1,
			BinToken.TokenType.False => 0xE2,
			BinToken.TokenType.Null => 0xE3,
			BinToken.TokenType.Float32 => 0xE4,
			BinToken.TokenType.Float64 => 0xE5,
			BinToken.TokenType.ConstructorStartStringTable => 0xE6,
			BinToken.TokenType.ConstructorStartList => 0xE7,
			_ => throw new InvalidOperationException(),
		};

		if(binToken.IntValue is { } intValue) {
			b |= (byte)(intValue & 0x0F);
			intValue >>= 4;

			var isPos = intValue.Sign > 0;
			if(isPos) {
				b |= 0x10;
			}

			await stream.WriteAsync(new byte[] { b }, cancellationToken).ConfigureAwait(false);

			if(isPos) {
				await WriteInt(intValue, cancellationToken).ConfigureAwait(false);
			}
		}
		else {
			await stream.WriteAsync(new byte[] { b }, cancellationToken).ConfigureAwait(false);
		}
	}

	private async ValueTask WriteInt(BigInteger intValue, CancellationToken cancellationToken = default) {
		byte[] buff = new byte[1];
		do {
			byte b = (byte)(intValue & 0x7F);
			intValue >>= 7;

			if(intValue.Sign > 0) {
				b |= 0x80;
			}

			buff[0] = b;
			await stream.WriteAsync(buff, cancellationToken).ConfigureAwait(false);
		} while(intValue.Sign > 0);
	}


	public static StringTable BuildSymbolTable(Expr expr) {
		var builder = new SymbolTableBuilder();
		builder.Add(expr);
		return builder.Build();
	}

	public sealed class SymbolTableBuilder {
		private readonly ISet<string> st = new HashSet<string>();
		
		public void Add(Expr expr) {
			if(expr is Expr.Constructor(var name, var args, var kwargs)) {
				if(name != VList<int>.Codec.ListConstructor && name != StringTable.Codec.StringTableConstructor) {
					st.Add(name);
				}

				foreach(var arg in args) {
					Add(arg);
				}
				
				foreach(var kvp in kwargs) {
					st.Add(kvp.Key);
					Add(kvp.Value);
				}
			}
		}

		public StringTable Build() => new StringTable(st.ToImmutableList());
	}
}
