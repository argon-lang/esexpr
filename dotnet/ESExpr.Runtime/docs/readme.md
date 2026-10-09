# ESExpr

This is the runtime library to support the ESExpr serialization format.

`ESExprTextReader` and `ESExprTextWriter` read and write the Rust ESExpr text
syntax using `TextReader` and `TextWriter`. Operations are asynchronous and accept
cancellation tokens. The caller owns the underlying reader or writer; these
classes leave it open. Use each instance sequentially. The text reader buffers
characters and yields complete expressions without loading the entire file.

```csharp
using ESExpr.Runtime;

using var input = new StringReader("(example 42 message: \"hello\")");
var reader = new ESExprTextReader(input);
using var output = new StringWriter();
var writer = new ESExprTextWriter(output, indentation: "  ");

await foreach(var expr in reader.ReadAll()) {
    await writer.Write(expr);
}
Console.WriteLine(output.ToString());
```

`Read()` reads one expression and throws `EndOfStreamException` at EOF.
`TryRead()` returns `null` at EOF, and `ReadAll()` returns an
`IAsyncEnumerable<Expr>`. Invalid or truncated text throws `SyntaxException`.
`Write()` writes one expression without a separator; `WriteAll()` accepts either
an `IEnumerable<Expr>` or an `IAsyncEnumerable<Expr>` and appends a newline after
each expression. Writes do not flush the underlying writer.

Output is compact by default. Set `indentation` to a string of spaces or tabs for
multiline constructors, or `""` for line breaks without indentation. Arrays remain
on one line, and keyword arguments are sorted by Unicode scalar value. Numbers
use culture-independent formatting. Floating-point output preserves signed zero
and NaN sign, payload, and signaling bits, using the explicit `Float16NaN`,
`Float32NaN`, and `Float64NaN` variants when decoding NaNs.

The reader supports quoted names and strings, `//` comments, decimal and
hexadecimal floats, decimal/binary/octal/hexadecimal integers, arbitrary-size
null levels, and all five unsigned array widths. Strings and names must contain
valid Unicode scalar values. As in the Rust parser, comments end at a newline,
and repeated keyword names keep the last value.
