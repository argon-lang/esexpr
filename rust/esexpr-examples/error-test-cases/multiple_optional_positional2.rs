#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct MultipleOptionalPositional2 {
	#[esexpr(default_value = 1)]
	pub a: i32,

	#[esexpr(default_value = 1)]
	pub b: i32,
}
