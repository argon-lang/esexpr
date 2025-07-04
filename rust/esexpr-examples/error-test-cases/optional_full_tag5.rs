#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct OptionalNoTag5 {
	#[optional]
	pub a: Option<i32>,

	#[optional]
	pub b: Option<esexpr::ESExprStatic>,
}