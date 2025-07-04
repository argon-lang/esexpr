#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct OptionalNoTag4 {
	#[optional]
	pub a: Option<esexpr::ESExprStatic>,

	pub b: i32,
}