#![expect(missing_docs, reason = "Tests")]

use std::fs;
use std::path::Path;

use esexpr::ESExpr;

fn parse_test(path: &Path) {
	let esx = std::fs::read_to_string(path).expect("text fixture should be readable");
	let esx = esexpr_text::parse_multi(&esx).expect("text fixture should parse");

	let mut json = path
		.parent()
		.expect("fixture should have a parent directory")
		.to_path_buf();
	json.push(format!(
		"{}.json",
		path.file_stem()
			.expect("fixture should have a name")
			.to_str()
			.expect("fixture name should be UTF-8")
	));
	let json = std::fs::read_to_string(json).expect("JSON fixture should be readable");
	let json: esexpr_json::JsonEncodedESExpr = deser_json::from_str(&json).expect("JSON fixture should parse");
	let json = match json {
		esexpr_json::JsonEncodedESExpr::List(items) => items,
		_ => vec![json],
	};
	let json = json
		.into_iter()
		.map(esexpr_json::JsonEncodedESExpr::into_esexpr)
		.collect::<Vec<_>>();

	assert_eq!(esx, json);
}

#[test]
fn parse_tests() {
	for path in
		fs::read_dir(concat!(env!("CARGO_MANIFEST_DIR"), "/../../tests")).expect("fixture directory should be readable")
	{
		let path = path.expect("fixture directory entry should be readable").path();
		if path.extension().is_none_or(|extension| extension != "esx") {
			continue;
		}

		parse_test(&path);
	}
}

#[test]
fn parse_nan() {
	assert!(
		matches!(esexpr_text::parse(" #float32:nan ").expect("NaN literal should parse"), ESExpr::Float32(f) if f.is_nan())
	);
	assert!(
		matches!(esexpr_text::parse(" #float64:nan ").expect("NaN literal should parse"), ESExpr::Float64(f) if f.is_nan())
	);
}

#[test]
#[expect(
	clippy::cast_possible_truncation,
	reason = "The test values are exactly representable in f32"
)]
fn float_suffixes_signs_and_exponents() {
	for (decimal, hex, value) in [
		("1.5", "0x1.8p0", 1.5),
		("-1.5e+1", "-0x1.ep3", -15.0),
		("+1.25E-1", "+0x1.0P-3", 0.125),
	] {
		for (suffix, width) in [
			("", 64),
			("d", 64),
			("D", 64),
			("f", 32),
			("F", 32),
			("f16", 16),
			("F16", 16),
		] {
			for literal in [format!("{decimal}{suffix}"), format!("{hex}{suffix}")] {
				let expected = match width {
					16 => ESExpr::Float16(esexpr::core_types::half::f16::from_f64(value)),
					32 => ESExpr::Float32(value as f32),
					_ => ESExpr::Float64(value),
				};
				assert_eq!(
					esexpr_text::parse(&literal).expect("float literal should parse"),
					expected,
					"{literal}"
				);
			}
		}
	}
}

#[test]
fn nan_signs_payloads_and_invalid_payloads() {
	for (width, sign_bit, exponent, max_payload) in [
		(16, 0x8000u64, 0x7C00u64, 0x3FFu64),
		(32, 0x8000_0000, 0x7F80_0000, 0x7F_FFFF),
		(64, 0x8000_0000_0000_0000, 0x7FF0_0000_0000_0000, 0xF_FFFF_FFFF_FFFF),
	] {
		for (sign, negative) in [("", false), ("+", false), ("-", true)] {
			let literal = format!("#float{width}:{sign}nan");
			let parsed = esexpr_text::parse(&literal).expect("signed NaN");
			match parsed {
				ESExpr::Float16(f) => assert!(f.is_nan() && f.is_sign_negative() == negative),
				ESExpr::Float32(f) => assert!(f.is_nan() && f.is_sign_negative() == negative),
				ESExpr::Float64(f) => assert!(f.is_nan() && f.is_sign_negative() == negative),
				_ => panic!("expected float"),
			}
			for payload in [1, max_payload] {
				let literal = format!("#float{width}:{sign}nan:{payload}");
				let parsed = esexpr_text::parse(&literal).expect("NaN payload");
				let bits = match parsed {
					ESExpr::Float16(f) => u64::from(f.to_bits()),
					ESExpr::Float32(f) => u64::from(f.to_bits()),
					ESExpr::Float64(f) => f.to_bits(),
					_ => panic!("expected float"),
				};
				assert_eq!(bits, exponent | payload | if negative { sign_bit } else { 0 });
			}
		}
		assert!(esexpr_text::parse(&format!("#float{width}:nan:{}", max_payload + 1)).is_err());
	}
}

#[test]
fn malformed_text_and_file_boundaries() {
	for input in [
		"",
		"(list",
		"\"unterminated",
		"\"\\u{110000}\"",
		"1.0 trailing",
		"0x1.0",
		"#float32:nan:4294967296",
	] {
		assert!(esexpr_text::parse(input).is_err(), "accepted {input:?}");
	}
	assert_eq!(esexpr_text::parse_multi(" ").expect("empty file"), []);
	assert_eq!(
		esexpr_text::parse_multi("#true #false").expect("multiple expressions"),
		[ESExpr::Bool(true), ESExpr::Bool(false)]
	);
}

#[test]
fn unsigned_integer_radices_boundaries_and_remainders() {
	use esexpr::core_types::num_bigint::BigUint;
	use esexpr_text::parser::unsigned_integer;
	let huge = BigUint::from(1u8) << 128usize;
	for (literal, expected) in [
		("0", BigUint::ZERO),
		("255", 255u16.into()),
		("0xff", 255u16.into()),
		("0XFF", 255u16.into()),
		("0b11111111", 255u16.into()),
		("0B11111111", 255u16.into()),
		("0o377", 255u16.into()),
		("340282366920938463463374607431768211456", huge.clone()),
		("0x100000000000000000000000000000000", huge.clone()),
	] {
		let input = format!("  {literal} remaining");
		let (rest, value) = unsigned_integer(&input).expect("unsigned integer should parse");
		assert_eq!(value, expected, "{literal}");
		assert_eq!(rest, " remaining", "{literal}");
	}
	let binary = format!("0b1{}", "0".repeat(128));
	assert_eq!(unsigned_integer(&binary).expect("large binary integer"), ("", huge));
	for input in ["", " ", "-1", "+1", "not-a-number"] {
		assert!(unsigned_integer(input).is_err(), "accepted {input:?}");
	}
}
