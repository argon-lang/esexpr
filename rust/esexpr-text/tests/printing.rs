#![expect(missing_docs, reason = "Tests")]

use esexpr::ESExpr;
use esexpr_text::{ExprPrinter, Indentation, format_as_string, parse};
use proptest::prelude::*;

proptest! {
	#[test]
	fn arbitrary_expressions_round_trip(expr in any::<ESExpr<'static>>()) {
		let text = format_as_string(&expr);
		prop_assert_eq!(&parse(&text).expect("printed expression should parse"), &expr);
		for indentation in [Indentation::Spaces(0), Indentation::Spaces(2), Indentation::Spaces(4), Indentation::Tabs(0), Indentation::Tabs(1), Indentation::Tabs(2)] {
			let mut options = ExprPrinter::default();
			options.indentation = indentation;
			prop_assert_eq!(&parse(&options.format_as_string(&expr)).expect("indented expression should parse"), &expr);
		}
	}
}

#[test]
fn canonical_output() {
	for (input, expected) in [
		("(example 42 z: #true a: (nested))", "(example 42 a: (nested) z: #true)"),
		("('' '': \"\")", "('' '': \"\")"),
		(
			"('A b' 'x\\'y': \"\\\"\\\\\\n\\r\\t\\f\\u{0}λ\")",
			"('A b' 'x\\'y': \"\\\"\\\\\\n\\r\\t\\f\\u{0}λ\")",
		),
		("#u8[0 255 16]", "#\"00ff10\""),
		("#u16[0 65535]", "#u16[0 65535]"),
		("#u32[4294967295]", "#u32[4294967295]"),
		("#u64[18446744073709551615]", "#u64[18446744073709551615]"),
		(
			"#u128[340282366920938463463374607431768211455]",
			"#u128[340282366920938463463374607431768211455]",
		),
		("\"\"", "\"\""),
		("#null0", "#null"),
		(
			"#null123456789012345678901234567890",
			"#null123456789012345678901234567890",
		),
		("-123456789012345678901234567890", "-123456789012345678901234567890"),
		("-0.0f16", "-0.0f16"),
		("1.0f", "1.0f"),
		("1.0", "1.0d"),
		("#float64:-nan:1", "#float64:-nan:1"),
	] {
		let expr = parse(input).expect("input should parse");
		assert_eq!(format_as_string(&expr), expected);
		let mut output = String::new();
		ExprPrinter::default()
			.write(&mut output, &expr)
			.expect("sink should accept output");
		assert_eq!(output, expected);
		assert_eq!(parse(expected).expect("output should parse"), expr);
	}
}

#[test]
fn every_float16_bit_pattern_round_trips() {
	for bits in 0..=u16::MAX {
		let expr = ESExpr::Float16(half::f16::from_bits(bits));
		assert_eq!(parse(&format_as_string(&expr)).expect("float16 should parse"), expr);
	}
}

#[test]
fn float_boundaries_round_trip() {
	for bits in [
		0,
		1,
		0x8000_0000,
		0x007F_FFFF,
		0x0080_0000,
		0x7F7F_FFFF,
		0x7F80_0000,
		0xFF80_0000,
		0x7F80_0001,
		0xFFFF_FFFF,
	] {
		let expr = ESExpr::Float32(f32::from_bits(bits));
		assert_eq!(parse(&format_as_string(&expr)).expect("float32 should parse"), expr);
	}
	for bits in [
		0,
		1,
		0x8000_0000_0000_0000,
		0x000F_FFFF_FFFF_FFFF,
		0x0010_0000_0000_0000,
		0x7FEF_FFFF_FFFF_FFFF,
		0x7FF0_0000_0000_0000,
		0xFFF0_0000_0000_0000,
		0x7FF0_0000_0000_0001,
		u64::MAX,
	] {
		let expr = ESExpr::Float64(f64::from_bits(bits));
		assert_eq!(parse(&format_as_string(&expr)).expect("float64 should parse"), expr);
	}
}

#[test]
fn text_fixtures_round_trip() {
	for entry in std::fs::read_dir(concat!(env!("CARGO_MANIFEST_DIR"), "/../../tests"))
		.expect("fixture directory should be readable")
	{
		let path = entry.expect("fixture entry should be readable").path();
		if path.extension().is_none_or(|ext| ext != "esx") {
			continue;
		}
		let source = std::fs::read_to_string(path).expect("fixture should be readable");
		let expressions = esexpr_text::parse_multi(&source).expect("fixture should parse");
		let output = expressions.iter().map(format_as_string).collect::<Vec<_>>().join("\n");
		assert_eq!(
			esexpr_text::parse_multi(&output).expect("output should parse"),
			expressions
		);
	}
}

#[test]
fn indentation() {
	let expr = parse("(outer (inner 1) z: #true a: (empty))").expect("input should parse");
	let mut options = ExprPrinter::default();
	options.indentation = Indentation::Spaces(2);
	assert_eq!(
		options.format_as_string(&expr),
		"(outer\n  (inner\n    1\n  )\n  a: (empty)\n  z: #true\n)"
	);
	options.indentation = Indentation::Spaces(0);
	assert_eq!(
		options.format_as_string(&expr),
		"(outer\n(inner\n1\n)\na: (empty)\nz: #true\n)"
	);
	assert_eq!(
		options.format_as_string(&parse("#u16[1 2]").expect("array should parse")),
		"#u16[1 2]"
	);
}

#[test]
fn sink_errors_are_propagated() {
	struct FailingSink;
	impl std::fmt::Write for FailingSink {
		fn write_str(&mut self, _: &str) -> std::fmt::Result {
			Err(std::fmt::Error)
		}
	}
	assert!(
		ExprPrinter::default()
			.write(&mut FailingSink, &ESExpr::Bool(true))
			.is_err()
	);
}

#[test]
fn tab_indentation() {
	let expr = parse("(outer (inner 1) z: #true a: (nested child: (leaf 2)))").expect("input should parse");
	let mut printer = ExprPrinter::default();
	printer.indentation = Indentation::Tabs(1);
	let text = printer.format_as_string(&expr);
	assert_eq!(
		text,
		"(outer\n\t(inner\n\t\t1\n\t)\n\ta: (nested\n\t\tchild: (leaf\n\t\t\t2\n\t\t)\n\t)\n\tz: #true\n)"
	);
	assert_eq!(parse(&text).expect("tab-indented expression should parse"), expr);
	assert_eq!(
		printer.format_as_string(&parse("(empty)").expect("input should parse")),
		"(empty)"
	);
}

#[test]
fn indentation_variants() {
	let expr = parse("(outer (inner 1))").expect("input should parse");
	assert_eq!(Indentation::default(), Indentation::Compact);
	let mut printer = ExprPrinter::default();
	assert_eq!(printer.indentation, Indentation::Compact);
	assert_eq!(printer.format_as_string(&expr), "(outer (inner 1))");
	printer.indentation = Indentation::Tabs(2);
	assert_eq!(
		printer.format_as_string(&expr),
		"(outer\n\t\t(inner\n\t\t\t\t1\n\t\t)\n)"
	);
	printer.indentation = Indentation::Tabs(0);
	assert_eq!(printer.format_as_string(&expr), "(outer\n(inner\n1\n)\n)");
}
