#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct OptionalNoTag2 {
	#[optional]
	pub a: Option<esexpr::ESExprStatic>,

	pub b: esexpr::ESExprStatic,
}