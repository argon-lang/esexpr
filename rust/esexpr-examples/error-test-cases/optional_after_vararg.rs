#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct OptionalAfterVarArg {
	#[esexpr(vararg)]
	pub a: Vec<i32>,

	#[esexpr(optional)]
	pub b: Option<i32>,
}