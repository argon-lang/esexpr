package dev.argon.esexpr;

import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Writes lossless ESExpr text, with sorted keyword arguments. */
public class ESExprTextWriter {
	/**
	 * Creates a writer using compact formatting.
	 * 
	 * @param writer
	 *            The character stream, owned by the caller.
	 */
	public ESExprTextWriter(Writer writer) {
		this(writer, null);
	}

	/**
	 * Creates a writer with optional indentation. Arrays remain on one line.
	 * 
	 * @param writer
	 *            The character stream, owned by the caller.
	 * @param indentation
	 *            Spaces or tabs per nesting level; null for compact output. An
	 *            empty string adds line breaks without indentation.
	 */
	public ESExprTextWriter(Writer writer, @Nullable String indentation) {
		if(indentation != null && !indentation.matches("[ \t]*"))
			throw new IllegalArgumentException("Indentation must contain only spaces or tabs");
		this.writer = writer;
		this.indentation = indentation;
	}

	private final Writer writer;
	private final @Nullable String indentation;
	private boolean written;
	private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");

	/**
	 * Writes an expression, separating successive expressions with newlines. Does
	 * not flush or close the underlying writer.
	 * 
	 * @param expr
	 *            The expression.
	 * @throws IOException
	 *             If the character stream fails.
	 * @throws IllegalArgumentException
	 *             If a null level is negative or an Array128 has odd length.
	 */
	public void write(ESExpr expr) throws IOException {
		if(written)
			writer.write('\n');
		written = true;
		writeExpr(expr, 0);
	}

	private void lineBreak(int depth) throws IOException {
		writer.write('\n');
		if(indentation != null)
			writer.write(indentation.repeat(depth));
	}

	private void separator(int depth) throws IOException {
		if(indentation == null)
			writer.write(' ');
		else
			lineBreak(depth);
	}

	private void identifier(String name) throws IOException {
		if(IDENTIFIER.matcher(name).matches())
			writer.write(name);
		else
			quoted(name, '\'');
	}

	private void quoted(String value, char quote) throws IOException {
		writer.write(quote);
		for(int i = 0; i < value.length();) {
			int c = value.codePointAt(i);
			i += Character.charCount(c);
			switch (c) {
				case '\\' -> writer.write("\\\\");
				case '\n' -> writer.write("\\n");
				case '\r' -> writer.write("\\r");
				case '\t' -> writer.write("\\t");
				case '\f' -> writer.write("\\f");
				default -> {
					if(c == quote) {
						writer.write('\\');
						writer.write(c);
					}
					else if(Character.isISOControl(c))
						writer.write("\\u{" + Integer.toHexString(c) + "}");
					else
						writer.write(Character.toChars(c));
				}
			}
		}
		writer.write(quote);
	}

	private void floating(long bits, int width, String finite, String suffix) throws IOException {
		long sign = 1L << (width - 1);
		long exponent = switch (width) {
			case 16 -> 0x7C00L;
			case 32 -> 0x7F800000L;
			default -> 0x7FF0000000000000L;
		};
		long mask = switch (width) {
			case 16 -> 0x3FFL;
			case 32 -> 0x7FFFFFL;
			default -> 0xFFFFFFFFFFFFFL;
		};
		if((bits & exponent) == exponent) {
			writer.write(
				"#float" + width + ":" + ((bits & sign) == 0 ? "+" : "-")
					+ ((bits & mask) == 0 ? "inf" : "nan:" + (bits & mask))
			);
		}
		else {
			writer.write(finite);
			writer.write(suffix);
		}
	}

	private static BigInteger unsigned(long value) {
		return new BigInteger(Long.toUnsignedString(value));
	}

	private void writeExpr(ESExpr expr, int depth) throws IOException {
		switch (expr) {
			case ESExpr.Constructor(var name, var args, var kwargs) -> {
				writer.write('(');
				identifier(name);
				for(var arg : args) {
					separator(depth + 1);
					writeExpr(arg, depth + 1);
				}
				for(var entry : new TreeMap<>(kwargs).entrySet()) {
					separator(depth + 1);
					identifier(entry.getKey());
					writer.write(": ");
					writeExpr(entry.getValue(), depth + 1);
				}
				if(indentation != null && (!args.isEmpty() || !kwargs.isEmpty()))
					lineBreak(depth);
				writer.write(')');
			}
			case ESExpr.Bool(var b) -> writer.write(b ? "#true" : "#false");
			case ESExpr.Int(var n) -> writer.write(n.toString());
			case ESExpr.Str(var s) -> quoted(s, '"');
			case ESExpr.Null(var level) -> {
				if(level.signum() < 0)
					throw new IllegalArgumentException("Null level must be nonnegative");
				writer.write(level.signum() == 0 ? "#null" : "#null" + level);
			}
			case ESExpr.Float16(var f) ->
				floating(Short.toUnsignedLong(f), 16, Float.toString(Float.float16ToFloat(f)), "f16");
			case ESExpr.Float32(var f) ->
				floating(Integer.toUnsignedLong(Float.floatToRawIntBits(f)), 32, Float.toString(f), "f");
			case ESExpr.Float64(var d) -> floating(Double.doubleToRawLongBits(d), 64, Double.toString(d), "d");
			case ESExpr.Array8(var b) -> {
				writer.write("#\"");
				for(int i = 0; i < b.size(); ++i) {
					int v = Byte.toUnsignedInt(b.get(i));
					writer.write(Character.forDigit(v >>> 4, 16));
					writer.write(Character.forDigit(v & 15, 16));
				}
				writer.write('"');
			}
			case ESExpr.Array16(var b) -> {
				writer.write("#u16[");
				for(int i = 0; i < b.size(); ++i) {
					if(i != 0)
						writer.write(' ');
					writer.write(Integer.toString(Short.toUnsignedInt(b.get(i))));
				}
				writer.write(']');
			}
			case ESExpr.Array32(var b) -> {
				writer.write("#u32[");
				for(int i = 0; i < b.size(); ++i) {
					if(i != 0)
						writer.write(' ');
					writer.write(Integer.toUnsignedString(b.get(i)));
				}
				writer.write(']');
			}
			case ESExpr.Array64(var b) -> {
				writer.write("#u64[");
				for(int i = 0; i < b.size(); ++i) {
					if(i != 0)
						writer.write(' ');
					writer.write(Long.toUnsignedString(b.get(i)));
				}
				writer.write(']');
			}
			case ESExpr.Array128(var b) -> {
				if(b.size() % 2 != 0)
					throw new IllegalArgumentException("Array128 must have even length");
				writer.write("#u128[");
				for(int i = 0; i < b.size(); i += 2) {
					if(i != 0)
						writer.write(' ');
					writer.write(unsigned(b.get(i + 1)).shiftLeft(64).or(unsigned(b.get(i))).toString());
				}
				writer.write(']');
			}
		}
	}
}
