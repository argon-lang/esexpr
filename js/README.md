# ESExpr JavaScript library

The `@argon-lang/esexpr/text_format` module reads and writes the Rust ESExpr text
syntax. Readers consume `AsyncIterable<string>` and writers produce
`AsyncIterable<string>`. Chunks can split tokens, escapes, or Unicode surrogate
pairs. The reader yields complete expressions without buffering the entire file.

```ts
import { readExprStream, writeExprs } from "@argon-lang/esexpr/text_format";

async function* input(): AsyncIterable<string> {
  yield '(example 42 message: "hello")';
}

const expressions = await Array.fromAsync(readExprStream(input()));
for await (const chunk of writeExprs(expressions, { indentation: "  " })) {
  process.stdout.write(chunk);
}
```

`ExprReader` provides `readExpr()`, `tryReadExpr()` (returns `undefined` at EOF),
and `readAll()`. `writeExpr()` writes one expression; `writeExprs()` accepts
synchronous or asynchronous expression iterables and adds a newline after each
expression. Invalid text throws the module's `ESExprFormatError`.

Formatting is compact by default. Set `indentation` to a string of spaces or
tabs for multiline constructors, or `""` for line breaks without indentation.
Arrays stay on one line and keyword names are sorted by Unicode scalar value.
Finite floats include a decimal point and width suffix; NaN representations
preserve sign, payload, and signaling bits. Array128 values use the same
little-endian byte representation as the binary module. Strings and names must
contain valid Unicode, and comments end with a newline, as in the Rust parser.
