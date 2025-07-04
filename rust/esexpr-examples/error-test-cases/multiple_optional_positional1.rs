#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct MultipleOptionalPositional1 {
	#[optional]
	pub a: Option<i32>,

	#[optional]
	pub b: Option<i32>,
}