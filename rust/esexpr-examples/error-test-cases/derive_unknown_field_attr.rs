#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
struct Invalid {
 #[esexpr(unknown)]
 //~^ ERROR: Unknown field
 value: i32,
}
