#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct RequiredAfterOptionalPositional1 {
	#[optional]
	pub a: Option<i32>,

	#[optional]
	pub b: Option<f32>,

	pub c: i32,
}