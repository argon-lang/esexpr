#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
#[esexpr(unknown)]
//~^ ERROR: Unknown field
struct Invalid;
