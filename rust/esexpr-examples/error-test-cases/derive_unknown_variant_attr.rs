#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
enum Invalid {
 #[esexpr(unknown)]
 //~^ ERROR: Unknown field
 Value(i32),
}
