#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct RequiredAfterOptionalPositional2 {
	#[optional]
	pub a: Option<i32>,

	#[optional]
	pub b: Option<f32>,

	pub c: f32,
}
