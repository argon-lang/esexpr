#![expect(missing_docs, reason = "Tests")]

use std::convert::Infallible;
use std::path::{Path, PathBuf};

use esexpr::{ESExpr, ValueEq};
use esexpr_binary::ExprParserSync;

fn encoding_test(name: &str) {
	let mut dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
	dir.push("../../tests");

	let mut esx = dir.clone();
	esx.push(format!("{}.esxb", name));

	let mut json = dir;
	json.push(format!("{}.json", name));
	let json = std::fs::read_to_string(json).unwrap();
	let json: esexpr_json::JsonEncodedESExpr = serde_json::from_str(&json).unwrap();
	let json = match json {
		esexpr_json::JsonEncodedESExpr::List(items) => items,
		_ => vec![json],
	};
	let json = json
		.into_iter()
		.map(esexpr_json::JsonEncodedESExpr::into_esexpr)
		.collect::<Vec<_>>();

	encoding_test_sync(&json, &esx);
}

fn encoding_test_sync(expected: &[ESExpr<'static>], path: &Path) {
	use esexpr_binary::ExprGeneratorSync;
	let esx = {
		let mut file = std::fs::File::open(path).unwrap();
		esexpr_binary::parse_sync(&mut file)
			.iter_static()
			.collect::<Result<Vec<_>, _>>()
			.unwrap()
	};

	assert!(expected.value_eq(&esx));

	let mut reencoded = Vec::with_capacity(esx.len());
	for e in &esx {
		let mut data: Vec<u8> = Vec::new();
		let mut eg = esexpr_binary::ExprGenerator::<_, Infallible>::new(&mut data);
		eg.generate(e).unwrap();

		reencoded.extend(
			esexpr_binary::parse_sync::<Infallible>(&mut data.as_slice())
				.iter_static()
				.collect::<Result<Vec<_>, _>>()
				.unwrap(),
		);
	}
}

#[test]
fn encoding_bool() {
	encoding_test("bool_false");
	encoding_test("bool_true");
}

#[test]
fn encoding_constructor() {
	encoding_test("constructor");
	encoding_test("constructor2");
	encoding_test("constructor-keyword");
}

#[test]
fn encoding_append_string_table() {
	encoding_test("append-string-table");
}

#[test]
fn encoding_str() {
	encoding_test("str");
}

#[test]
fn encoding_int() {
	encoding_test("int");
}

#[test]
fn encoding_float32() {
	encoding_test("float32");
}

#[test]
fn encoding_float64() {
	encoding_test("float64");
}

#[test]
fn encoding_null() {
	encoding_test("null");
}
