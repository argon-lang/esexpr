#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct OptionalNoTag6 {
	#[optional]
	pub a: Option<i32>,

	pub b: esexpr::ESExprStatic,
}