package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

/**
 * A reader for the ESExpr binary format.
 */
public class ESExprBinaryReader {
	/**
	 * Create a reader for the ESExpr binary format.
	 * @param symbolTable The symbol table used when parsing.
	 * @param is The stream.
	 */
	public ESExprBinaryReader(@NotNull List<String> symbolTable, @NotNull InputStream is) {
		this.symbolTable = new ArrayList<>(symbolTable);
		this.is = is;
	}

	private final List<String> symbolTable;
	private final @NotNull InputStream is;
	private int nextByte = -1;

	/**
	 * Attempts to read an ESExpr from the stream.
	 * @return The ESExpr, or null if at the end of the stream.
	 * @throws IOException when an error occurs in the underlying stream.
	 * @throws SyntaxException when an expression cannot be read.
	 */
	public @Nullable ESExpr read() throws IOException, SyntaxException {
		if(peekNext() < 0) {
			return null;
		}

		return readExpr();
	}

	/**
	 * Reads all ESExpr values from the stream.
	 * @return A stream of ESExpr values.
	 */
	public @NotNull Stream<@NotNull ESExpr> readAll() {
		return Stream
			.generate(() -> {
				try {
					return read();
				}
				catch(IOException | SyntaxException ex) {
					throw new RuntimeException(ex);
				}
			})
			.takeWhile(Objects::nonNull);
	}

	/**
	 * Reads all ESExpr values, using the first as the string table.
	 * @param is The input stream.
	 * @return A stream of ESExpr values.
	 * @throws IOException when an error occurs in the underlying stream.
	 * @throws SyntaxException when an expression cannot be read.
	 */
	public static @NotNull Stream<@NotNull ESExpr> readEmbeddedStringTable(InputStream is) throws IOException, SyntaxException {
		try {
			var stExpr = new ESExprBinaryReader(List.of(), is).readExpr();
			var stringTable = StringTable.codec.decode(stExpr);
			return new ESExprBinaryReader(stringTable.values(), is).readAll();
		}
		catch(DecodeException ex) {
			throw new SyntaxException(ex);
		}
	}



	private int next() throws IOException {
		if(nextByte >= 0) {
			int res = nextByte;
			nextByte = -1;
			return res;
		}

		return is.read();
	}

	private int peekNext() throws IOException {
		if(nextByte >= 0) {
			return nextByte;
		}

		nextByte = is.read();
		return nextByte;
	}

	private BinToken nextToken() throws IOException, SyntaxException {
		int b = next();
		if(b < 0) {
			throw new EOFException();
		}

		BinToken.WithIntegerType type = switch((b & 0xE0)) {
			case 0x00 -> BinToken.WithIntegerType.CONSTRUCTOR;
			case 0x20 -> BinToken.WithIntegerType.INT;
			case 0x40 -> BinToken.WithIntegerType.NEG_INT;
			case 0x60 -> BinToken.WithIntegerType.STRING;
			case 0x80 -> BinToken.WithIntegerType.STRING_POOL_INDEX;
			case 0xA0 -> BinToken.WithIntegerType.BINARY;
			case 0xC0 -> BinToken.WithIntegerType.KEYWORD;
			default -> null;
		};

		if(type == null) {
			return switch(b) {
				case 0xE0 -> BinToken.Fixed.CONSTRUCTOR_END;
				case 0xE1 -> BinToken.Fixed.TRUE;
				case 0xE2 -> BinToken.Fixed.FALSE;
				case 0xE3 -> BinToken.Fixed.NULL;
				case 0xE4 -> BinToken.Fixed.FLOAT32;
				case 0xE5 -> BinToken.Fixed.FLOAT64;
				case 0xE6 -> BinToken.Fixed.CONSTRUCTOR_START_STRING_TABLE;
				case 0xE7 -> BinToken.Fixed.CONSTRUCTOR_START_LIST;
				default -> throw new SyntaxException();
			};
		}
		else {
			BigInteger i = BigInteger.valueOf(b & 0x0F);
			if((b & 0x10) == 0x10) {
				i = readInt(i, 4);
			}

			return new BinToken.WithInteger(type, i);
		}
	}

	private BigInteger readInt(BigInteger acc, int bits) throws IOException {
		while(true) {
			int b = next();
			if(b < 0) {
				throw new EOFException();
			}

			acc = acc.or(BigInteger.valueOf(b & 0x7F).shiftLeft(bits));
			bits += 7;

			if((b & 0x80) == 0) {
				return acc;
			}
		}
	}



	private @NotNull ESExpr readExpr() throws SyntaxException, IOException {
		return switch(readExprPlus()) {
			case ExprPlus.Expr(var expr) -> expr;
			default -> throw new SyntaxException();
		};
	}

	private sealed interface ExprPlus {
		record Expr(ESExpr expr) implements ExprPlus {}
		record ConstructorEnd() implements ExprPlus {}
		record Keyword(String name) implements ExprPlus {}
	}

	private @NotNull ExprPlus readExprPlus() throws SyntaxException, IOException {
		return switch(nextToken()) {
			case BinToken.WithInteger(var type, var value) -> switch(type) {
				case CONSTRUCTOR -> {
					var sym = symbolTable.get(value.intValueExact());
					yield new ExprPlus.Expr(readConstructor(sym));
				}
				case INT -> new ExprPlus.Expr(new ESExpr.Int(value));
				case NEG_INT -> new ExprPlus.Expr(new ESExpr.Int(value.add(BigInteger.ONE).negate()));

				// Should be safe to bypass next/peekNext here.
				case STRING -> {
					int len = value.intValueExact();
					byte[] b = new byte[len];
					if(is.readNBytes(b, 0, len) < len) {
						throw new EOFException();
					}

					yield new ExprPlus.Expr(new ESExpr.Str(new String(b, StandardCharsets.UTF_8)));
				}

				case STRING_POOL_INDEX -> {
					var sym = symbolTable.get(value.intValueExact());
					yield new ExprPlus.Expr(new ESExpr.Str(sym));
				}

				case BINARY -> {
					int len = value.intValueExact();
					byte[] b = new byte[len];
					if(is.readNBytes(b, 0, len) < len) {
						throw new EOFException();
					}

					yield new ExprPlus.Expr(new ESExpr.Binary(b));
				}

				case KEYWORD -> {
					var sym = symbolTable.get(value.intValueExact());
					yield new ExprPlus.Keyword(sym);
				}
			};

			case BinToken.Fixed fixed -> switch(fixed) {
				case NULL -> new ExprPlus.Expr(new ESExpr.Null());
				case CONSTRUCTOR_END -> new ExprPlus.ConstructorEnd();
				case TRUE -> new ExprPlus.Expr(new ESExpr.Bool(true));
				case FALSE -> new ExprPlus.Expr(new ESExpr.Bool(false));
				case FLOAT32 -> {
					int bits = 0;
					for(int i = 0; i < 4; ++i) {
						int b = next();
						if(b < 0) {
							throw new EOFException();
						}

						bits |= (b & 0xFF) << (i * 8);
					}

					yield new ExprPlus.Expr(new ESExpr.Float32(Float.intBitsToFloat(bits)));
				}
				case FLOAT64 -> {
					long bits = 0;
					for(int i = 0; i < 8; ++i) {
						int b = next();
						if(b < 0) {
							throw new EOFException();
						}

						bits |= (long)(b & 0xFF) << (i * 8);
					}

					yield new ExprPlus.Expr(new ESExpr.Float64(Double.longBitsToDouble(bits)));
				}

				case CONSTRUCTOR_START_STRING_TABLE -> new ExprPlus.Expr(readConstructor(BinToken.StringTableName));
				case CONSTRUCTOR_START_LIST -> new ExprPlus.Expr(readConstructor(BinToken.ListName));
			};
		};
	}

	private @NotNull ESExpr readConstructor(String name) throws IOException, SyntaxException {
		var args = new ArrayList<ESExpr>();
		var kwargs = new HashMap<String, ESExpr>();

		body:
		while(true) {
			switch(readExprPlus()) {
				case ExprPlus.Expr(var expr) -> args.add(expr);
				case ExprPlus.ConstructorEnd() -> {
					break body;
				}
				case ExprPlus.Keyword(var kw) -> {
					var expr = readExpr();
					kwargs.put(kw, expr);
				}
			}
		}

		return new ESExpr.Constructor(name, args, kwargs);
	}

}
