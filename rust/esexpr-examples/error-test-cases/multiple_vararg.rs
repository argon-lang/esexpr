#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct MultipleVararg {
	#[esexpr(vararg)]
	pub a: Vec<i32>,

	#[esexpr(vararg)]
	pub b: Vec<i32>,
}