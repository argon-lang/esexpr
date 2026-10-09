
# `ESExpr` Binary Format

This crate contains the binary readers and writers for the `ESExpr` serialization format.

## Compacting a stream

Record the expressions before writing them to select and order a string table:

```rust
use esexpr::ESExpr;
use esexpr_binary::{ExprGenerator, ExprGeneratorSync, StringTableBuilder};

let expressions = vec![ESExpr::Str("repeated value".into()); 2];
let mut table = StringTableBuilder::new();
for expr in &expressions {
    table.record(expr);
}

let mut bytes = Vec::new();
let mut generator = ExprGenerator::new(&mut bytes);
generator.add_strings(table.build())?;
for expr in &expressions {
    generator.generate(expr)?;
}
# Ok::<(), esexpr_binary::GeneratorError<core::convert::Infallible>>(())
```

`build` consumes the builder and moves its strings into a table. Required
constructor and keyword entries are always included. Literal-only strings must
occur at least twice and save more bytes than their table-entry cost, accounting
for UTF-8 length and index width. Entries are ordered by decreasing combined
frequency, with lexical ordering for ties. This heuristic excludes shared table
framing and the effect of an entry on later indices.

`add_strings` emits the table into the stream, so a fresh `parse_sync` or
`parse_async` reader can decode it. It skips duplicates and preserves existing
indices. It can also add entries between expressions. The corresponding method
on `ExprGeneratorAsync` is awaited. Table payloads are written inline; subsequent
literals use table references when those are no larger than inline encoding.
Discard the generator and stream if writing an addition fails.

`new_with_string_pool` instead assumes the reader already knows the supplied
pool, and does not emit it. Use it with the corresponding
`parse_existing_string_pool_sync` or `parse_existing_string_pool_async` API.
