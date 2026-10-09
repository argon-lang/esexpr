using System;
using System.Globalization;
using System.Numerics;
using System.Text;
using System.Text.RegularExpressions;

namespace ESExpr.Runtime;

// Numeric and identifier syntax shared by the streaming reader and writer.
internal static partial class ESExprTextFormat {
	[GeneratedRegex(@"\A[a-z][a-z0-9]*(?:-[a-z0-9]+)*\z", RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture, 1000)]
	internal static partial Regex Identifier();

	[GeneratedRegex(@"\A(?<sign>[+-]?)(?<digits>0[xX][0-9a-fA-F]+|0[bB][01]+|0o[0-7]+|[0-9]+)\z", RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture, 1000)]
	private static partial Regex Integer();

	[GeneratedRegex(@"\A[+-]?[0-9]+\.[0-9]+(?:[eE][+-]?[0-9]+)?(?<suffix>f16|F16|f|F|d|D)?\z", RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture, 1000)]
	private static partial Regex DecimalFloat();

	[GeneratedRegex(@"\A(?<sign>[+-]?)0[xX](?<whole>[0-9a-fA-F]+)\.(?<fraction>[0-9a-fA-F]+)[pP](?<exponent>[+-]?[0-9]+)(?<suffix>f16|F16|f|F|d|D)?\z", RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture, 1000)]
	private static partial Regex HexFloat();

	[GeneratedRegex(@"\A#float(?<width>16|32|64):(?<sign>[+-]?)nan(?::(?<payload>[0-9]+))?\z", RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture, 1000)]
	private static partial Regex NaN();

	[GeneratedRegex(@"\A#float(?<width>16|32|64):(?<sign>[+-])inf\z", RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture, 1000)]
	private static partial Regex Infinity();

	[GeneratedRegex(@"\A#null(?<level>[0-9]*)\z", RegexOptions.CultureInvariant | RegexOptions.ExplicitCapture, 1000)]
	private static partial Regex Null();

	internal static (ulong Sign, ulong Exponent, ulong Payload) FloatLayout(int width) => width switch {
		16 => (0x8000, 0x7C00, 0x3FF),
		32 => (0x80000000, 0x7F800000, 0x7FFFFF),
		64 => (0x8000000000000000, 0x7FF0000000000000, 0xFFFFFFFFFFFFF),
		_ => throw new ArgumentOutOfRangeException(nameof(width)),
	};

	internal static Expr FloatFromBits(int width, ulong bits) => width switch {
		16 => Half.IsNaN(BitConverter.UInt16BitsToHalf((ushort)bits))
			? new Expr.Float16NaN((ushort)bits) : new Expr.Float16(BitConverter.UInt16BitsToHalf((ushort)bits)),
		32 => float.IsNaN(BitConverter.UInt32BitsToSingle((uint)bits))
			? new Expr.Float32NaN((uint)bits) : new Expr.Float32(BitConverter.UInt32BitsToSingle((uint)bits)),
		64 => double.IsNaN(BitConverter.UInt64BitsToDouble(bits))
			? new Expr.Float64NaN(bits) : new Expr.Float64(BitConverter.UInt64BitsToDouble(bits)),
		_ => throw new ArgumentOutOfRangeException(nameof(width)),
	};

	internal static bool TryInteger(string token, out BigInteger value) {
		value = BigInteger.Zero;
		var match = Integer().Match(token);
		if(!match.Success) return false;
		string digits = match.Groups["digits"].Value;
		int radix = 10;
		if(digits.Length > 2 && digits[0] == '0') {
			radix = digits[1] switch { 'x' or 'X' => 16, 'b' or 'B' => 2, 'o' => 8, _ => 10 };
			if(radix != 10) digits = digits[2..];
		}
		foreach(char digit in digits) value = value * radix + HexDigit(digit);
		if(string.Equals(match.Groups["sign"].Value, "-", StringComparison.Ordinal)) value = -value;
		return true;
	}

	internal static int HexDigit(int digit) => digit switch {
		>= '0' and <= '9' => digit - '0',
		>= 'a' and <= 'f' => digit - 'a' + 10,
		>= 'A' and <= 'F' => digit - 'A' + 10,
		_ => -1,
	};

	internal static Expr ParseAtom(string token) {
		if(string.Equals(token, "#true", StringComparison.Ordinal)) return new Expr.Bool(true);
		if(string.Equals(token, "#false", StringComparison.Ordinal)) return new Expr.Bool(false);
		var match = Null().Match(token);
		if(match.Success) return new Expr.Null(match.Groups["level"].Length == 0 ? BigInteger.Zero : BigInteger.Parse(match.Groups["level"].Value, CultureInfo.InvariantCulture));
		match = Infinity().Match(token);
		if(match.Success) {
			int width = int.Parse(match.Groups["width"].Value, CultureInfo.InvariantCulture);
			return FloatFromBits(width, FloatLayout(width).Exponent | (string.Equals(match.Groups["sign"].Value, "-", StringComparison.Ordinal) ? FloatLayout(width).Sign : 0));
		}
		match = NaN().Match(token);
		if(match.Success) {
			int width = int.Parse(match.Groups["width"].Value, CultureInfo.InvariantCulture);
			var (sign, exponent, mask) = FloatLayout(width);
			var payload = match.Groups["payload"].Success ? BigInteger.Parse(match.Groups["payload"].Value, CultureInfo.InvariantCulture) : (mask + 1) >> 1;
			if(payload > mask) throw new SyntaxException("Invalid NaN payload");
			return FloatFromBits(width, exponent | (ulong)payload | (string.Equals(match.Groups["sign"].Value, "-", StringComparison.Ordinal) ? sign : 0));
		}
		match = DecimalFloat().Match(token);
		if(match.Success) {
			string suffix = match.Groups["suffix"].Value.ToLowerInvariant();
			string literal = suffix.Length == 0 ? token : token[..^suffix.Length];
			return suffix switch {
				// Rust's half crate parses through f32, rather than directly to f16.
				"f16" => new Expr.Float16((Half)float.Parse(literal, NumberStyles.Float, CultureInfo.InvariantCulture)),
				"f" => new Expr.Float32(float.Parse(literal, NumberStyles.Float, CultureInfo.InvariantCulture)),
				_ => new Expr.Float64(double.Parse(literal, NumberStyles.Float, CultureInfo.InvariantCulture)),
			};
		}
		match = HexFloat().Match(token);
		if(match.Success) {
			string suffix = match.Groups["suffix"].Value.ToLowerInvariant();
			bool single = suffix is "f" or "f16";
			string digits = match.Groups["whole"].Value + match.Groups["fraction"].Value;
			var significand = BigInteger.Parse("0" + digits, NumberStyles.AllowHexSpecifier, CultureInfo.InvariantCulture);
			var exponent = BigInteger.Parse(match.Groups["exponent"].Value, CultureInfo.InvariantCulture) - 4 * match.Groups["fraction"].Length;
			double value = ParseHexFloat(significand, exponent, single, string.Equals(match.Groups["sign"].Value, "-", StringComparison.Ordinal));
			return suffix switch {
				"f16" => new Expr.Float16((Half)(float)value),
				"f" => new Expr.Float32((float)value),
				_ => new Expr.Float64(value),
			};
		}
		if(TryInteger(token, out var integer)) return new Expr.Int(integer);
		throw new SyntaxException("Invalid expression: " + token);
	}

	// Round a binary rational directly to the target width, preserving subnormals
	// and ties to even without overflowing an intermediate floating-point value.
	private static double ParseHexFloat(BigInteger significand, BigInteger exponent, bool single, bool negative) {
		int precision = single ? 24 : 53;
		int maxExponent = single ? 127 : 1023;
		int minExponent = 1 - maxExponent;
		var leadingExponent = significand.GetBitLength() - 1 + exponent;
		if(significand.IsZero || leadingExponent < minExponent - precision) return negative ? -0.0 : 0.0;
		if(leadingExponent > maxExponent) return negative ? double.NegativeInfinity : double.PositiveInfinity;
		int unitExponent = (int)BigInteger.Max(leadingExponent, minExponent) - precision + 1;
		var shift = unitExponent - exponent;
		if(shift > 0) {
			// A positive shift cannot exceed the length of the input significand by
			// more than one bit here; extreme exponents were handled above.
			int count = checked((int)shift);
			var truncated = significand >> count;
			var remainder = significand - (truncated << count);
			var half = BigInteger.One << (count - 1);
			significand = truncated + (remainder > half || (remainder == half && !truncated.IsEven) ? 1 : 0);
		}
		else significand <<= checked((int)-shift);
		double value = Math.ScaleB((double)significand, unitExponent);
		if(single) value = (float)value;
		return negative ? -value : value;
	}

	internal static void ValidateUnicode(string value) {
		for(int i = 0; i < value.Length; ++i) {
			if(char.IsHighSurrogate(value[i])) {
				if(i + 1 >= value.Length || !char.IsLowSurrogate(value[++i])) throw new SyntaxException("Lone surrogate in string");
			}
			else if(char.IsLowSurrogate(value[i])) throw new SyntaxException("Lone surrogate in string");
		}
	}

	internal static string Quote(string value, char quote) {
		ValidateUnicode(value);
		var output = new StringBuilder().Append(quote);
		foreach(char c in value) {
			if(c == quote || c == '\\') output.Append('\\').Append(c);
			else if(c == '\n') output.Append("\\n");
			else if(c == '\r') output.Append("\\r");
			else if(c == '\t') output.Append("\\t");
			else if(c == '\f') output.Append("\\f");
			else if(char.IsControl(c)) output.Append("\\u{").Append(((int)c).ToString("x", CultureInfo.InvariantCulture)).Append('}');
			else output.Append(c);
		}
		return output.Append(quote).ToString();
	}

	internal static string PrintIdentifier(string name) => Identifier().IsMatch(name) ? name : Quote(name, '\'');
}
