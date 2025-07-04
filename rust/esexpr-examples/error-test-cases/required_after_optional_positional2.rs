#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct RequiredAfterOptionalPositional2 {
	#[esexpr(optional)]
	pub a: Option<i32>,

	#[esexpr(optional)]
	pub b: Option<f32>,

	pub c: f32,
}
