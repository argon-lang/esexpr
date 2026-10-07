#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ ERROR: Duplicate simple enum case name: same
#[esexpr(simple_enum)]
enum Invalid {
 #[esexpr(constructor = "same")]
 First,
 #[esexpr(constructor = "same")]
 Second,
}
