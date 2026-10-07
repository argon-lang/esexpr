#![expect(missing_docs, reason = "Integration tests")]

use std::borrow::Cow;
use std::collections::{BTreeMap, BTreeSet, VecDeque};
use std::fmt::Debug;

use esexpr::cowstr::CowStr;
use esexpr::{
	DecodeError,
	DecodeErrorPath,
	DecodeErrorType,
	Dictionary,
	ESExpr,
	ESExprCodec,
	ESExprDictCodec,
	ESExprEncodedEq,
	ESExprOptionalFieldCodec,
	ESExprTag,
	ESExprVarArgCodec,
};
use half::f16;
use num_bigint::{BigInt, BigUint};

fn round_trip<'a, T>(value: &'a T)
where
	T: ESExprCodec<'a> + Debug,
{
	let decoded = T::decode_esexpr(value.encode_esexpr()).expect("valid value should decode");
	assert!(value.is_encoded_eq(&decoded), "{value:?} != {decoded:?}");
}

fn unexpected<T>(expr: ESExpr<'static>)
where
	T: ESExprCodec<'static> + Debug,
{
	let tag = expr.tag().into_owned();
	let error = T::decode_esexpr(expr).expect_err("wrong tag must fail");
	assert!(
		matches!(error.error_type(), DecodeErrorType::UnexpectedExpr { expected_tags, actual_tag } if *expected_tags == T::TAGS && *actual_tag == tag)
	);
	assert!(matches!(error.error_path(), DecodeErrorPath::Current));
}

fn out_of_range(error: &DecodeError) {
	assert!(matches!(error.error_type(), DecodeErrorType::OutOfRange(_)));
	assert!(matches!(error.error_path(), DecodeErrorPath::Current));
}

macro_rules! integer_tests {
    ($($name:ident: $ty:ty),* $(,)?) => {$ (
        #[test]
        fn $name() {
            for value in [<$ty>::MIN, 0, 1, <$ty>::MAX] { round_trip(&value); }
            for value in [BigInt::from(<$ty>::MIN) - 1, BigInt::from(<$ty>::MAX) + 1] {
                out_of_range(&<$ty>::decode_esexpr(ESExpr::Int(Cow::Owned(value))).expect_err("overflow must fail"));
            }
            unexpected::<$ty>(ESExpr::Bool(false));
        }
    )*};
}

integer_tests! {
	i8_boundaries: i8, u8_boundaries: u8, i16_boundaries: i16, u16_boundaries: u16,
	i32_boundaries: i32, u32_boundaries: u32, i64_boundaries: i64, u64_boundaries: u64,
	i128_boundaries: i128, u128_boundaries: u128, isize_boundaries: isize, usize_boundaries: usize,
}

#[test]
fn bigint_bool_unit_and_string_codecs() {
	let huge = BigInt::from(1u8) << 256usize;
	round_trip(&huge);
	round_trip(&-huge);
	round_trip(&(BigUint::from(1u8) << 256));
	out_of_range(&BigUint::decode_esexpr((-1i32).encode_esexpr()).expect_err("unsigned must reject negative"));
	unexpected::<BigInt>(ESExpr::Bool(true));
	unexpected::<BigUint>(ESExpr::Bool(true));
	round_trip(&false);
	round_trip(&true);
	unexpected::<bool>(ESExpr::Str("true".into()));
	round_trip(&());
	unexpected::<()>(ESExpr::Null(Cow::Owned(BigUint::from(1u8))));
	unexpected::<()>(ESExpr::Bool(false));
	for s in ["", "héllo\0world"] {
		round_trip(&s.to_owned());
		round_trip(&Cow::<'_, str>::Borrowed(s));
		round_trip(&Cow::<'_, str>::Owned(s.to_owned()));
		round_trip(&CowStr::Borrowed(s));
		round_trip(&CowStr::Static(s));
		round_trip(&CowStr::Owned(s.to_owned()));
	}
	unexpected::<String>(ESExpr::Bool(true));
	unexpected::<Cow<'_, str>>(ESExpr::Bool(true));
	unexpected::<CowStr<'_>>(ESExpr::Bool(true));
}

#[test]
fn floats_preserve_bits_and_reject_other_tags() {
	macro_rules! check {
		($ty:ty, $nan:expr) => {{
			for value in [
				<$ty>::ZERO,
				-<$ty>::ZERO,
				<$ty>::INFINITY,
				<$ty>::NEG_INFINITY,
				<$ty>::from_bits($nan),
			] {
				round_trip(&value);
			}
			assert!(!<$ty>::ZERO.is_encoded_eq(&-<$ty>::ZERO));
			assert!(<$ty>::from_bits($nan).is_encoded_eq(&<$ty>::from_bits($nan)));
			assert!(!<$ty>::from_bits($nan).is_encoded_eq(&<$ty>::from_bits($nan + 1)));
			unexpected::<$ty>(ESExpr::Bool(false));
		}};
	}
	check!(f16, 0x7E01);
	// Primitive floats do not expose ZERO constants.
	for value in [
		0.0f32,
		-0.0,
		1.5,
		f32::INFINITY,
		f32::NEG_INFINITY,
		f32::from_bits(0x7FC0_0001),
	] {
		round_trip(&value);
	}
	for value in [
		0.0f64,
		-0.0,
		1.5,
		f64::INFINITY,
		f64::NEG_INFINITY,
		f64::from_bits(0x7FF8_0000_0000_0001),
	] {
		round_trip(&value);
	}
	assert!(!0.0f32.is_encoded_eq(&-0.0));
	assert!(!0.0f64.is_encoded_eq(&-0.0));
	assert!(!f32::from_bits(0x7FC0_0001).is_encoded_eq(&f32::from_bits(0x7FC0_0002)));
	assert!(!f64::from_bits(0x7FF8_0000_0000_0001).is_encoded_eq(&f64::from_bits(0x7FF8_0000_0000_0002)));
	unexpected::<f32>(ESExpr::Float64(1.0));
	unexpected::<f64>(ESExpr::Float32(1.0));
}

#[test]
fn nested_options_and_optional_fields() {
	for value in [None, Some(None), Some(Some(None)), Some(Some(Some(())))] {
		round_trip(&value);
	}
	for (level, expected) in [
		(0u8, None),
		(1, Some(None)),
		(2, Some(Some(None))),
		(3, Some(Some(Some(())))),
	] {
		assert_eq!(
			Option::<Option<Option<()>>>::decode_esexpr(ESExpr::Null(Cow::Owned(level.into())))
				.expect("nested null should decode"),
			expected
		);
		assert_eq!(expected.encode_esexpr(), ESExpr::Null(Cow::Owned(level.into())));
	}
	assert!(Option::<Option<Option<()>>>::decode_esexpr(ESExpr::Null(Cow::Owned(4u8.into()))).is_err());
	round_trip(&Some(42i32));
	assert!(!Some(1i32).is_encoded_eq(&None));
	assert_eq!(Option::<i32>::decode_optional_field(None).expect("absent field"), None);
	assert_eq!(
		Option::<i32>::decode_optional_field(Some(42i32.encode_esexpr())).expect("present field"),
		Some(42)
	);
	assert_eq!(None::<i32>.encode_optional_field(), None);
	assert_eq!(Some(42i32).encode_optional_field(), Some(42i32.encode_esexpr()));
	let error = Option::<i32>::decode_esexpr(ESExpr::Bool(false)).expect_err("inner type must reject wrong tag");
	assert!(
		matches!(error.error_type(), DecodeErrorType::UnexpectedExpr { expected_tags, actual_tag: ESExprTag::Bool } if *expected_tags == <i32 as ESExprCodec>::TAGS)
	);
	assert!(matches!(error.error_path(), DecodeErrorPath::Current));
	assert!(Option::<i32>::decode_optional_field(Some(ESExpr::Bool(false))).is_err());
}

#[test]
fn collections_round_trip_and_reject_invalid_shapes() {
	round_trip(&Vec::<i32>::new());
	round_trip(&vec![1i32, 2]);
	round_trip(&BTreeMap::<i32, i32>::new());
	round_trip(&BTreeMap::from([(1i32, 2i32)]));
	round_trip(&BTreeSet::<i32>::new());
	round_trip(&BTreeSet::from([1i32, 2]));
	macro_rules! invalid {
        ($ty:ty, $name:literal) => {{
            unexpected::<$ty>(ESExpr::Bool(false));
            unexpected::<$ty>(ESExpr::constructor("wrong", [], []));
            let error = <$ty>::decode_esexpr(ESExpr::constructor($name, [], [("key".into(), ESExpr::Bool(true))])).expect_err("kwargs must fail");
            assert!(matches!(error.error_type(), DecodeErrorType::OutOfRange(_)));
            assert!(matches!(error.error_path(), DecodeErrorPath::Constructor(name) if name == $name));
        }};
    }
	invalid!(Vec<i32>, "list");
	invalid!(BTreeMap<i32, i32>, "map");
	invalid!(BTreeSet<i32>, "set");
	let error = BTreeMap::<i32, i32>::decode_esexpr(ESExpr::constructor("map", [1i32.encode_esexpr()], []))
		.expect_err("odd arity must fail");
	assert!(matches!(error.error_type(), DecodeErrorType::OutOfRange(_)));
	assert!(matches!(error.error_path(), DecodeErrorPath::Constructor(name) if name == "map"));
	for args in [
		[ESExpr::Bool(true), 2i32.encode_esexpr()],
		[1i32.encode_esexpr(), ESExpr::Bool(true)],
	] {
		let error = BTreeMap::<i32, i32>::decode_esexpr(ESExpr::constructor("map", args, []))
			.expect_err("bad key/value must fail");
		assert!(matches!(error.error_type(), DecodeErrorType::UnexpectedExpr { .. }));
	}
	assert!(Vec::<i32>::decode_esexpr(ESExpr::constructor("list", [ESExpr::Bool(true)], [])).is_err());
	assert!(BTreeSet::<i32>::decode_esexpr(ESExpr::constructor("set", [ESExpr::Bool(true)], [])).is_err());
	assert!(!vec![1i32].is_encoded_eq(&vec![1, 2]));
	assert!(!vec![1i32].is_encoded_eq(&vec![2]));
}

#[test]
fn dictionary_conversions_and_validation() {
	round_trip(&Dictionary::<i32>::new());
	let values = BTreeMap::from([("a".to_owned(), 1i32), ("b".to_owned(), 2)]);
	let dictionary = Dictionary::from(values.clone());
	round_trip(&dictionary);
	assert_eq!(dictionary.len(), 2);
	assert_eq!(BTreeMap::from(dictionary.clone()), values);
	let hashbrown_map: hashbrown::HashMap<String, i32> = dictionary.clone().into();
	assert_eq!(Dictionary::from(hashbrown_map), dictionary);
	#[cfg(feature = "std")]
	{
		let std_map: std::collections::HashMap<String, i32> = dictionary.clone().into();
		assert_eq!(Dictionary::from(std_map), dictionary);
	}
	assert!(dictionary.is_encoded_eq(&dictionary.clone()));
	assert!(!dictionary.is_encoded_eq(&Dictionary::new()));
	unexpected::<Dictionary<i32>>(ESExpr::Bool(true));
	let error = Dictionary::<i32>::decode_esexpr(ESExpr::constructor("dict", [1i32.encode_esexpr()], []))
		.expect_err("positional arguments must fail");
	assert!(matches!(error.error_type(), DecodeErrorType::OutOfRange(_)));
	assert!(matches!(error.error_path(), DecodeErrorPath::Constructor(name) if name == "dict"));
	assert!(
		Dictionary::<i32>::decode_esexpr(ESExpr::constructor("dict", [], [("a".into(), ESExpr::Bool(true))])).is_err()
	);
}

#[test]
fn boxed_codecs_forward_fields_and_errors() {
	round_trip(&Box::new(42i32));
	unexpected::<Box<i32>>(ESExpr::Bool(false));
	assert_eq!(<Box<i32> as ESExprCodec>::TAGS, <i32 as ESExprCodec>::TAGS);
	for value in [None, Some(42i32)] {
		let boxed = Box::new(value);
		let decoded =
			Box::<Option<i32>>::decode_optional_field(boxed.encode_optional_field()).expect("boxed optional field");
		assert_eq!(boxed, decoded);
	}
	assert!(Box::<Option<i32>>::decode_optional_field(Some(ESExpr::Bool(true))).is_err());
	let values = Box::new(vec![1i32, 2]);
	let mut args = Vec::new();
	values.encode_vararg_element(&mut args);
	args.push(ESExpr::Bool(true));
	let mut args = VecDeque::from(args);
	let mut index = 5;
	assert_eq!(
		Box::<Vec<i32>>::decode_vararg_element(&mut args, "sample", &mut index).expect("boxed varargs"),
		values
	);
	assert_eq!(index, 7);
	assert_eq!(args, VecDeque::from([ESExpr::Bool(true)]));
	let values = Box::new(Dictionary::from(BTreeMap::from([("a".to_owned(), 42i32)])));
	let mut kwargs = hashbrown::HashMap::new();
	values.encode_dict_element(&mut kwargs);
	assert_eq!(
		Box::<Dictionary<i32>>::decode_dict_element(&mut kwargs, "sample").expect("boxed dictionary field"),
		values
	);
	assert!(kwargs.is_empty());
	kwargs.insert(CowStr::Static("bad"), ESExpr::Bool(true));
	let error =
		Box::<Dictionary<i32>>::decode_dict_element(&mut kwargs, "sample").expect_err("invalid field must fail");
	assert!(matches!(
		error.error_type(),
		DecodeErrorType::UnexpectedExpr {
			actual_tag: ESExprTag::Bool,
			..
		}
	));
	assert!(
		matches!(error.error_path(), DecodeErrorPath::Keyword(name, key, path) if name == "sample" && key == "bad" && matches!(**path, DecodeErrorPath::Current))
	);
	let mut args = VecDeque::from([256i32.encode_esexpr()]);
	let mut index = 5;
	let error =
		Box::<Vec<u8>>::decode_vararg_element(&mut args, "sample", &mut index).expect_err("range error in varargs");
	assert!(matches!(error.error_type(), DecodeErrorType::OutOfRange(_)));
	assert!(
		matches!(error.error_path(), DecodeErrorPath::Positional(name, 5, path) if name == "sample" && matches!(**path, DecodeErrorPath::Current))
	);
}

#[test]
fn map_and_set_equality_matches_their_encoded_argument_order() {
	fn check<'a, T: ESExprCodec<'a> + Debug>(a: &'a T, b: &'a T) {
		assert_eq!(a.is_encoded_eq(b), a.encode_esexpr() == b.encode_esexpr());
	}
	macro_rules! maps {
		($ty:ty) => {{
			let a: $ty = [(1i32, 2i32), (3, 4), (5, 6)].into_iter().collect();
			let b: $ty = [(5i32, 6i32), (3, 4), (1, 2)].into_iter().collect();
			check(&a, &b);
			check(&a, &a.clone());
			check(&a, &<$ty>::new());
			let different: $ty = [(1i32, 2i32), (3, 4), (5, 7)].into_iter().collect();
			check(&a, &different);
			assert_eq!(<$ty>::decode_esexpr(a.encode_esexpr()).expect("map round trip"), a);
			unexpected::<$ty>(ESExpr::Bool(false));
			assert!(<$ty>::decode_esexpr(ESExpr::constructor("map", [1i32.encode_esexpr()], [])).is_err());
			assert!(
				<$ty>::decode_esexpr(ESExpr::constructor("map", [], [("key".into(), ESExpr::Bool(false))])).is_err()
			);
		}};
	}
	macro_rules! sets {
		($ty:ty) => {{
			let a: $ty = [1i32, 2, 3].into_iter().collect();
			let b: $ty = [3i32, 2, 1].into_iter().collect();
			check(&a, &b);
			check(&a, &a.clone());
			check(&a, &<$ty>::new());
			let different: $ty = [1i32, 2, 4].into_iter().collect();
			check(&a, &different);
			assert_eq!(<$ty>::decode_esexpr(a.encode_esexpr()).expect("set round trip"), a);
			unexpected::<$ty>(ESExpr::Bool(false));
			assert!(
				<$ty>::decode_esexpr(ESExpr::constructor("set", [], [("key".into(), ESExpr::Bool(false))])).is_err()
			);
		}};
	}
	maps!(BTreeMap<i32, i32>);
	maps!(hashbrown::HashMap<i32, i32>);
	sets!(BTreeSet<i32>);
	sets!(hashbrown::HashSet<i32>);
	#[cfg(feature = "std")]
	{
		maps!(std::collections::HashMap<i32, i32>);
		sets!(std::collections::HashSet<i32>);
	}
}

#[test]
fn dictionary_equality_is_independent_of_iteration_order() {
	let a = Dictionary::from((0..32).map(|i| (format!("key{i}"), i)).collect::<BTreeMap<_, _>>());
	let b = Dictionary::from(
		(0..32)
			.rev()
			.map(|i| (format!("key{i}"), i))
			.collect::<hashbrown::HashMap<_, _>>(),
	);
	assert_eq!(a, b);
	assert_eq!(a.encode_esexpr(), b.encode_esexpr());
	assert!(a.is_encoded_eq(&b));
	let changed = Dictionary::from((0..32).map(|i| (format!("key{i}"), i + 1)).collect::<BTreeMap<_, _>>());
	let renamed = Dictionary::from((0..32).map(|i| (format!("other{i}"), i)).collect::<BTreeMap<_, _>>());
	assert!(!a.is_encoded_eq(&changed));
	assert!(!a.is_encoded_eq(&renamed));
}

#[test]
fn dictionary_codec_rejects_other_constructor_names() {
	unexpected::<Dictionary<i32>>(ESExpr::constructor("list", [], []));
	unexpected::<Dictionary<i32>>(ESExpr::constructor(
		"dictionary",
		[],
		[("a".into(), 1i32.encode_esexpr())],
	));
}
