#![expect(missing_docs, reason = "Integration tests")]

use std::borrow::Cow;
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};

use esexpr::cowstr::CowStr;
use esexpr::{ESExpr, ESExprEncodedEq, ESExprTag, ESExprTagSet};
use half::f16;
use num_bigint::{BigInt, BigUint};

#[test]
fn tag_set_algebra_matches_membership() {
	use ESExprTagSet::{All, Concat, Tags};
	let sets = [
		Tags(&[]),
		Concat(&[]),
		All,
		Tags(&[ESExprTag::Bool]),
		Tags(&[ESExprTag::Int, ESExprTag::Bool, ESExprTag::Bool]),
		Concat(&[Tags(&[]), Tags(&[ESExprTag::Bool]), Concat(&[Tags(&[ESExprTag::Int])])]),
		Concat(&[Tags(&[ESExprTag::Bool]), All]),
		Tags(&[ESExprTag::Constructor(CowStr::Static("a"))]),
		Tags(&[ESExprTag::Constructor(CowStr::Static("ab"))]),
	];
	let tags = [
		ESExprTag::Bool,
		ESExprTag::Int,
		ESExprTag::Str,
		ESExprTag::Float16,
		ESExprTag::Float32,
		ESExprTag::Float64,
		ESExprTag::Array8,
		ESExprTag::Array16,
		ESExprTag::Array32,
		ESExprTag::Array64,
		ESExprTag::Array128,
		ESExprTag::Null,
		ESExprTag::Constructor("a".into()),
		ESExprTag::Constructor("ab".into()),
		ESExprTag::Constructor("other".into()),
	];
	for a in sets {
		assert_eq!(a.is_empty(), tags.iter().all(|t| !a.contains(t)));
		assert_eq!(a.is_all(), tags.iter().all(|t| a.contains(t)));
		for b in sets {
			assert_eq!(a.is_disjoint(b), tags.iter().all(|t| !(a.contains(t) && b.contains(t))));
			assert_eq!(a.is_subset(b), tags.iter().all(|t| !a.contains(t) || b.contains(t)));
			assert_eq!(a.is_equal(b), tags.iter().all(|t| a.contains(t) == b.contains(t)));
			assert_eq!(a.is_disjoint(b), b.is_disjoint(a));
			assert_eq!(a == b, b == a);
			for c in sets {
				if a.is_subset(b) && b.is_subset(c) {
					assert!(a.is_subset(c));
				}
			}
		}
	}
	for tag in tags {
		assert_eq!(tag.as_borrowed(), tag);
		assert_eq!(tag.as_owned(), tag);
		assert_eq!(tag.clone().into_owned(), tag);
		assert_eq!(
			tag.is_constructor("a"),
			matches!(&tag, ESExprTag::Constructor(name) if **name == *"a")
		);
	}
}

fn hash(value: &CowStr<'_>) -> u64 {
	let mut hasher = DefaultHasher::new();
	value.hash(&mut hasher);
	hasher.finish()
}

#[test]
fn cowstr_storage_is_irrelevant_to_equality_ordering_and_hashing() {
	let backing = "héllo".to_owned();
	let strings = [
		CowStr::Borrowed(&backing),
		CowStr::Static("héllo"),
		CowStr::Owned(backing.clone()),
	];
	for a in &strings {
		assert_eq!(a.as_borrowed(), *a);
		assert_eq!(a.as_owned_cowstr(), *a);
		assert_eq!(a.clone().into_owned_cowstr(), *a);
		assert_eq!(a.clone().into_string(), backing);
		assert_eq!(format!("{a:?}"), format!("{backing:?}"));
		assert_eq!(*a, "héllo");
		assert_eq!("héllo", *a);
		assert!(a < &CowStr::Static("z"));
		assert_eq!(a.partial_cmp(&CowStr::Static("z")), Some(std::cmp::Ordering::Less));
		let cow: Cow<'_, str> = a.clone().into();
		assert_eq!(CowStr::from(cow), *a);
		for b in &strings {
			assert_eq!(a, b);
			assert_eq!(hash(a), hash(b));
		}
	}
	assert_eq!(CowStr::from(Cow::Borrowed(backing.as_str())), strings[0]);
	assert_eq!(CowStr::from(Cow::Owned(backing.clone())), strings[0]);
}

#[test]
fn deep_owned_expressions_outlive_all_backing_data() {
	let (owned, copied) = {
		let name = "nested".to_owned();
		let text = "borrowed".to_owned();
		let integer = BigInt::from(42);
		let null = BigUint::from(3u8);
		let bytes = [1u8, 255];
		let a16 = [1u16, u16::MAX];
		let a32 = [1u32, u32::MAX];
		let a64 = [1u64, u64::MAX];
		let a128 = [1u128, u128::MAX];
		let expr = ESExpr::constructor(
			CowStr::Borrowed(&name),
			[
				ESExpr::Bool(true),
				ESExpr::Int(Cow::Borrowed(&integer)),
				ESExpr::Str(CowStr::Borrowed(&text)),
				ESExpr::Float16(f16::from_bits(0x7E01)),
				ESExpr::Float32(f32::from_bits(0x7FC0_0001)),
				ESExpr::Float64(-0.0),
				ESExpr::Array8(Cow::Borrowed(&bytes)),
				ESExpr::Array16(Cow::Borrowed(&a16)),
				ESExpr::Array32(Cow::Borrowed(&a32)),
				ESExpr::Array64(Cow::Borrowed(&a64)),
				ESExpr::Array128(Cow::Borrowed(&a128)),
				ESExpr::Null(Cow::Borrowed(&null)),
			],
			[(
				CowStr::Borrowed(&text),
				ESExpr::constructor("inner", [ESExpr::Str(CowStr::Borrowed(&text))], []),
			)],
		);
		assert_eq!(expr.as_borrowed(), expr);
		let owned = expr.clone().into_owned();
		let copied = expr.as_owned();
		assert_eq!(owned, expr);
		assert_eq!(copied, expr);
		(owned, copied)
	};
	assert_eq!(owned, copied);
	assert_eq!(owned.as_borrowed().into_owned(), owned);
	assert_eq!(owned.tag(), ESExprTag::Constructor("nested".into()));
	let ESExpr::Constructor(constructor) = &owned
	else {
		panic!("expected constructor")
	};
	for value in &constructor.args {
		assert_eq!(value.as_borrowed().as_owned(), value);
	}
}

#[test]
fn expression_equality_checks_variants_args_kwargs_and_float_bits() {
	let a = ESExpr::constructor("sample", [ESExpr::Bool(true)], [("key".into(), ESExpr::Bool(false))]);
	assert!(a.is_encoded_eq(&a.clone()));
	for b in [
		ESExpr::Bool(true),
		ESExpr::constructor("different", [ESExpr::Bool(true)], [("key".into(), ESExpr::Bool(false))]),
		ESExpr::constructor("sample", [ESExpr::Bool(false)], [("key".into(), ESExpr::Bool(false))]),
		ESExpr::constructor("sample", [], [("key".into(), ESExpr::Bool(false))]),
		ESExpr::constructor("sample", [ESExpr::Bool(true)], []),
		ESExpr::constructor("sample", [ESExpr::Bool(true)], [("other".into(), ESExpr::Bool(false))]),
		ESExpr::constructor("sample", [ESExpr::Bool(true)], [("key".into(), ESExpr::Bool(true))]),
	] {
		assert_ne!(a, b);
	}
	assert_ne!(ESExpr::Float32(0.0), ESExpr::Float32(-0.0));
	assert_eq!(
		ESExpr::Float64(f64::from_bits(0x7FF8_0000_0000_0001)),
		ESExpr::Float64(f64::from_bits(0x7FF8_0000_0000_0001))
	);
	assert_ne!(
		ESExpr::Float16(f16::from_bits(0x7E01)),
		ESExpr::Float16(f16::from_bits(0x7E02))
	);
}

#[test]
fn argument_containers_preserve_values_across_storage_conversions() {
	use std::collections::BTreeMap;

	use esexpr::{ConstructorArgs, ESExprCodec, ESExprStatic, KeywordArgs};

	assert_eq!(ConstructorArgs::default().len(), 0);
	let args = [ESExpr::Bool(true), ESExpr::Str("value".into())];
	for converted in [
		ConstructorArgs::from(args.clone()),
		ConstructorArgs::from(args.to_vec()),
		ConstructorArgs::from(args.as_slice()),
	] {
		assert_eq!(converted.len(), 2);
		assert_eq!(Vec::from(converted.clone()), args);
		assert_eq!(converted.into_iter().collect::<Vec<_>>(), args);
	}
	let kwargs = hashbrown::HashMap::from([(CowStr::Static("key"), ESExpr::Bool(false))]);
	let sorted: BTreeMap<_, _> = kwargs.clone().into_iter().collect();
	for converted in [
		KeywordArgs::from(kwargs.clone()),
		KeywordArgs::from(&kwargs),
		KeywordArgs::from(sorted.clone()),
		KeywordArgs::from(&sorted),
	] {
		assert_eq!(converted.len(), 1);
		assert_eq!(hashbrown::HashMap::from(converted.clone()), kwargs);
		assert_eq!(BTreeMap::from(converted.clone()), sorted);
		assert_eq!(converted.into_iter().collect::<BTreeMap<_, _>>(), sorted);
	}
	let static_expr = {
		let backing = "temporary".to_owned();
		let borrowed = ESExpr::constructor("sample", [ESExpr::Str(CowStr::Borrowed(&backing))], &kwargs);
		let static_expr = ESExprStatic::decode_esexpr(borrowed.clone()).expect("static codec should own borrowed data");
		assert_eq!(static_expr.encode_esexpr(), borrowed);
		static_expr
	};
	assert!(static_expr.is_encoded_eq(&static_expr.clone()));
	assert_eq!(ESExprStatic::new(static_expr.clone().into_inner()), static_expr);
}

#[test]
fn primitive_tag_membership_and_expression_identity_codec() {
	use esexpr::ESExprCodec;
	const PRIMITIVES: &[ESExprTag<'static>] = &[
		ESExprTag::Bool,
		ESExprTag::Int,
		ESExprTag::Str,
		ESExprTag::Float16,
		ESExprTag::Float32,
		ESExprTag::Float64,
		ESExprTag::Array8,
		ESExprTag::Array16,
		ESExprTag::Array32,
		ESExprTag::Array64,
		ESExprTag::Array128,
		ESExprTag::Null,
	];
	let expressions = [
		ESExpr::Bool(false),
		ESExpr::Int(Cow::Owned(42.into())),
		ESExpr::Str("str".into()),
		ESExpr::Float16(f16::from_f32(1.5)),
		ESExpr::Float32(-0.0),
		ESExpr::Float64(f64::from_bits(0x7FF8_0000_0000_0001)),
		ESExpr::Array8(Cow::Owned(vec![1])),
		ESExpr::Array16(Cow::Owned(vec![2])),
		ESExpr::Array32(Cow::Owned(vec![3])),
		ESExpr::Array64(Cow::Owned(vec![4])),
		ESExpr::Array128(Cow::Owned(vec![5])),
		ESExpr::Null(Cow::Owned(2u8.into())),
	];

	for (expr, expected_tag) in expressions.iter().zip(PRIMITIVES) {
		assert_eq!(expr.tag(), *expected_tag);
		assert_eq!(expr.encode_esexpr(), *expr);
		assert_eq!(
			ESExpr::decode_esexpr(expr.as_borrowed()).expect("identity codec"),
			*expr
		);
	}
	for i in 0..PRIMITIVES.len() {
		let set = ESExprTagSet::Tags(&PRIMITIVES[i..=i]);
		for (j, tag) in PRIMITIVES.iter().enumerate() {
			assert_eq!(set.contains(tag), i == j);
		}
	}
}

#[test]
fn constructor_tag_membership_ignores_string_storage() {
	static OWNED_SET: std::sync::LazyLock<ESExprTagSet> = std::sync::LazyLock::new(|| {
		ESExprTagSet::Tags(Box::leak(
			vec![ESExprTag::Constructor(CowStr::Owned("record".to_owned()))].into_boxed_slice(),
		))
	});
	let sets = [
		*OWNED_SET,
		ESExprTagSet::Tags(&[ESExprTag::Constructor(CowStr::Static("record"))]),
		ESExprTagSet::Tags(&[ESExprTag::Constructor(CowStr::Borrowed("record"))]),
	];
	let backing = "record".to_owned();
	let tags = [
		ESExprTag::Constructor(CowStr::Borrowed(&backing)),
		ESExprTag::Constructor(CowStr::Static("record")),
		ESExprTag::Constructor(CowStr::Owned(backing.clone())),
	];
	for set in sets {
		for tag in &tags {
			assert!(set.contains(tag));
		}
		for different in [
			ESExprTag::Bool,
			ESExprTag::Constructor(CowStr::Static("recorD")),
			ESExprTag::Constructor(CowStr::Static("records")),
		] {
			assert!(!set.contains(&different));
		}
		for other in sets {
			assert!(set.is_equal(other));
		}
	}
}

#[test]
fn repeated_borrowing_preserves_nested_args_and_kwargs() {
	use esexpr::{ConstructorArgs, KeywordArgs};
	let text = "backing".to_owned();
	let args = [ESExpr::Str(CowStr::Borrowed(&text))];
	let kwargs = hashbrown::HashMap::from([(CowStr::Borrowed(&text), ESExpr::Array8(Cow::Borrowed(&[1, 2][..])))]);
	let expr = ESExpr::constructor(
		CowStr::Borrowed(&text),
		ConstructorArgs::from(args.as_slice()),
		KeywordArgs::from(&kwargs),
	);
	let once = expr.as_borrowed();
	let twice = once.as_borrowed();
	let thrice = twice.as_borrowed();
	assert_eq!(thrice, expr);
	assert_eq!(thrice.as_owned(), expr.as_owned());
}

#[test]
fn cowstr_display_and_unsized_str_equality() {
	let backing = "héllo".to_owned();
	for value in [
		CowStr::Borrowed(&backing),
		CowStr::Static("héllo"),
		CowStr::Owned(backing.clone()),
	] {
		assert_eq!(format!("{value}"), backing);
		assert!(str::eq(backing.as_str(), &value));
		assert!(!str::eq("different", &value));
	}
}

#[test]
fn argument_container_debug_formats_owned_and_borrowed_values() {
	use esexpr::{ConstructorArgs, KeywordArgs};

	let args = [ESExpr::Bool(true), ESExpr::Str("value".into())];
	for converted in [
		ConstructorArgs::from(args.to_vec()),
		ConstructorArgs::from(args.as_slice()),
	] {
		assert_eq!(
			format!("{converted:?}"),
			format!("ConstructorArgs {{ args: {args:?} }}")
		);
	}
	let kwargs = hashbrown::HashMap::from([(CowStr::Static("key"), ESExpr::Bool(false))]);
	for converted in [KeywordArgs::from(kwargs.clone()), KeywordArgs::from(&kwargs)] {
		assert_eq!(
			format!("{converted:?}"),
			format!("KeywordArgs {{ kwargs: {kwargs:?} }}")
		);
	}
	assert_eq!(
		format!("{:?}", ConstructorArgs::default()),
		"ConstructorArgs { args: [] }"
	);
	assert_eq!(
		format!(
			"{:?}",
			KeywordArgs::from(hashbrown::HashMap::<CowStr<'_>, ESExpr<'_>>::new())
		),
		"KeywordArgs { kwargs: {} }"
	);
}

#[test]
fn expression_equality_rejects_different_values_and_variants() {
	let pairs = [
		(ESExpr::Bool(false), ESExpr::Bool(true)),
		(ESExpr::Int(Cow::Owned(1.into())), ESExpr::Int(Cow::Owned(2.into()))),
		(ESExpr::Str("a".into()), ESExpr::Str("b".into())),
		(
			ESExpr::Float16(f16::from_bits(0x7E01)),
			ESExpr::Float16(f16::from_bits(0x7E02)),
		),
		(
			ESExpr::Float32(f32::from_bits(0x7FC0_0001)),
			ESExpr::Float32(f32::from_bits(0x7FC0_0002)),
		),
		(
			ESExpr::Float64(f64::from_bits(0x7FF8_0000_0000_0001)),
			ESExpr::Float64(f64::from_bits(0x7FF8_0000_0000_0002)),
		),
		(ESExpr::Array8(Cow::Owned(vec![1])), ESExpr::Array8(Cow::Owned(vec![2]))),
		(
			ESExpr::Array16(Cow::Owned(vec![1])),
			ESExpr::Array16(Cow::Owned(vec![2])),
		),
		(
			ESExpr::Array32(Cow::Owned(vec![1])),
			ESExpr::Array32(Cow::Owned(vec![2])),
		),
		(
			ESExpr::Array64(Cow::Owned(vec![1])),
			ESExpr::Array64(Cow::Owned(vec![2])),
		),
		(
			ESExpr::Array128(Cow::Owned(vec![1])),
			ESExpr::Array128(Cow::Owned(vec![2])),
		),
		(
			ESExpr::Null(Cow::Owned(0u8.into())),
			ESExpr::Null(Cow::Owned(1u8.into())),
		),
		(ESExpr::constructor("a", [], []), ESExpr::constructor("b", [], [])),
	];
	for (i, (a, b)) in pairs.iter().enumerate() {
		assert_eq!(a, &a.clone());
		assert_ne!(a, b);
		assert_ne!(b, a);
		assert!(!a.is_encoded_eq(b));
		assert!(!b.is_encoded_eq(a));
		for (j, (other, _)) in pairs.iter().enumerate() {
			if i != j {
				assert_ne!(a, other, "different variants {i} and {j}");
				assert!(!a.is_encoded_eq(other));
			}
		}
	}
}
