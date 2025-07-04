#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
struct ConstructorName123Conversion {
	a: i32,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
enum ConstructorNameEnum {
	MyName123Test,

	#[constructor = "my-ctor"]
	CustomName,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
#[constructor = "my-ctor"]
struct CustomConstructorName {
	a: i32,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
enum InlineValueTest {
	#[inline_value]
	Flag(bool),

	NormalCase(bool),
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
#[constructor = "optional-args"]
struct PositionalArgsOptional1(bool, bool, #[optional] Option<bool>);

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
#[constructor = "keywords"]
struct KeywordStruct {
	#[keyword]
	a: bool,

	#[keyword = "b2"]
	b: bool,

	#[keyword = "c2"]
	#[optional]
	c: Option<bool>,

	#[keyword]
	#[optional]
	d: Option<bool>,

	#[keyword]
	#[default_value = "false"]
	e: bool,

	#[keyword]
	f: Option<bool>,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
enum KeywordEnum {
	#[constructor = "keywords"]
	Value {
		#[keyword]
		a: bool,

		#[keyword = "b2"]
		b: bool,

		#[keyword = "c2"]
		#[optional]
		c: Option<bool>,

		#[keyword]
		#[optional]
		d: Option<bool>,

		#[keyword]
		#[default_value = "false"]
		e: bool,

		#[keyword]
		f: Option<bool>,
	},
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
#[simple_enum]
enum SimpleEnum {
	A,
	B,
	#[constructor = "my-c"]
	C,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
#[constructor = "many"]
struct ManyArgsStruct {
	#[vararg]
	args: alloc::vec::Vec<bool>,

	#[dict]
	kwargs: alloc::collections::BTreeMap<alloc::string::String, bool>,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq, Debug, Clone)]
enum ManyArgsEnum {
	#[constructor = "many"]
	Value {
		#[vararg]
		args: alloc::vec::Vec<bool>,

		#[dict]
		kwargs: alloc::collections::BTreeMap<alloc::string::String, bool>,
	},
}

#[derive(esexpr::ESExprCodec, Clone)]
struct GenericTest<A>(A);

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
pub struct MultipleOptionalPositional1 {
	#[optional]
	pub a: Option<i32>,

	#[optional]
	pub b: Option<f32>,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
pub struct RequiredAfterOptionalPositional1 {
	#[optional]
	pub a: Option<i32>,

	#[optional]
	pub b: Option<f32>,

	pub c: alloc::string::String,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
pub struct RequiredAfterOptionalPositional2 {
	#[optional]
	pub a: Option<i32>,

	#[optional]
	pub b: Option<f32>,

	pub c: alloc::string::String,

	pub d: i32,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
pub struct RequiredAfterOptionalPositional3 {
	#[default_value = "4"]
	pub a: i32,

	#[default_value = "4.0"]
	pub b: f32,

	pub c: alloc::string::String,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
pub struct RequiredAfterOptionalPositional4 {
	#[default_value = "4"]
	pub a: i32,

	#[default_value = "4.0"]
	pub b: f32,

	pub c: alloc::string::String,

	pub d: i32,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
pub struct OptionalNoTag1 {
	#[optional]
	pub a: Option<esexpr::ESExprStatic>,
}

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
struct VarArgAfterOptional(#[optional] Option<u32>, #[vararg] alloc::vec::Vec<f32>);

#[derive(esexpr::ESExprCodec, esexpr::ValueEq)]
pub struct AnyExpr {
	pub a: esexpr::ESExprStatic,
}

#[cfg(test)]
mod tests {
	use alloc::borrow::ToOwned;
	use alloc::collections::BTreeMap;
	use alloc::vec;

	use esexpr::cowstr::CowStr;
	use esexpr::{ESExprCodec, ESExprTag, ESExprTagCollection, ValueEq, esexpr};

	use super::*;

	#[test]
	fn constructor_name_conversion() {
		let expr = esexpr! {
			("constructor-name123-conversion" 5)
		};

		let value = ConstructorName123Conversion { a: 5 };

		assert_eq!(
			ESExprTagCollection::Tags(&[ESExprTag::Constructor(CowStr::Static("constructor-name123-conversion"))]),
			ConstructorName123Conversion::TAGS
		);
		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&ConstructorName123Conversion::decode_esexpr(expr).unwrap()
		));

		let expr = esexpr! { ("bad-name" 5) };

		assert!(ConstructorName123Conversion::decode_esexpr(expr).is_err());

		let expr = esexpr! { ("my-name123-test") };

		let value = ConstructorNameEnum::MyName123Test;

		assert_eq!(
			ESExprTagCollection::Tags(&[
				ESExprTag::Constructor(CowStr::Static("my-name123-test")),
				ESExprTag::Constructor(CowStr::Static("my-ctor")),
			]),
			ConstructorNameEnum::TAGS
		);
		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&ConstructorNameEnum::decode_esexpr(expr).unwrap()
		));

		let expr = esexpr! { ("bad-name") };

		assert!(ConstructorNameEnum::decode_esexpr(expr).is_err());
	}

	#[test]
	fn custom_constructor_name() {
		let expr = esexpr! { ("my-ctor" 5) };

		let value = CustomConstructorName { a: 5 };

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&CustomConstructorName::decode_esexpr(expr).unwrap()
		));

		let expr = esexpr! { ("bad-name" 5) };

		assert!(ConstructorName123Conversion::decode_esexpr(expr).is_err());

		let expr = esexpr! { ("my-ctor") };

		let value = ConstructorNameEnum::CustomName;

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&ConstructorNameEnum::decode_esexpr(expr).unwrap()
		));
	}

	#[test]
	fn inline_value_case() {
		let expr = esexpr!(true);
		let value = InlineValueTest::Flag(true);

		assert_eq!(
			ESExprTagCollection::Tags(&[ESExprTag::Bool, ESExprTag::Constructor(CowStr::Static("normal-case"))]),
			InlineValueTest::TAGS
		);
		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&InlineValueTest::decode_esexpr(expr).unwrap()
		));

		let expr = esexpr! { (flag true) };

		assert!(InlineValueTest::decode_esexpr(expr).is_err());

		let expr = esexpr! { ("normal-case" true) };
		let value = InlineValueTest::NormalCase(true);

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&InlineValueTest::decode_esexpr(expr).unwrap()
		));
	}

	#[test]
	fn positional_optional_args() {
		let expr2 = esexpr! { ("optional-args" true false) };
		let expr3 = esexpr! { ("optional-args" true false true) };

		let value = PositionalArgsOptional1(true, false, None);
		assert_eq!(expr2, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&PositionalArgsOptional1::decode_esexpr(expr2.clone()).unwrap()
		));

		let value = PositionalArgsOptional1(true, false, Some(true));
		assert_eq!(expr3, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&PositionalArgsOptional1::decode_esexpr(expr3.clone()).unwrap()
		));
	}

	#[test]
	fn keyword_args() {
		let expr = esexpr! { (keywords a: true b2: true c2: true d: true e: true f: true) };

		let value = KeywordStruct {
			a: true,
			b: true,
			c: Some(true),
			d: Some(true),
			e: true,
			f: Some(true),
		};

		let tags = ESExprTagCollection::Tags(&[ESExprTag::Constructor(CowStr::Static("keywords"))]);

		assert_eq!(tags, KeywordStruct::TAGS);
		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&KeywordStruct::decode_esexpr(expr.clone()).unwrap()
		));

		let value = KeywordEnum::Value {
			a: true,
			b: true,
			c: Some(true),
			d: Some(true),
			e: true,
			f: Some(true),
		};

		assert_eq!(tags, KeywordEnum::TAGS);
		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(&value, &KeywordEnum::decode_esexpr(expr).unwrap()));

		let expr = esexpr! { (keywords a: true b2: true f: #null) };

		let value = KeywordStruct {
			a: true,
			b: true,
			c: None,
			d: None,
			e: false,
			f: None,
		};

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&KeywordStruct::decode_esexpr(expr.clone()).unwrap()
		));

		let value = KeywordEnum::Value {
			a: true,
			b: true,
			c: None,
			d: None,
			e: false,
			f: None,
		};

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(&value, &KeywordEnum::decode_esexpr(expr).unwrap()));

		let expr = esexpr! {
			(keywords a: true b2: true)
		};

		assert!(KeywordEnum::decode_esexpr(expr).is_err());
	}

	#[test]
	fn simple_enum_test() {
		let expr = esexpr!("a");
		let value = SimpleEnum::A;

		assert_eq!(ESExprTagCollection::Tags(&[ESExprTag::Str]), SimpleEnum::TAGS);
		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(&value, &SimpleEnum::decode_esexpr(expr).unwrap()));

		let expr = esexpr!("b");
		let value = SimpleEnum::B;

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(&value, &SimpleEnum::decode_esexpr(expr).unwrap()));

		let expr = esexpr!("my-c");
		let value = SimpleEnum::C;

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(&value, &SimpleEnum::decode_esexpr(expr).unwrap()));

		let expr = esexpr!("d");

		assert!(ConstructorNameEnum::decode_esexpr(expr).is_err());
	}

	#[test]
	fn many_args_test() {
		let expr = esexpr! {
			(many true true false a: true b: true z: false)
		};
		let value = ManyArgsStruct {
			args: vec![true, true, false],
			kwargs: BTreeMap::from([("a".to_owned(), true), ("b".to_owned(), true), ("z".to_owned(), false)]),
		};

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(
			&value,
			&ManyArgsStruct::decode_esexpr(expr.clone()).unwrap()
		));

		let value = ManyArgsEnum::Value {
			args: vec![true, true, false],
			kwargs: BTreeMap::from([("a".to_owned(), true), ("b".to_owned(), true), ("z".to_owned(), false)]),
		};

		assert_eq!(expr, value.encode_esexpr());
		assert!(ValueEq::value_eq(&value, &ManyArgsEnum::decode_esexpr(expr).unwrap()));
	}

	#[test]
	fn generic_tests() {
		let expr = esexpr! {
			("generic-test" 5)
		};

		assert_eq!(expr, GenericTest(5).encode_esexpr());
		assert_eq!(5, GenericTest::<i32>::decode_esexpr(expr).unwrap().0);
	}
}
