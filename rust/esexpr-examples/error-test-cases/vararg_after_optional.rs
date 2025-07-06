#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct VarArgAfterOptional {
	#[esexpr(optional)]
	pub a: Option<i32>,

	#[esexpr(vararg)]
	pub b: Vec<i32>,
}