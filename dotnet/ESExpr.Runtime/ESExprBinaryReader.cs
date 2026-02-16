using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.IO;
using System.Linq;
using System.Numerics;
using System.Runtime.CompilerServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using ESExpr.Runtime.Codecs;

namespace ESExpr.Runtime;

public class ESExprBinaryReader {
	public ESExprBinaryReader(Stream stream) {
		symbolTable = new List<string>();
		this.stream = stream;
	}

	public ESExprBinaryReader(IImmutableList<string> symbolTable, Stream stream) {
		this.symbolTable = symbolTable.ToList();
		this.stream = stream;
	}

	private readonly List<string> symbolTable;
	private readonly Stream stream;

	private readonly byte[] byteBuffer = new byte[1];
	private int nextByte = -1;

	public async ValueTask<Expr?> TryRead(CancellationToken cancellationToken = default) {
		var readVisitor = new ReadExprVisitor();

		for(; ; )
		{
			cancellationToken.ThrowIfCancellationRequested();

			var expr = await ReadExprPlus(readVisitor, cancellationToken);
			if(readVisitor.IsDone) {
				return expr;
			}
		}
	}

	public async ValueTask<Expr> Read(CancellationToken cancellationToken = default) {
		var expr = await TryRead(cancellationToken).ConfigureAwait(false);
		if(expr is null) {
			throw new EndOfStreamException();
		}

		return expr;
	}

	public async IAsyncEnumerable<Expr> ReadAll([EnumeratorCancellation] CancellationToken cancellationToken = default) {
		while(true) {
			cancellationToken.ThrowIfCancellationRequested();

			var expr = await TryRead(cancellationToken).ConfigureAwait(false);
			if(expr is null) {
				break;
			}

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

	private interface IExprPlusVisitor<out T> {
		T VisitExpr(Expr expr);
		T VisitConstructorEnd();
		T VisitKeyword(BigInteger index);
		T VisitAppendedToStringTable();
		T VisitEndOfStream();
	}

	private sealed class ReadExprVisitor : IExprPlusVisitor<Expr?> {
		public ReadExprVisitor() { }

		public bool IsDone { get; private set; } = false;

		public Expr? VisitExpr(Expr expr) {
			IsDone = true;
			return expr;
		}

		public Expr? VisitConstructorEnd() {
			throw new SyntaxException();
		}

		public Expr? VisitKeyword(BigInteger index) {
			throw new SyntaxException();
		}

		public Expr? VisitAppendedToStringTable() {
			return null;
		}

		public Expr? VisitEndOfStream() {
			IsDone = true;
			return null;
		}
	}

	private async ValueTask<T> ReadExprPlus<T>(IExprPlusVisitor<T> visitor, CancellationToken cancellationToken) {
		var tokenOpt = await NextToken(cancellationToken).ConfigureAwait(false);
		if(tokenOpt is not { } token) {
			return visitor.VisitEndOfStream();
		}

		Expr expr;
		switch(token.BinTokenType) {
			case BinToken.TokenType.Constructor:
				expr = await ReadConstructor(LookupStringTable(token.IntValue ?? throw new SyntaxException()), cancellationToken).ConfigureAwait(false);
				break;

			case BinToken.TokenType.Int:
				expr = new Expr.Int(token.IntValue ?? throw new SyntaxException());
				break;

			case BinToken.TokenType.NegInt:
				expr = new Expr.Int(-((token.IntValue ?? throw new SyntaxException()) + 1));
				break;

			case BinToken.TokenType.String: {
				var bytes = await ReadBytes(token.IntValue ?? throw new SyntaxException(), cancellationToken);
				expr = new Expr.Str(Encoding.UTF8.GetString(bytes));
				break;
			}

			case BinToken.TokenType.StringPoolIndex:
				expr = new Expr.Str(LookupStringTable(token.IntValue ?? throw new SyntaxException()));
				break;

			case BinToken.TokenType.Null0:
				expr = new Expr.Null(0);
				break;

			case BinToken.TokenType.Null1:
				expr = new Expr.Null(1);
				break;

			case BinToken.TokenType.Null2:
				expr = new Expr.Null(2);
				break;

			case BinToken.TokenType.NullN: {
				var level = await ReadInt(0, 0, cancellationToken).ConfigureAwait(false);
				expr = new Expr.Null(level + 3);
				break;
			}


			case BinToken.TokenType.True:
				expr = new Expr.Bool(true);
				break;

			case BinToken.TokenType.False:
				expr = new Expr.Bool(false);
				break;

			case BinToken.TokenType.Float16: {
				var bits = await ReadFixed<ushort>(sizeof(ushort), cancellationToken);
				var value = BitConverter.UInt16BitsToHalf(bits);
				if(Half.IsNaN(value)) {
					expr = new Expr.Float16NaN(bits);					
				}
				else {
					expr = new Expr.Float16(value);
				}
				
				break;
			}

			case BinToken.TokenType.Float32: {
				var bits = await ReadFixed<uint>(sizeof(float), cancellationToken);
				var value = BitConverter.UInt32BitsToSingle(bits);

				if(float.IsNaN(value)) {
					expr = new Expr.Float32NaN(bits);
				}
				else {
					expr = new Expr.Float32(value);	
				}
				break;
			}

			case BinToken.TokenType.Float64: {
				var bits = await ReadFixed<ulong>(sizeof(double), cancellationToken);
				var value = BitConverter.UInt64BitsToDouble(bits);

				if(double.IsNaN(value)) {
					expr = new Expr.Float64NaN(bits);
				}
				else {
					expr = new Expr.Float64(value);
				}
				break;
			}

			case BinToken.TokenType.Array8:
				expr = new Expr.Array8(
					(await ReadBytes(token.IntValue ?? throw new SyntaxException(), cancellationToken)).ToImmutableArray()
				);
				break;

			case BinToken.TokenType.Array16: {
				expr = new Expr.Array16(await ReadArrayN<ushort>(sizeof(ushort), cancellationToken));
				break;
			}

			case BinToken.TokenType.Array32: {
				expr = new Expr.Array32(await ReadArrayN<uint>(sizeof(uint), cancellationToken));
				break;
			}

			case BinToken.TokenType.Array64: {
				expr = new Expr.Array64(await ReadArrayN<ulong>(sizeof(ulong), cancellationToken));
				break;
			}

			case BinToken.TokenType.Array128: {
				expr = new Expr.Array128(await ReadArrayN<UInt128>(16, cancellationToken));
				break;
			}


			case BinToken.TokenType.ConstructorStartStringTable:
				expr = await ReadConstructor(StringTable.StringTableConstructor, cancellationToken).ConfigureAwait(false);
				break;

			case BinToken.TokenType.ConstructorStartList:
				expr = await ReadConstructor(ListCodecBase<int, List<int>>.ListConstructor, cancellationToken).ConfigureAwait(false);
				break;
			
			case BinToken.TokenType.ConstructorStartMap:
				expr = await ReadConstructor(MapCodecBase<int, int, Dictionary<int, int>>.MapConstructor, cancellationToken).ConfigureAwait(false);
				break;
			
			case BinToken.TokenType.ConstructorStartSet:
				expr = await ReadConstructor(SetCodecBase<int, HashSet<int>>.SetConstructor, cancellationToken).ConfigureAwait(false);
				break;

			case BinToken.TokenType.AppendStringTable: {
				var newStringTableExpr = await Read(cancellationToken).ConfigureAwait(false);

				if(newStringTableExpr is Expr.Str s) {
					symbolTable.Add(s.value);
				}
				else {
					StringTable newStringTable;
					try {
						IESExprCodec<StringTable> codec = StringTable.Codec;
						newStringTable = codec.Decode(newStringTableExpr);
					}
					catch(DecodeException ex) {
						throw new SyntaxException("Could not decode string table", ex);
					}

					symbolTable.AddRange(newStringTable.strings);
				}

				return visitor.VisitAppendedToStringTable();
			}

			case BinToken.TokenType.ConstructorEnd:
				return visitor.VisitConstructorEnd();

			case BinToken.TokenType.Keyword:
				return visitor.VisitKeyword(token.IntValue ?? throw new SyntaxException());

			default:
				throw new SyntaxException();
		}

		return visitor.VisitExpr(expr);
	}

	private async ValueTask<N> ReadFixed<N>(int size, CancellationToken cancellationToken = default)
		where N : IBinaryInteger<N> {
		var bytes = await ReadBytes(size, cancellationToken);
		N value = N.Zero;
		for(int i = 0; i < size; ++i) {
			value |= N.CreateTruncating(bytes[i]) << (i * 8);
		}

		return value;
	}

	private async ValueTask<ImmutableArray<T>> ReadArrayN<T>(int byteSize, CancellationToken cancellationToken = default)
		where T : IBinaryInteger<T> {
		var lengthBig = await ReadInt(0, 0, cancellationToken).ConfigureAwait(false);
		var buff = await ReadBytes(lengthBig * byteSize, cancellationToken);
		int length = (int)lengthBig;
		var builder = ImmutableArray.CreateBuilder<T>(length);
		for(int i = 0; i < length; ++i) {
			T value = T.Zero;
			for(int j = 0; j < byteSize; ++j) {
				value |= T.CreateTruncating(buff[i * byteSize + j]) << (j * 8);
			}
			builder.Add(value);
		}
		return builder.MoveToImmutable();
	}

	private async Task<Expr> ReadConstructor(string constructor, CancellationToken cancellationToken = default) {
		var argVisitor = new ConstructorArgumentVisitor(this, cancellationToken);

		while(!argVisitor.IsDone) {
			cancellationToken.ThrowIfCancellationRequested();

			await await ReadExprPlus(argVisitor, cancellationToken);
		}

		return new Expr.Constructor(constructor, argVisitor.Args.ToImmutable(), argVisitor.Kwargs.ToImmutable());
	}

	private sealed class ConstructorArgumentVisitor(ESExprBinaryReader reader, CancellationToken cancellationToken)
		: IExprPlusVisitor<ValueTask> {
		public ImmutableList<Expr>.Builder Args { get; } = ImmutableList.CreateBuilder<Expr>();
		public ImmutableDictionary<string, Expr>.Builder Kwargs { get; } = ImmutableDictionary.CreateBuilder<string, Expr>();
		public bool IsDone { get; private set; } = false;

		public async ValueTask VisitExpr(Expr expr) {
			Args.Add(expr);
		}

		public async ValueTask VisitConstructorEnd() {
			IsDone = true;
		}

		public async ValueTask VisitKeyword(BigInteger index) {
			var kw = reader.LookupStringTable(index);
			var value = await reader.Read(cancellationToken);
			Kwargs.Add(kw, value);
		}

		public async ValueTask VisitAppendedToStringTable() {
		}

		public async ValueTask VisitEndOfStream() {
			throw new EndOfStreamException();
		}
	}

	private async ValueTask<byte[]> ReadBytes(BigInteger tokenIntValue, CancellationToken cancellationToken = default) {
		if(tokenIntValue < 0 || tokenIntValue >= int.MaxValue) {
			throw new SyntaxException("Length too large");
		}

		int length = (int)tokenIntValue;
		// Assume nextByte is -1 here.
		byte[] buff = new byte[length];
		await stream.ReadExactlyAsync(buff, cancellationToken);
		return buff;
	}




	private async ValueTask<BinToken?> NextToken(CancellationToken cancellationToken = default) {
		int b = await Next(cancellationToken).ConfigureAwait(false);
		if(b < 0) {
			return null;
		}

		BinToken.TokenType? tokenType = (b & 0xE0) switch {
			0x00 => BinToken.TokenType.Constructor,
			0x20 => BinToken.TokenType.Int,
			0x40 => BinToken.TokenType.NegInt,
			0x60 => BinToken.TokenType.String,
			0x80 => BinToken.TokenType.StringPoolIndex,
			0xA0 => BinToken.TokenType.Array8,
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
				0xE8 => BinToken.TokenType.Null1,
				0xE9 => BinToken.TokenType.Null2,
				0xEA => BinToken.TokenType.NullN,
				0xEC => BinToken.TokenType.Float16,
				0xE4 => BinToken.TokenType.Float32,
				0xE5 => BinToken.TokenType.Float64,
				0xED => BinToken.TokenType.Array16,
				0xEE => BinToken.TokenType.Array32,
				0xEF => BinToken.TokenType.Array64,
				0xF0 => BinToken.TokenType.Array128,
				0xE6 => BinToken.TokenType.ConstructorStartStringTable,
				0xE7 => BinToken.TokenType.ConstructorStartList,
				0xF1 => BinToken.TokenType.ConstructorStartMap,
				0xF2 => BinToken.TokenType.ConstructorStartSet,
				0xEB => BinToken.TokenType.AppendStringTable,
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
