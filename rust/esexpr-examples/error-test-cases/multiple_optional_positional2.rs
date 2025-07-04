#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
//~^ E0080
pub struct MultipleOptionalPositional2 {
	#[default_value = "1"]
	pub a: i32,

	#[default_value = "1"]
	pub b: i32,
}
