#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct RequiredAfterVarArg1 {
	#[esexpr(vararg)]
	pub a: Vec<i32>,

	#[esexpr(vararg)]
	pub b: Vec<f32>,

	pub c: i32,
}