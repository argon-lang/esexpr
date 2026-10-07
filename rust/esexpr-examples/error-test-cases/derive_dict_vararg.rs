#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ ERROR: Dict arguments cannot be vararg values.
struct Invalid {
 #[esexpr(dict, vararg)]
 values: Vec<i32>,
}
