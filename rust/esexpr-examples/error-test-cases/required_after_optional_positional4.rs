#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct RequiredAfterOptionalPositional4 {
	#[esexpr(default_value = 1)]
	pub a: i32,

	#[esexpr(default_value = 1.0)]
	pub b: f32,

	pub c: f32,
}