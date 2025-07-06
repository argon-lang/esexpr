#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct OptionalNoTag4 {
	#[esexpr(optional)]
	pub a: Option<esexpr::ESExprStatic>,

	pub b: i32,
}