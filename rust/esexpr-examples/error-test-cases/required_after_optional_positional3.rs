#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct RequiredAfterOptionalPositional3 {
	#[default_value = "1"]
	pub a: i32,

	#[default_value = "1.0"]
	pub b: f32,

	pub c: i32,
}
