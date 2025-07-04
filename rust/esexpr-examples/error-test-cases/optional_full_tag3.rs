#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct OptionalNoTag3 {
	#[esexpr(optional)]
	pub a: Option<esexpr::ESExprStatic>,

	#[esexpr(optional)]
	pub b: Option<i32>,
}