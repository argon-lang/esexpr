#![expect(missing_docs, reason = "Integration tests")]

use std::borrow::Cow;
use std::collections::BTreeMap;

use esexpr::{ESExpr, ESExprCodec, ESExprEncodedEq};
use esexpr_json::{JsonEncodedESExpr, JsonExpr};
use half::f16;
use num_bigint::{BigInt, BigUint};
use serde_json::json;

#[test]
fn nested_json_values_round_trip() {
	for value in [
		json!(null),
		json!(true),
		json!(false),
		json!(1.5),
		json!(-42.0),
		json!("héllo"),
		json!([]),
		json!({}),
		json!({"nested": [null, true, "str", 1.5, {"a": []}]}),
	] {
		let expr = JsonExpr::from_json(value.clone()).expect("JSON value should convert");
		let decoded = JsonExpr::decode_esexpr(expr.encode_esexpr()).expect("JSON expression codec");
		assert!(expr.is_encoded_eq(&decoded));
		assert_eq!(decoded.into_json(), Some(value));
	}
}

#[test]
fn nonfinite_numbers_fail_even_when_nested() {
	for n in [f64::NAN, f64::INFINITY, f64::NEG_INFINITY] {
		assert!(JsonExpr::Num(n).into_json().is_none());
		assert!(JsonExpr::Arr(vec![JsonExpr::Num(n)]).into_json().is_none());
		assert!(
			JsonExpr::Obj {
				values: BTreeMap::from([("bad".to_owned(), JsonExpr::Num(n))])
			}
			.into_json()
			.is_none()
		);
	}
}

#[test]
fn every_esexpr_variant_round_trips_through_json() {
	let huge = BigInt::from(1u8) << 256usize;
	let expressions = [
		ESExpr::constructor(
			"example",
			[ESExpr::Bool(true)],
			[("keyword".into(), ESExpr::Str("value".into()))],
		),
		ESExpr::constructor("empty", [], []),
		ESExpr::Bool(false),
		ESExpr::Bool(true),
		ESExpr::Int(Cow::Owned(huge.clone())),
		ESExpr::Int(Cow::Owned(-huge)),
		ESExpr::Str("héllo\0".into()),
		ESExpr::Array8(Cow::Owned(vec![0, 255])),
		ESExpr::Array16(Cow::Owned(vec![0, u16::MAX])),
		ESExpr::Array32(Cow::Owned(vec![0, u32::MAX])),
		ESExpr::Array64(Cow::Owned(vec![0, u64::MAX])),
		ESExpr::Array128(Cow::Owned(vec![0, u128::MAX])),
		ESExpr::Float16(f16::from_f32(1.5)),
		ESExpr::Float32(-0.0),
		ESExpr::Float64(1.5),
		ESExpr::Null(Cow::Owned(BigUint::ZERO)),
		ESExpr::Null(Cow::Owned(BigUint::from(1u8) << 256)),
	];
	for expr in expressions {
		let encoded = JsonEncodedESExpr::from_esexpr(expr.clone());
		let json = serde_json::to_string(&encoded).expect("expression should serialize");
		let decoded: JsonEncodedESExpr = serde_json::from_str(&json).unwrap_or_else(|error| panic!("{json}: {error}"));
		assert_eq!(decoded.into_esexpr(), expr, "JSON: {json}");
	}
}

#[test]
fn omitted_constructor_fields_lists_and_array8() {
	for input in [
		r#"{"constructor_name":"empty"}"#,
		r#"{"constructor_name":"empty","args":null,"kwargs":null}"#,
	] {
		let decoded: JsonEncodedESExpr = serde_json::from_str(input).expect("omitted fields should decode");
		assert_eq!(decoded.into_esexpr(), ESExpr::constructor("empty", [], []));
	}
	let list: JsonEncodedESExpr = serde_json::from_str("[true,\"str\",null]").expect("list should deserialize");
	assert_eq!(
		list.into_esexpr(),
		ESExpr::constructor(
			"list",
			[
				ESExpr::Bool(true),
				ESExpr::Str("str".into()),
				ESExpr::Null(Cow::Owned(BigUint::ZERO))
			],
			[]
		)
	);
	let bytes: JsonEncodedESExpr = serde_json::from_str(r#"{"array8":[0,255]}"#).expect("array8 should deserialize");
	let bytes = bytes.into_esexpr();
	assert_eq!(bytes, ESExpr::Array8(Cow::Owned(vec![0, 255])));
	assert_eq!(
		serde_json::to_value(JsonEncodedESExpr::from_esexpr(bytes)).expect("bytes serialize"),
		json!({"base64":"AP8="})
	);
}

#[test]
fn float_json_representations_and_numeric_visitors() {
	for (width, values) in [
		(
			"float16",
			vec![
				ESExpr::Float16(f16::NAN),
				ESExpr::Float16(f16::INFINITY),
				ESExpr::Float16(f16::NEG_INFINITY),
			],
		),
		(
			"float32",
			vec![
				ESExpr::Float32(f32::NAN),
				ESExpr::Float32(f32::INFINITY),
				ESExpr::Float32(f32::NEG_INFINITY),
			],
		),
		(
			"float64",
			vec![
				ESExpr::Float64(f64::NAN),
				ESExpr::Float64(f64::INFINITY),
				ESExpr::Float64(f64::NEG_INFINITY),
			],
		),
	] {
		for (expr, special) in values.into_iter().zip(["nan", "+inf", "-inf"]) {
			let value = serde_json::to_value(JsonEncodedESExpr::from_esexpr(expr)).expect("special float serialize");
			assert_eq!(value, json!({width: special}));
			let decoded = serde_json::from_value::<JsonEncodedESExpr>(value)
				.expect("special float deserialize")
				.into_esexpr();
			let f = match decoded {
				ESExpr::Float16(f) => f64::from(f),
				ESExpr::Float32(f) => f64::from(f),
				ESExpr::Float64(f) => f,
				_ => panic!("expected float"),
			};
			match special {
				"nan" => assert!(f.is_nan()),
				"+inf" => assert_eq!(f, f64::INFINITY),
				"-inf" => assert_eq!(f, f64::NEG_INFINITY),
				_ => unreachable!(),
			}
		}
	}
	for width in ["float16", "float32", "float64"] {
		for n in [json!(-1), json!(1), json!(1.5)] {
			let input = json!({width: n});
			let decoded = serde_json::from_value::<JsonEncodedESExpr>(input.clone()).expect("numeric float visitor");
			assert_eq!(
				serde_json::to_value(decoded).expect("finite float serialize"),
				json!({width: n.as_f64().expect("number")})
			);
		}
	}
}

#[test]
fn invalid_typed_json_values_are_rejected() {
	for input in [
		json!({"base64":"%%%"}),
		json!({"base64":123}),
		json!({"int":"not an integer"}),
		json!({"int":42}),
		json!({"null":"-1"}),
		json!({"null":"bad"}),
		json!({"array128":[-1]}),
		json!({"array128":["bad"]}),
		json!({"array8":[256]}),
		json!({"float16":"infinity"}),
		json!({"float32":"invalid"}),
		json!({"float64":"invalid"}),
		json!({"float16":true}),
		json!({"float32":[]}),
		json!({"float64":{}}),
	] {
		assert!(
			serde_json::from_value::<JsonEncodedESExpr>(input.clone()).is_err(),
			"accepted {input}"
		);
	}
}

#[test]
fn uint128_json_uses_strings_only_above_uint64_range() {
	let values = vec![0u128, u128::from(u64::MAX), u128::from(u64::MAX) + 1, u128::MAX];
	let encoded = JsonEncodedESExpr::from_esexpr(ESExpr::Array128(Cow::Owned(values.clone())));
	let expected =
		json!({"array128": [0, u64::MAX, "18446744073709551616", "340282366920938463463374607431768211455"]});
	assert_eq!(
		serde_json::to_value(&encoded).expect("mixed uint128 array should serialize"),
		expected
	);
	for decoded in [
		serde_json::from_value::<JsonEncodedESExpr>(expected).expect("mixed array from Value"),
		serde_json::from_str::<JsonEncodedESExpr>(&serde_json::to_string(&encoded).expect("serialize array"))
			.expect("mixed array from string"),
	] {
		assert_eq!(decoded.into_esexpr(), ESExpr::Array128(Cow::Owned(values.clone())));
	}
	let small_strings: JsonEncodedESExpr =
		serde_json::from_str(r#"{"array128":["0","42"]}"#).expect("decimal strings are accepted");
	assert_eq!(small_strings.into_esexpr(), ESExpr::Array128(Cow::Owned(vec![0, 42])));
	for value in [
		json!("340282366920938463463374607431768211456"),
		json!("-1"),
		json!("1.5"),
		json!(1.5),
		json!(true),
		json!(null),
	] {
		assert!(serde_json::from_value::<JsonEncodedESExpr>(json!({"array128":[value]})).is_err());
	}
}

#[test]
fn invalid_array128_container_types_are_rejected() {
	for value in [json!(true), json!(42), json!("not an array"), json!(null), json!({})] {
		assert!(serde_json::from_value::<JsonEncodedESExpr>(json!({"array128": value})).is_err());
	}
}

#[test]
fn serialization_io_errors_propagate_at_every_output_byte() {
	struct FailingWriter {
		output: Vec<u8>,
		remaining: usize,
	}
	impl std::io::Write for FailingWriter {
		fn write(&mut self, bytes: &[u8]) -> std::io::Result<usize> {
			if self.remaining == 0 {
				return Err(std::io::Error::other("test writer failure"));
			}
			let n = bytes.len().min(self.remaining);
			self.output.extend_from_slice(&bytes[..n]);
			self.remaining -= n;
			Ok(n)
		}
		fn flush(&mut self) -> std::io::Result<()> {
			Ok(())
		}
	}
	for expr in [
		ESExpr::Array128(Cow::Owned(vec![0, u128::from(u64::MAX), u128::MAX])),
		ESExpr::constructor(
			"nested",
			[ESExpr::Array8(Cow::Owned(vec![0, 255])), ESExpr::Float32(f32::INFINITY)],
			[("key".into(), ESExpr::Int(Cow::Owned(BigInt::from(1u8) << 256usize)))],
		),
		ESExpr::Null(Cow::Owned(BigUint::from(1u8) << 128usize)),
	] {
		let expr = JsonEncodedESExpr::from_esexpr(expr);
		let expected = serde_json::to_vec(&expr).expect("baseline should serialize");
		for offset in 0..expected.len() {
			let mut writer = FailingWriter {
				output: Vec::new(),
				remaining: offset,
			};
			let error = serde_json::to_writer(&mut writer, &expr).expect_err("writer must fail");
			assert!(error.is_io());
			assert_eq!(error.io_error_kind(), Some(std::io::ErrorKind::Other));
			assert!(error.to_string().contains("test writer failure"));
			assert_eq!(writer.output, expected[..offset], "prefix at offset {offset}");
		}
	}
}
