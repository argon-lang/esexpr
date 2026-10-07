use std::borrow::Cow;

use esexpr::{DecodeErrorPath, DecodeErrorType, ESExpr, ESExprCodec, ESExprEncodedEq, esexpr, esexpr_flags};
use num_bigint::{BigInt, BigUint};

#[test]
fn literal_atoms_nested_constructors_and_keywords() {
	assert_eq!(esexpr!(#null), ESExpr::Null(Cow::Owned(BigUint::ZERO)));
	assert_eq!(esexpr!(true), ESExpr::Bool(true));
	assert_eq!(esexpr!(false), ESExpr::Bool(false));
	assert_eq!(esexpr!(1.5f32), ESExpr::Float32(1.5));
	assert_eq!(esexpr!(1.5f64), ESExpr::Float64(1.5));
	assert_eq!(esexpr!(1.5), ESExpr::Float64(1.5));
	assert_eq!(esexpr!(42), ESExpr::Int(Cow::Owned(BigInt::from(42))));
	assert_eq!(
		esexpr!((r#type (nested #null) r#extern: true :"quoted-key" "value")),
		ESExpr::constructor(
			"type",
			[ESExpr::constructor(
				"nested",
				[ESExpr::Null(Cow::Owned(BigUint::ZERO))],
				[]
			)],
			[
				("extern".into(), ESExpr::Bool(true)),
				("quoted-key".into(), ESExpr::Str("value".into()))
			]
		)
	);
	assert_eq!(esexpr!(("quoted-name")), ESExpr::constructor("quoted-name", [], []));
}

#[test]
fn flags_accept_all_radices_and_large_string_masks() {
	esexpr_flags! {
		LargeFlags:
		decimal = "1",
		binary = "0b10",
		octal = "0o4",
		hex = "0x08",
		underscores = "0x1_0",
		above_u64 = "18446744073709551616",
		above_u128 = "340282366920938463463374607431768211456",
		enum mode { off = "0", on = "0x20" }
	}
	let flags = LargeFlags {
		decimal: true,
		binary: true,
		octal: true,
		hex: true,
		underscores: true,
		above_u64: true,
		above_u128: true,
		mode: LargeFlags__mode::on,
	};
	let expected = (BigInt::from(1u8) << 128usize) | (BigInt::from(1u8) << 64usize) | BigInt::from(63u8);
	assert_eq!(flags.encode_esexpr(), ESExpr::Int(Cow::Owned(expected)));
	let decoded = LargeFlags::decode_esexpr(flags.encode_esexpr()).expect("large mask round trip");
	assert!(flags.is_encoded_eq(&decoded));
	assert_eq!(decoded, flags);
	let empty = LargeFlags::decode_esexpr(ESExpr::Int(Cow::Owned(BigInt::ZERO))).expect("zero flags");
	assert!(!empty.decimal && !empty.above_u128);
	assert_eq!(empty.mode, LargeFlags__mode::off);
}

#[test]
fn generated_flags_codec_rejects_invalid_values() {
	esexpr_flags! { ModeFlags: enum mode { a = 1, b = 2 } }
	for n in [0, 3, -1] {
		let error =
			ModeFlags::decode_esexpr(ESExpr::Int(Cow::Owned(n.into()))).expect_err("invalid enum/negative flags");
		assert!(matches!(error.error_type(), DecodeErrorType::OutOfRange(_)));
		assert!(matches!(error.error_path(), DecodeErrorPath::Current));
	}
	let error = ModeFlags::decode_esexpr(ESExpr::Bool(true)).expect_err("wrong tag");
	assert!(matches!(error.error_type(), DecodeErrorType::UnexpectedExpr { .. }));
	assert!(matches!(error.error_path(), DecodeErrorPath::Current));
}

#[derive(Debug, PartialEq, esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
struct LifetimeRecord<'a> {
	value: esexpr::cowstr::CowStr<'a>,
}

#[derive(Debug, PartialEq, esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
struct ConstRecord<const N: usize> {
	value: i32,
}

#[derive(Debug, PartialEq, esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
struct UnitRecord;

#[derive(Debug, PartialEq, esexpr::ESExprCodec, esexpr::ESExprEncodedEq)]
enum NamedInline {
	#[esexpr(inline_value)]
	Value {
		value: u8,
	},
	Empty,
}

#[test]
fn derive_preserves_lifetime_and_const_parameters() {
	let backing = "borrowed".to_owned();
	let value = LifetimeRecord {
		value: esexpr::cowstr::CowStr::Borrowed(&backing),
	};
	let encoded = value.encode_esexpr();
	assert_eq!(
		encoded,
		ESExpr::constructor(
			"lifetime-record",
			[ESExpr::Str(esexpr::cowstr::CowStr::Borrowed(&backing))],
			[]
		)
	);
	let decoded = LifetimeRecord::decode_esexpr(encoded).expect("lifetime record should decode");
	assert_eq!(decoded, value);
	assert!(value.is_encoded_eq(&decoded));
	assert!(!value.is_encoded_eq(&LifetimeRecord {
		value: "different".into()
	}));
	let value = ConstRecord::<42> { value: 7 };
	let encoded = value.encode_esexpr();
	assert_eq!(encoded, ESExpr::constructor("const-record", [7i32.encode_esexpr()], []));
	assert_eq!(
		ConstRecord::<42>::decode_esexpr(encoded).expect("const record should decode"),
		value
	);
	assert!(value.is_encoded_eq(&ConstRecord::<42> { value: 7 }));
	assert!(!value.is_encoded_eq(&ConstRecord::<42> { value: 8 }));
}

#[test]
fn unit_struct_and_named_inline_variant_codecs() {
	let unit = UnitRecord;
	assert_eq!(unit.encode_esexpr(), ESExpr::constructor("unit-record", [], []));
	assert_eq!(
		UnitRecord::decode_esexpr(unit.encode_esexpr()).expect("unit struct should decode"),
		unit
	);
	assert!(unit.is_encoded_eq(&UnitRecord));
	for value in [
		NamedInline::Value { value: 0 },
		NamedInline::Value { value: 255 },
		NamedInline::Empty,
	] {
		let decoded = NamedInline::decode_esexpr(value.encode_esexpr()).expect("named inline variant should decode");
		assert_eq!(decoded, value);
		assert!(value.is_encoded_eq(&decoded));
	}
	assert_eq!(NamedInline::Value { value: 42 }.encode_esexpr(), 42u8.encode_esexpr());
	assert!(!NamedInline::Value { value: 42 }.is_encoded_eq(&NamedInline::Value { value: 43 }));
	assert!(!NamedInline::Value { value: 42 }.is_encoded_eq(&NamedInline::Empty));
	let error = NamedInline::decode_esexpr(256u16.encode_esexpr()).expect_err("inline integer overflow");
	assert!(matches!(error.error_type(), DecodeErrorType::OutOfRange(_)));
	assert!(matches!(error.error_path(), DecodeErrorPath::Current));
	let error = UnitRecord::decode_esexpr(ESExpr::constructor("other-record", [], []))
		.expect_err("wrong unit constructor name");
	assert!(matches!(error.error_type(), DecodeErrorType::UnexpectedExpr { .. }));
}

#[test]
fn flags_accept_enum_commas_and_a_final_flag_without_comma() {
	esexpr_flags! {
		CommaFlags:
		enum mode { off = "0", on = 2, },
		bit = 1
	}
	let value = CommaFlags {
		mode: CommaFlags__mode::on,
		bit: true,
	};
	assert_eq!(value.encode_esexpr(), 3u8.encode_esexpr());
	assert_eq!(
		CommaFlags::decode_esexpr(value.encode_esexpr()).expect("comma forms should round trip"),
		value
	);
}
