package dev.argon.esexpr;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.collections.api.factory.primitive.*;
import org.jspecify.annotations.Nullable;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** A streaming reader for the ESExpr text format. */
public class ESExprTextReader {
	/**
	 * Creates a reader. The caller owns the underlying stream.
	 * 
	 * @param reader
	 *            The character stream.
	 */
	public ESExprTextReader(Reader reader) {
		this.reader = reader;
	}
	private final Reader reader;
	private int ahead = -2;
	private long offset;
	private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
	private static final Pattern ARRAY_ATOM = Pattern.compile("u(8|16|32|64|128)");
	private static final Pattern NULL_ATOM = Pattern.compile("null[0-9]+");
	private static final Pattern INT = Pattern.compile("[+-]?(?:0[xX][0-9a-fA-F]+|0[bB][01]+|0o[0-7]+|[0-9]+)");
	private static final Pattern FLOAT = Pattern.compile(
		"[+-]?(?:[0-9]+\\.[0-9]+(?:[eE][+-]?[0-9]+)?|0[xX][0-9a-fA-F]+\\.[0-9a-fA-F]+[pP][+-]?[0-9]+)(?:f16|F16|[fFdD])?"
	);
	private static final Pattern FLOAT_ATOM = Pattern
		.compile("float(?<floatSize>16|32|64):(?<sign>[+-])?(?<nan>nan(?::(?<nanPayload>[0-9]+))?|(?<inf>inf))");

	/**
	 * Reads an expression or null at EOF.
	 * 
	 * @return The expression or null.
	 * @throws IOException
	 *             On stream failure.
	 * @throws SyntaxException
	 *             On invalid text.
	 */
	public @Nullable ESExpr tryRead() throws IOException, SyntaxException {
		ws();
		return peek() < 0 ? null : expr();
	}

	/**
	 * Reads an expression.
	 * 
	 * @return The expression.
	 * @throws EOFException
	 *             At EOF.
	 * @throws IOException
	 *             On stream failure.
	 * @throws SyntaxException
	 *             On invalid text.
	 */
	public ESExpr read() throws IOException, SyntaxException {
		var e = tryRead();
		if(e == null)
			throw new EOFException();
		return e;
	}

	/**
	 * Lazily reads expressions, wrapping checked errors in RuntimeException.
	 * 
	 * @return The expression stream.
	 */
	public Stream<ESExpr> readAll() {
		return Stream.generate(() -> {
			try {
				return tryRead();
			}
			catch(IOException | SyntaxException e) {
				throw new RuntimeException(e);
			}
		}).takeWhile(Objects::nonNull);
	}

	private int peek() throws IOException {
		if(ahead == -2)
			ahead = reader.read();
		return ahead;
	}

	private int next() throws IOException {
		int c = peek();
		ahead = -2;
		if(c >= 0)
			++offset;
		return c;
	}

	private SyntaxException error(String s) {
		return new SyntaxException(s + " at character " + offset);
	}

	private void expect(int c) throws IOException, SyntaxException {
		if(next() != c)
			throw error("Expected '" + (char)c + "'");
	}

	private void ws() throws IOException, SyntaxException {
		for(;;) {
			while (peek() >= 0 && " \t\r\n".indexOf(peek()) >= 0)
				next();
			if(peek() != '/')
				return;
			next();
			expect('/');
			while (peek() >= 0 && peek() != '\n')
				next();
		}
	}

	private String token() throws IOException {
		var s = new StringBuilder();
		while (peek() >= 0 && " \t\r\n()[]\"'/".indexOf(peek()) < 0)
			s.append((char)next());
		return s.toString();
	}

	private String identifier() throws IOException, SyntaxException {
		ws();
		if(peek() == '\'')
			return string();
		var s = new StringBuilder();
		while (peek() >= 0 && ((peek() >= 'a' && peek() <= 'z') || (peek() >= '0' && peek() <= '9') || peek() == '-'))
			s.append((char)next());
		if(!IDENTIFIER.matcher(s).matches())
			throw error("Invalid identifier");
		return s.toString();
	}

	private String string() throws IOException, SyntaxException {
		int quote = next();
		var s = new StringBuilder();
		while (peek() != quote) {
			int c = next();
			if(c < 0)
				throw error("Unterminated string");
			if(c != '\\') {
				s.append((char)c);
				continue;
			}
			switch(next()) {
				case 'f' -> s.append('\f');
				case 'n' -> s.append('\n');
				case 'r' -> s.append('\r');
				case 't' -> s.append('\t');
				case '\\' -> s.append('\\');
				case '"' -> s.append('"');
				case '\'' -> s.append('\'');
				case 'u' -> {
					expect('{');
					var digits = new StringBuilder();
					while (peek() >= 0 && Character.digit(peek(), 16) >= 0)
						digits.append((char)next());
					expect('}');
					try {
						int cp = Integer.parseInt(digits.toString(), 16);
						if(!Character.isValidCodePoint(cp) || (cp >= 0xD800 && cp <= 0xDFFF))
							throw error("Invalid Unicode code point");
						s.appendCodePoint(cp);
					}
					catch(NumberFormatException e) {
						throw error("Invalid Unicode code point");
					}
				}
				default -> throw error("Invalid escape");
			}
		}
		next();
		return s.toString();
	}

	private ESExpr expr() throws IOException, SyntaxException {
		ws();
		if(peek() == '(') {
			next();
			String name = identifier();
			var args = ImmutableList.<ESExpr>builder();
			var kwargs = new HashMap<String, ESExpr>();
			for(;;) {
				ws();
				if(peek() == ')') {
					next();
					break;
				}
				if(peek() == '\'' || (peek() >= 'a' && peek() <= 'z')) {
					String key = identifier();
					ws();
					expect(':');
					kwargs.put(key, expr());
				}
				else
					args.add(expr());
			}
			return new ESExpr.Constructor(name, args.build(), ImmutableMap.copyOf(kwargs));
		}
		if(peek() == '"')
			return new ESExpr.Str(string());
		if(peek() == '#') {
			next();
			if(peek() == '"') {
				next();
				var bytes = ByteLists.mutable.empty();
				while (peek() != '"') {
					int hi = Character.digit(next(), 16);
					int lo = Character.digit(next(), 16);
					if(hi < 0 || lo < 0)
						throw error("Invalid hexadecimal byte");
					bytes.add((byte)(hi * 16 + lo));
				}
				next();
				return new ESExpr.Array8(bytes.toImmutable());
			}
			String atom = token();
			if(ARRAY_ATOM.matcher(atom).matches())
				return array(Integer.parseInt(atom.substring(1)));
			if(atom.equals("true"))
				return new ESExpr.Bool(true);
			if(atom.equals("false"))
				return new ESExpr.Bool(false);
			if(atom.equals("null"))
				return new ESExpr.Null(BigInteger.ZERO);
			if(NULL_ATOM.matcher(atom).matches())
				return new ESExpr.Null(new BigInteger(atom.substring(4)));
			var m = FLOAT_ATOM.matcher(atom);
			if(m.matches()) {
				return buildFloatAtom(atom, m);
			}

			throw error("Invalid atom");
		}
		String n = token();
		if(FLOAT.matcher(n).matches()) {
			if(n.endsWith("f16") || n.endsWith("F16")) {
				String literal = n.substring(0, n.length() - 3);
				return new ESExpr.Float16(Float.floatToFloat16(Float.parseFloat(literal)));
			}
			else if(n.endsWith("f") || n.endsWith("F")) {
				String literal = n.substring(0, n.length() - 1);
				return new ESExpr.Float32(Float.parseFloat(literal));
			}
			else {
				String literal = n.endsWith("d") || n.endsWith("D") ? n.substring(0, n.length() - 1) : n;
				return new ESExpr.Float64(Double.parseDouble(literal));
			}
		}
		return new ESExpr.Int(integer(n));
	}

	private BigInteger integer(String s) throws SyntaxException {
		if(!INT.matcher(s).matches())
			throw error("Invalid integer");
		boolean neg = s.startsWith("-");
		if(s.startsWith("-") || s.startsWith("+"))
			s = s.substring(1);
		int radix = s.startsWith("0x") || s.startsWith("0X")
			? 16
			: s.startsWith("0b") || s.startsWith("0B") ? 2 : s.startsWith("0o") ? 8 : 10;
		var n = new BigInteger(radix == 10 ? s : s.substring(2), radix);
		return neg ? n.negate() : n;
	}

	private ESExpr array(int width) throws IOException, SyntaxException {
		return switch(width) {
			case 8 -> {
				var values = ByteLists.mutable.empty();
				arrayElements(width, n -> values.add(n.byteValue()));
				yield new ESExpr.Array8(values.toImmutable());
			}
			case 16 -> {
				var values = ShortLists.mutable.empty();
				arrayElements(width, n -> values.add(n.shortValue()));
				yield new ESExpr.Array16(values.toImmutable());
			}
			case 32 -> {
				var values = IntLists.mutable.empty();
				arrayElements(width, n -> values.add(n.intValue()));
				yield new ESExpr.Array32(values.toImmutable());
			}
			case 64 -> {
				var values = LongLists.mutable.empty();
				arrayElements(width, n -> values.add(n.longValue()));
				yield new ESExpr.Array64(values.toImmutable());
			}
			case 128 -> {
				var values = LongLists.mutable.empty();
				arrayElements(width, n -> {
					values.add(n.longValue());
					values.add(n.shiftRight(64).longValue());
				});
				yield new ESExpr.Array128(values.toImmutable());
			}
			default -> throw new AssertionError(width);
		};
	}

	private void arrayElements(int width, Consumer<BigInteger> addElement) throws IOException, SyntaxException {
		expect('[');
		for(;;) {
			ws();
			if(peek() == ']') {
				next();
				return;
			}
			var n = integer(token());
			if(n.signum() < 0 || n.bitLength() > width)
				throw error("Unsigned array element out of range");
			addElement.accept(n);
		}
	}

	private ESExpr buildFloatAtom(String atom, Matcher m) throws SyntaxException {
		String floatSize = Objects.requireNonNull(m.group("floatSize"));

		boolean isNeg = switch(m.group("sign")) {
			case null -> false;
			case "+" -> false;
			case "-" -> true;
			case String signStr -> throw new AssertionError(signStr);
		};

		if(m.group("inf") != null) {
			return switch(floatSize) {
				case "16" -> new ESExpr.Float16(isNeg ? (short)0xFC00 : (short)0x7C00);
				case "32" -> new ESExpr.Float32(isNeg ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
				case "64" -> new ESExpr.Float64(isNeg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
				default -> throw new AssertionError(floatSize);
			};
		}

		if(m.group("nan") != null) {
			int width;
			int pw;
			long exponent;
			LongFunction<ESExpr> factory;
			switch(floatSize) {
				case "16" -> {
					width = 16;
					pw = 10;
					exponent = 0x7C00L;
					factory = bits -> new ESExpr.Float16((short)bits);
				}
				case "32" -> {
					width = 32;
					pw = 23;
					exponent = 0x7F800000L;
					factory = bits -> new ESExpr.Float32(Float.intBitsToFloat((int)bits));
				}
				case "64" -> {
					width = 64;
					pw = 52;
					exponent = 0x7FF0000000000000L;
					factory = bits -> new ESExpr.Float64(Double.longBitsToDouble(bits));
				}
				default -> throw new AssertionError(floatSize);
			};

			long sign = isNeg ? 1L << (width - 1) : 0L;
			long payload = 0;

			var nanPayload = m.group("nanPayload");
			if(nanPayload == null) {
				payload = 1L << (pw - 1);
			}
			else {
				try {
					payload = Long.parseLong(nanPayload);
				}
				catch(NumberFormatException e) {
					throw error("Invalid NaN payload");
				}
				if(payload >= (1L << pw))
					throw error("Invalid NaN payload");
			}

			long bits = sign | exponent | payload;
			return factory.apply(bits);
		}

		throw new IllegalArgumentException("Invalid float atom: " + atom);
	}
}
