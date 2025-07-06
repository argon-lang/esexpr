#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct OptionalNoTag2 {
	#[esexpr(optional)]
	pub a: Option<esexpr::ESExprStatic>,

	pub b: esexpr::ESExprStatic,
}