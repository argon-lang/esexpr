package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Encodes ESExpr values into a binary format.
 */
public class ESExprBinaryWriter {

	/**
	 * Creates an encoder.
	 * @param symbolTable The symbol table used when parsing.
	 * @param os The stream.
	 */
	public ESExprBinaryWriter(@NotNull List<? extends @NotNull String> symbolTable, OutputStream os) {

		this.symbolTable = symbolTable;
		this.os = os;
	}

	private final List<? extends @NotNull String> symbolTable;
	private final OutputStream os;

	/**
	 * Write an ESExpr to the stream.
	 * @param expr The ESExpr to write.
	 * @throws IOException when an error occurs in the underlying stream.
	 */
	public void write(ESExpr expr) throws IOException {
		switch(expr) {
			case ESExpr.Constructor(var constructor, var args, var kwargs) -> {
				switch(constructor) {
					case BinToken.StringTableName -> writeToken(BinToken.Fixed.CONSTRUCTOR_START_STRING_TABLE);
					case BinToken.ListName -> writeToken(BinToken.Fixed.CONSTRUCTOR_START_LIST);
					default -> {
						var index = getSymbolIndex(constructor);
						writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.CONSTRUCTOR, index));
					}
				}
				for(var arg : args) {
					write(arg);
				}
				for(var pair : kwargs.entrySet()) {
					writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.KEYWORD, getSymbolIndex(pair.getKey())));
					write(pair.getValue());
				}
				writeToken(BinToken.Fixed.CONSTRUCTOR_END);
			}

			case ESExpr.Bool(var b) -> {
				if(b) {
					writeToken(BinToken.Fixed.TRUE);
				}
				else {
					writeToken(BinToken.Fixed.FALSE);
				}
			}

			case ESExpr.Int(var i) -> {
				if(i.signum() < 0) {
					writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.NEG_INT, i.negate().subtract(BigInteger.ONE)));
				}
				else {
					writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.INT, i));
				}
			}

			case ESExpr.Str(var s) -> {
				byte[] b = s.getBytes(StandardCharsets.UTF_8);
				writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.STRING, BigInteger.valueOf(b.length)));
				os.write(b);
			}

			case ESExpr.Binary(var b) -> {
				writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.BINARY, BigInteger.valueOf(b.length)));
				os.write(b);
			}

			case ESExpr.Float32(var f) -> {
				writeToken(BinToken.Fixed.FLOAT32);
				int bits = Float.floatToRawIntBits(f);
				for(int i = 0; i < 4; ++i) {
					os.write(bits & 0xFF);
					bits >>>= 8;
				}
			}

			case ESExpr.Float64(var d) -> {
				writeToken(BinToken.Fixed.FLOAT64);
				long bits = Double.doubleToRawLongBits(d);
				for(int i = 0; i < 8; ++i) {
					os.write((int)bits & 0xFF);
					bits >>>= 8;
				}
			}

			case ESExpr.Null(var level) -> {
				if(level.equals(BigInteger.ZERO)) {
					writeToken(BinToken.Fixed.NULL0);
				}
				else if(level.equals(BigInteger.ONE)) {
					writeToken(BinToken.Fixed.NULL1);
				}
				else if(level.equals(BigInteger.valueOf(2))) {
					writeToken(BinToken.Fixed.NULL2);
				}
				else {
					writeToken(BinToken.Fixed.NULLN);
					writeInt(level.subtract(BigInteger.valueOf(3)));
				}
			}
		}
	}

	private void writeToken(BinToken token) throws IOException {
		switch(token) {
			case BinToken.WithInteger(var type, var value) -> {
				int b = switch(type) {
					case CONSTRUCTOR -> 0x00;
					case INT -> 0x20;
					case NEG_INT -> 0x40;
					case STRING -> 0x60;
					case STRING_POOL_INDEX -> 0x80;
					case BINARY -> 0xA0;
					case KEYWORD -> 0xC0;
				};

				b |= value.byteValue() & 0x0F;
				value = value.shiftRight(4);

				boolean isPos = value.signum() > 0;
				if(isPos) {
					b |= 0x10;
				}
				os.write(b);
				if(isPos) {
					writeInt(value);
				}
			}
			case BinToken.Fixed fixed -> {
				int b = switch(fixed) {
					case CONSTRUCTOR_END -> 0xE0;
					case TRUE -> 0xE1;
					case FALSE -> 0xE2;
					case NULL0 -> 0xE3;
					case FLOAT32 -> 0xE4;
					case FLOAT64 -> 0xE5;
					case CONSTRUCTOR_START_STRING_TABLE -> 0xE6;
					case CONSTRUCTOR_START_LIST -> 0xE7;
					case NULL1 -> 0xE8;
					case NULL2 -> 0xE9;
					case NULLN -> 0xEA;
				};
				os.write(b);
			}
		}
	}

	private BigInteger getSymbolIndex(String symbol) {
		int index = symbolTable.indexOf(symbol);
		if(index < 0) {
			throw new IndexOutOfBoundsException();
		}
		return BigInteger.valueOf(index);
	}

	private void writeInt(BigInteger value) throws IOException {
		do {
			int b = value.byteValue() & 0x7F;
			value = value.shiftRight(7);

			if(value.signum() > 0) {
				b |= 0x80;
			}
			os.write(b);
		} while(value.signum() > 0);
	}


	/**
	 * Creates a string table with the required values for an expression.
	 * @param expr The expression to scan.
	 * @return The string table for expr.
	 */
	public static @NotNull StringTable buildSymbolTable(@NotNull ESExpr expr) {
		var builder = new SymbolTableBuilder();
		builder.add(expr);
		return builder.build();
	}

	/**
	 * Builds a string table from expressions.
	 */
	public static final class SymbolTableBuilder {

		/**
		 * Creates a SymbolTableBuilder.
		 */
		public SymbolTableBuilder() {}

		private final Set<String> st = new HashSet<>();

		/**
		 * Add any required strings to the string table.
		 * @param expr The expression to scan.
		 */
		public void add(@NotNull ESExpr expr) {
			if(expr instanceof ESExpr.Constructor(var name, var args, var kwargs)) {
				if(!name.equals(BinToken.StringTableName) && !name.equals(BinToken.ListName)) {
					st.add(name);
				}

				for(var arg : args) {
					add(arg);
				}

				for(var kwarg : kwargs.entrySet()) {
					st.add(kwarg.getKey());
					add(kwarg.getValue());
				}
			}
		}

		/**
		 * Builds the string table.
		 * @return The string table.
		 */
		public @NotNull StringTable build() {
			return new StringTable(st.stream().toList());
		}
	}

	/**
	 * Write an expression with an embedded string table.
	 * @param os The stream to write to.
	 * @param expr The expression to write.
	 * @throws IOException If an IO error occurs.
	 */
	public static void writeWithSymbolTable(@NotNull OutputStream os, @NotNull ESExpr expr) throws IOException {
		var st = buildSymbolTable(expr);

		new ESExprBinaryWriter(List.of(), os).write(StringTable.codec().encode(st));
		new ESExprBinaryWriter(st.values(), os).write(expr);
	}

}
