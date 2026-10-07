#![expect(missing_docs, reason = "Tests")]

use std::fs;
use std::path::Path;

use esexpr::ESExpr;

fn parse_test(path: &Path) {
	let esx = std::fs::read_to_string(path).expect("text fixture should be readable");
	let esx = esexpr_text::parse_multi(&esx).expect("text fixture should parse");

	let mut json = path.parent().expect("fixture should have a parent directory").to_path_buf();
	json.push(format!("{}.json", path.file_stem().expect("fixture should have a name").to_str().expect("fixture name should be UTF-8")));
	let json = std::fs::read_to_string(json).expect("JSON fixture should be readable");
	let json: esexpr_json::JsonEncodedESExpr = serde_json::from_str(&json).expect("JSON fixture should parse");
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
	for path in fs::read_dir(concat!(env!("CARGO_MANIFEST_DIR"), "/../../tests")).expect("fixture directory should be readable") {
		let path = path.expect("fixture directory entry should be readable").path();
		if path.extension().is_none_or(|extension| extension != "esx") {
			continue;
		}

		parse_test(&path);
	}
}


#[test]
fn parse_nan() {
	assert!(matches!(esexpr_text::parse(" #float32:nan ").expect("NaN literal should parse"), ESExpr::Float32(f) if f.is_nan()));
	assert!(matches!(esexpr_text::parse(" #float64:nan ").expect("NaN literal should parse"), ESExpr::Float64(f) if f.is_nan()));
}
