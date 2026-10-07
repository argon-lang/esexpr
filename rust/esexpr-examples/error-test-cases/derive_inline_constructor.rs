#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ ERROR: Variant cannot have both inline_value and constructor.
enum Invalid {
 #[esexpr(inline_value, constructor = "value")]
 Value(i32),
}
