
# ESExpr Text Format

This crate contains the text readers and writers for the ESExpr serialzation format.

`parse` and `parse_multi` read one or several expressions. `format_as_string` produces a
compact string. `ExprPrinter` configures formatting through its `format_as_string` and
`write` methods; `write` accepts a `core::fmt::Write` sink. Keyword arguments are
sorted by name. Set `indentation` to `Indentation::Spaces(2)` for multiline constructors with
two spaces per nesting level, or `Indentation::Tabs(2)` for two tabs per level. The default, `Indentation::Compact`, prints compact text; arrays
remain on a single line.

```rust
let expr = esexpr_text::parse("(example 1 value: #true)").unwrap();
let text = esexpr_text::format_as_string(&expr);
assert_eq!(esexpr_text::parse(&text).unwrap(), expr);
```

## Representable values

Every current `ESExpr` variant can be represented without loss: arbitrary-size
integers and null levels, Unicode strings, arbitrary constructor and keyword
names (including empty names via `''`), all five unsigned array widths, and
16-, 32-, and 64-bit floats. Float printing preserves signed zero and NaN sign,
payload, and signaling bits. Finite decimal literals include a decimal point
and a width suffix; special values use `#floatN:+inf`, `#floatN:-inf`, or
`#floatN:±nan:payload`.

The quoted-string parser previously rejected empty strings and identifiers;
this is now fixed. No remaining value-representation gaps are known. Printing
does not preserve source comments, whitespace, numeric spelling, or the source
order of keyword arguments. Repeated keyword names collapse to the last value
when parsed, as the expression model stores a map.
