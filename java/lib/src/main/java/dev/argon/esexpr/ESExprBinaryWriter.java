package dev.argon.esexpr;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Encodes ESExpr values into a binary format.
 */
public class ESExprBinaryWriter {

	/**
	 * Creates an encoder.
	 * @param symbolTable The initial symbol table used when writing.
	 * @param os The stream.
	 */
	public ESExprBinaryWriter(List<? extends String> symbolTable, OutputStream os) {
		this.symbolTable = new ArrayList<>(symbolTable);
		symbolSet = new HashSet<>(symbolTable);
		this.os = os;
	}

	/**
	 * Creates an encoder.
	 * @param os The stream.
	 */
	public ESExprBinaryWriter(OutputStream os) {
		symbolTable = new ArrayList<>();
		symbolSet = new HashSet<>();
		this.os = os;
	}

	private final List<String> symbolTable;
	private final Set<String> symbolSet;
	private final OutputStream os;

	/**
	 * Write an ESExpr to the stream.
	 * @param expr The ESExpr to write.
	 * @throws IOException when an error occurs in the underlying stream.
	 */
	public void write(ESExpr expr) throws IOException {
		int oldSize = symbolTable.size();
		addSymbols(expr);
		if(symbolTable.size() > oldSize) {
			writeToken(BinToken.Fixed.APPEND_STRING_TABLE);

			if(oldSize + 1 == symbolTable.size()) {
				writeExprRaw(new ESExpr.Str(symbolTable.get(oldSize)));
			}
			else {
				var appendTable = new StringTable(
					ImmutableList.copyOf(symbolTable.subList(oldSize, symbolTable.size()))
				);
				writeExprRaw(StringTable.codec().encode(appendTable));
			}
		}

		writeExprRaw(expr);
	}

	private void writeExprRaw(ESExpr expr) throws IOException {
		switch(expr) {
			case ESExpr.Constructor(var constructor, var args, var kwargs) -> {
				Objects.requireNonNull(constructor);
				switch(constructor) {
					case BinToken.StringTableName -> writeToken(BinToken.Fixed.CONSTRUCTOR_START_STRING_TABLE);
					case BinToken.ListName -> writeToken(BinToken.Fixed.CONSTRUCTOR_START_LIST);
					case BinToken.MapName -> writeToken(BinToken.Fixed.CONSTRUCTOR_START_MAP);
					case BinToken.SetName -> writeToken(BinToken.Fixed.CONSTRUCTOR_START_SET);
					default -> {
						var index = getSymbolIndex(constructor);
						writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.CONSTRUCTOR, index));
					}
				}
				for(var arg : args) {
					writeExprRaw(arg);
				}
				for(var pair : kwargs.entrySet()) {
					var index = getSymbolIndex(pair.getKey());
					writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.KEYWORD, index));
					writeExprRaw(pair.getValue());
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

			case ESExpr.Float16(var f) -> {
				writeToken(BinToken.Fixed.FLOAT16);
				int bits = Short.toUnsignedInt(f);
				for(int i = 0; i < 2; ++i) {
					os.write(bits & 0xFF);
					bits >>>= 8;
				}
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

			case ESExpr.Array8(var b) -> {
				writeToken(new BinToken.WithInteger(BinToken.WithIntegerType.ARRAY8, BigInteger.valueOf(b.size())));

				for(int i = 0; i < b.size(); ++i) {
					os.write(b.get(i) & 0xFF);
				}
			}

			case ESExpr.Array16(var b) -> {
				writeToken(BinToken.Fixed.ARRAY16);
				writeInt(BigInteger.valueOf(b.size()));
				for(int i = 0; i < b.size(); ++i) {
					short value = b.get(i);
					os.write(value & 0xFF);
					os.write((value >> 8) & 0xFF);
				}
			}

			case ESExpr.Array32(var b) -> {
				writeToken(BinToken.Fixed.ARRAY32);
				writeInt(BigInteger.valueOf(b.size()));
				for(int i = 0; i < b.size(); ++i) {
					int value = b.get(i);
					os.write(value & 0xFF);
					os.write((value >> 8) & 0xFF);
					os.write((value >> 16) & 0xFF);
					os.write((value >> 24) & 0xFF);
				}
			}

			case ESExpr.Array64(var b) -> {
				writeToken(BinToken.Fixed.ARRAY64);
				writeInt(BigInteger.valueOf(b.size()));
				for(int i = 0; i < b.size(); ++i) {
					long value = b.get(i);
					os.write((int) value & 0xFF);
					os.write((int) (value >> 8) & 0xFF);
					os.write((int) (value >> 16) & 0xFF);
					os.write((int) (value >> 24) & 0xFF);
					os.write((int) (value >> 32) & 0xFF);
					os.write((int) (value >> 40) & 0xFF);
					os.write((int) (value >> 48) & 0xFF);
					os.write((int) (value >> 56) & 0xFF);
				}
			}

			case ESExpr.Array128(var b) -> {
				if((b.size() % 2) != 0) {
					throw new IllegalArgumentException("Array128 must have even length");
				}
				writeToken(BinToken.Fixed.ARRAY128);
				writeInt(BigInteger.valueOf(b.size() / 2));
				for(int i = 0; i < b.size(); ++i) {
					long value = b.get(i);
					os.write((int) value & 0xFF);
					os.write((int) (value >> 8) & 0xFF);
					os.write((int) (value >> 16) & 0xFF);
					os.write((int) (value >> 24) & 0xFF);
					os.write((int) (value >> 32) & 0xFF);
					os.write((int) (value >> 40) & 0xFF);
					os.write((int) (value >> 48) & 0xFF);
					os.write((int) (value >> 56) & 0xFF);
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
					case ARRAY8 -> 0xA0;
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
					case NULL1 -> 0xE8;
					case NULL2 -> 0xE9;
					case NULLN -> 0xEA;
					case FLOAT16 -> 0xEC;
					case FLOAT32 -> 0xE4;
					case FLOAT64 -> 0xE5;
					case CONSTRUCTOR_START_STRING_TABLE -> 0xE6;
					case CONSTRUCTOR_START_LIST -> 0xE7;
					case CONSTRUCTOR_START_MAP -> 0xF1;
					case CONSTRUCTOR_START_SET -> 0xF2;
					case APPEND_STRING_TABLE -> 0xEB;
					case ARRAY16 -> 0xED;
					case ARRAY32 -> 0xEE;
					case ARRAY64 -> 0xEF;
					case ARRAY128 -> 0xF0;
				};
				os.write(b);
			}
		}
	}

	private BigInteger getSymbolIndex(String symbol) throws IOException {
		int index = symbolTable.indexOf(symbol);
		if(index < 0) {
			index = symbolTable.size();
			writeToken(BinToken.Fixed.APPEND_STRING_TABLE);
			writeExprRaw(new ESExpr.Str(symbol));
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


	private void addSymbols(ESExpr expr) {
		if(expr instanceof ESExpr.Constructor(var name, var args, var kwargs)) {
			if(!name.equals(BinToken.StringTableName) && !name.equals(BinToken.ListName)) {
				addSymbol(name);
			}

			for(var arg : args) {
				addSymbols(arg);
			}

			for(var kwarg : kwargs.entrySet()) {
				addSymbol(kwarg.getKey());
				addSymbols(kwarg.getValue());
			}
		}
	}

	private void addSymbol(String symbol) {
		if(symbolSet.add(symbol)) {
			symbolTable.add(symbol);
		}
	}

}
