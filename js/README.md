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

## Compacting binary streams

The binary module provides `StringTableBuilder` to record string usage before
writing. It includes constructor and keyword names required by the format, and
literal-only strings used at least twice when their references save more bytes
than the table-entry cost. UTF-8 lengths and index widths determine that cost.
Selected entries use decreasing combined frequency, with Unicode scalar ordering
for ties. The heuristic excludes shared framing and effects on later indices.
`build()` retains recorded usage and returns an independent array; it filters
sorted candidates in place.

```ts
import {
  addStrings, ArrayStringPool, StringTableBuilder, writeExprs,
} from "@argon-lang/esexpr/binary_format";

const expressions = ["repeated value", "repeated value"];
const builder = new StringTableBuilder();
for (const expr of expressions) builder.record(expr);

async function* encode() {
  const pool = new ArrayStringPool();
  yield* addStrings(builder.build(), pool);
  yield* writeExprs(expressions, pool);
}
```

`addStrings(strings, pool)` emits table additions and updates the pool, skipping
duplicates and preserving existing indices. Fully consume it before using the
pool to write more expressions. It can also emit additions between expressions.
A fresh `readExprStream` reader can decode the resulting stream. Discard the pool
and stream if emission fails or is stopped partway through.

`writeExpr` and `writeExprs` use pooled literal references when no larger than
inline encoding. Table payloads always encode strings inline. A pool initialized
with strings assumes the reader already knows those entries; initialization alone
does not emit them. `writeExprs` accepts an optional shared `ArrayStringPool` as its
second argument and supports both synchronous and asynchronous expression input.
