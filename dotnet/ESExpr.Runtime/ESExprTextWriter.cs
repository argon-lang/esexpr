using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Numerics;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ESExpr.Runtime;

/// <summary>Writes lossless, Rust-compatible ESExpr text to a TextWriter.</summary>
/// <remarks>The caller owns the underlying writer. Write operations must be sequential.</remarks>
public sealed class ESExprTextWriter {
	/// <param name="writer">The destination, which remains open after writes.</param>
	/// <param name="indentation">Null for compact output; spaces or tabs per nesting level for multiline constructors.</param>
	public ESExprTextWriter(TextWriter writer, string? indentation = null) {
		ArgumentNullException.ThrowIfNull(writer);
		if(indentation is not null && !(indentation.All(c => c == ' ') || indentation.All(c => c == '\t'))) {
			throw new ArgumentException("Indentation must contain only spaces or only tabs", nameof(indentation));
		}
		this.writer = writer;
		this.indentation = indentation;
	}

	private readonly TextWriter writer;
	private readonly string? indentation;

	private Task WriteText(string text, CancellationToken cancellationToken) {
		cancellationToken.ThrowIfCancellationRequested();
		return writer.WriteAsync(text.AsMemory(), cancellationToken);
	}

	private async Task LineBreak(int depth, CancellationToken cancellationToken) {
		await WriteText("\n", cancellationToken).ConfigureAwait(false);
		for(int i = 0; i < depth; ++i) await WriteText(indentation!, cancellationToken).ConfigureAwait(false);
	}

	private async Task Separator(int depth, CancellationToken cancellationToken) {
		if(indentation is null) await WriteText(" ", cancellationToken).ConfigureAwait(false);
		else await LineBreak(depth, cancellationToken).ConfigureAwait(false);
	}

	private Task WriteFloat(int width, ulong bits, string finite, string suffix, CancellationToken cancellationToken) {
		var (sign, exponent, payload) = ESExprTextFormat.FloatLayout(width);
		if((bits & exponent) == exponent) {
			string prefix = $"#float{width.ToString(CultureInfo.InvariantCulture)}:{((bits & sign) == 0 ? "+" : "-")}";
			return WriteText((bits & payload) == 0 ? prefix + "inf" : prefix + "nan:" + (bits & payload).ToString(CultureInfo.InvariantCulture), cancellationToken);
		}
		// .NET formatting must not erase the sign of negative zero.
		if((bits & ~sign) == 0 && (bits & sign) != 0) finite = "-0";
		if(!finite.Contains('.', StringComparison.Ordinal)) {
			int e = finite.IndexOfAny(['e', 'E']);
			finite = e >= 0 ? finite.Insert(e, ".0") : finite + ".0";
		}
		return WriteText(finite + suffix, cancellationToken);
	}

	private Task WriteNaN(int width, ulong bits, CancellationToken cancellationToken) {
		var (_, exponent, payload) = ESExprTextFormat.FloatLayout(width);
		if((bits & exponent) != exponent || (bits & payload) == 0) throw new SyntaxException("Invalid NaN bits");
		return WriteFloat(width, bits, "", "", cancellationToken);
	}

	private async Task WriteArray<T>(int width, IEnumerable<T> values, CancellationToken cancellationToken) where T : IFormattable {
		await WriteText("#u" + width.ToString(CultureInfo.InvariantCulture) + "[", cancellationToken).ConfigureAwait(false);
		bool first = true;
		foreach(var value in values) {
			if(!first) await WriteText(" ", cancellationToken).ConfigureAwait(false);
			await WriteText(value.ToString(null, CultureInfo.InvariantCulture), cancellationToken).ConfigureAwait(false);
			first = false;
		}
		await WriteText("]", cancellationToken).ConfigureAwait(false);
	}

	private async Task WriteConstructor(Expr.Constructor constructor, int depth, CancellationToken cancellationToken) {
		var (name, args, kwargs) = constructor;
		await WriteText("(" + ESExprTextFormat.PrintIdentifier(name), cancellationToken).ConfigureAwait(false);
		foreach(var arg in args) {
			await Separator(depth + 1, cancellationToken).ConfigureAwait(false);
			await WriteExpression(arg, depth + 1, cancellationToken).ConfigureAwait(false);
		}
		foreach(var key in kwargs.Keys.OrderBy(key => key, ScalarComparer.Instance)) {
			await Separator(depth + 1, cancellationToken).ConfigureAwait(false);
			await WriteText(ESExprTextFormat.PrintIdentifier(key) + ": ", cancellationToken).ConfigureAwait(false);
			await WriteExpression(kwargs[key], depth + 1, cancellationToken).ConfigureAwait(false);
		}
		if(indentation is not null && (args.Count != 0 || kwargs.Count != 0)) await LineBreak(depth, cancellationToken).ConfigureAwait(false);
		await WriteText(")", cancellationToken).ConfigureAwait(false);
	}

	private Task WriteExpression(Expr expr, int depth, CancellationToken cancellationToken) {
		cancellationToken.ThrowIfCancellationRequested();
		return expr switch {
			Expr.Constructor constructor => WriteConstructor(constructor, depth, cancellationToken),
			Expr.Bool(var value) => WriteBool(value, cancellationToken),
			Expr.Int(var value) => WriteInt(value, cancellationToken),
			Expr.Str(var value) => WriteString(value, cancellationToken),
			Expr.Float16(var value) => WriteFloat16(value, cancellationToken),
			Expr.Float32(var value) => WriteFloat32(value, cancellationToken),
			Expr.Float64(var value) => WriteFloat64(value, cancellationToken),
			Expr.Float16NaN(var bits) => WriteNaN(16, bits, cancellationToken),
			Expr.Float32NaN(var bits) => WriteNaN(32, bits, cancellationToken),
			Expr.Float64NaN(var bits) => WriteNaN(64, bits, cancellationToken),
			Expr.Array8(var values) => WriteArray8(values, cancellationToken),
			Expr.Array16(var values) => WriteArray(16, values, cancellationToken),
			Expr.Array32(var values) => WriteArray(32, values, cancellationToken),
			Expr.Array64(var values) => WriteArray(64, values, cancellationToken),
			Expr.Array128(var values) => WriteArray(128, values, cancellationToken),
			Expr.Null(var level) => WriteNull(level, cancellationToken),
		};
	}

	private Task WriteBool(bool value, CancellationToken cancellationToken) =>
		WriteText(value ? "#true" : "#false", cancellationToken);

	private Task WriteInt(BigInteger value, CancellationToken cancellationToken) =>
		WriteText(value.ToString(CultureInfo.InvariantCulture), cancellationToken);

	private Task WriteString(string value, CancellationToken cancellationToken) =>
		WriteText(ESExprTextFormat.Quote(value, '"'), cancellationToken);

	private Task WriteFloat16(Half value, CancellationToken cancellationToken) =>
		WriteFloat(16, BitConverter.HalfToUInt16Bits(value), ((float)value).ToString("R", CultureInfo.InvariantCulture), "f16", cancellationToken);

	private Task WriteFloat32(float value, CancellationToken cancellationToken) =>
		WriteFloat(32, BitConverter.SingleToUInt32Bits(value), value.ToString("R", CultureInfo.InvariantCulture), "f", cancellationToken);

	private Task WriteFloat64(double value, CancellationToken cancellationToken) =>
		WriteFloat(64, BitConverter.DoubleToUInt64Bits(value), value.ToString("R", CultureInfo.InvariantCulture), "d", cancellationToken);

	private async Task WriteArray8(ImmutableArray<byte> values, CancellationToken cancellationToken) {
		await WriteText("#\"", cancellationToken).ConfigureAwait(false);
		foreach(byte value in values) await WriteText(value.ToString("x2", CultureInfo.InvariantCulture), cancellationToken).ConfigureAwait(false);
		await WriteText("\"", cancellationToken).ConfigureAwait(false);
	}

	private Task WriteNull(BigInteger level, CancellationToken cancellationToken) {
		if(level.Sign < 0) throw new SyntaxException("Negative null level");
		return WriteText(level.IsZero ? "#null" : "#null" + level.ToString(CultureInfo.InvariantCulture), cancellationToken);
	}

	/// <summary>Write one expression without a trailing separator.</summary>
	public Task Write(Expr expr, CancellationToken cancellationToken = default) {
		ArgumentNullException.ThrowIfNull(expr);
		return WriteExpression(expr, 0, cancellationToken);
	}

	/// <summary>Write a stream of expressions with a newline after each expression.</summary>
	public async Task WriteAll(IAsyncEnumerable<Expr> exprs, CancellationToken cancellationToken = default) {
		ArgumentNullException.ThrowIfNull(exprs);
		cancellationToken.ThrowIfCancellationRequested();
		await foreach(var expr in exprs.WithCancellation(cancellationToken).ConfigureAwait(false)) {
			await Write(expr, cancellationToken).ConfigureAwait(false);
			await WriteText("\n", cancellationToken).ConfigureAwait(false);
		}
	}

	/// <summary>Write expressions with a newline after each expression.</summary>
	public async Task WriteAll(IEnumerable<Expr> exprs, CancellationToken cancellationToken = default) {
		ArgumentNullException.ThrowIfNull(exprs);
		cancellationToken.ThrowIfCancellationRequested();
		foreach(var expr in exprs) {
			await Write(expr, cancellationToken).ConfigureAwait(false);
			await WriteText("\n", cancellationToken).ConfigureAwait(false);
		}
	}

	// Rust sorts by Unicode scalar value, not by culture or UTF-16 code units.
	private sealed class ScalarComparer : IComparer<string> {
		internal static readonly ScalarComparer Instance = new();
		public int Compare(string? x, string? y) {
			if(x is null) return y is null ? 0 : -1;
			if(y is null) return 1;
			var left = x.EnumerateRunes().GetEnumerator();
			var right = y.EnumerateRunes().GetEnumerator();
			while(left.MoveNext()) {
				if(!right.MoveNext()) return 1;
				int diff = left.Current.Value.CompareTo(right.Current.Value);
				if(diff != 0) return diff;
			}
			return right.MoveNext() ? -1 : 0;
		}
	}
}
