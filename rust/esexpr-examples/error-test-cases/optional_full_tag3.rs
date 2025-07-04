#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct OptionalNoTag3 {
	#[optional]
	pub a: Option<esexpr::ESExprStatic>,

	#[optional]
	pub b: Option<i32>,
}