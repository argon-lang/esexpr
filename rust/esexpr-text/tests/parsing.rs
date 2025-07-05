#![expect(missing_docs, reason = "Tests")]

use std::fs;
use std::path::Path;

use esexpr::ESExpr;

fn parse_test(path: &Path) {
	let esx = std::fs::read_to_string(&path).unwrap();
	let esx = esexpr_text::parse_multi(&esx).unwrap();

	let mut json = path.parent().unwrap().to_path_buf();
	json.push(format!("{}.json", path.file_stem().unwrap().to_str().unwrap()));
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

	assert_eq!(esx, json);
}

#[test]
fn parse_tests() {
	for path in fs::read_dir(concat!(env!("CARGO_MANIFEST_DIR"), "/../../tests")).unwrap() {
		let path = path.unwrap().path();
		if path.extension().unwrap() != "esx" {
			continue;
		}

		parse_test(&path);
	}
}


#[test]
fn parse_nan() {
	assert!(matches!(esexpr_text::parse(" #float32:nan ").unwrap(), ESExpr::Float32(f) if f.is_nan()));
	assert!(matches!(esexpr_text::parse(" #float64:nan ").unwrap(), ESExpr::Float64(f) if f.is_nan()));
}
