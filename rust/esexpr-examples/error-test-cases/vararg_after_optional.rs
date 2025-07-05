#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct VarArgAfterOptional {
	#[esexpr(optional)]
	pub a: Option<i32>,

	#[esexpr(vararg)]
	pub b: Vec<i32>,
}