#[derive(esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
//~^ E0080
pub struct OptionalNoTag1 {
	#[esexpr(optional)]
	pub a: Option<esexpr::ESExprStatic>,

	#[esexpr(optional)]
	pub b: Option<esexpr::ESExprStatic>,
}