#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct OptionalNoTag5 {
	#[esexpr(optional)]
	pub a: Option<i32>,

	#[esexpr(optional)]
	pub b: Option<esexpr::ESExprStatic>,
}