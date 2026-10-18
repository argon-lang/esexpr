#![expect(missing_docs, reason = "Tests")]

use std::fs;
use std::path::{Path, PathBuf};

use esexpr::ESExpr;
use esexpr_binary::ExprParserSync;

async fn encoding_test(esx: &Path) {
	println!(
		"Executing test case {}",
		esx.file_stem()
			.expect("fixture should have a name")
			.to_str()
			.expect("fixture name should be UTF-8")
	);

	let dir = esx.parent().expect("fixture should have a parent directory");

	let mut json = PathBuf::from(dir);
	json.push(format!(
		"{}.json",
		esx.file_stem()
			.expect("fixture should have a name")
			.to_str()
			.expect("fixture name should be UTF-8")
	));
	let json = fs::read_to_string(json).expect("JSON fixture should be readable");
	let json: esexpr_json::JsonEncodedESExpr = deser_json::from_str(&json).expect("JSON fixture should parse");
	let json = match json {
		esexpr_json::JsonEncodedESExpr::List(items) => items,
		_ => vec![json],
	};
	let json = json
		.into_iter()
		.map(esexpr_json::JsonEncodedESExpr::into_esexpr)
		.collect::<Vec<_>>();

	encoding_test_sync(&json, esx);
	encoding_test_async(&json, esx).await;
}

fn encoding_test_sync(expected: &[ESExpr<'static>], path: &Path) {
	use esexpr_binary::ExprGeneratorSync;

	let test_name = path
		.file_stem()
		.expect("fixture should have a name")
		.to_str()
		.expect("fixture name should be UTF-8");

	let esx = {
		let mut file = fs::File::open(path).expect("binary fixture should be readable");
		let mut wrapped_file = embedded_io_adapters::std::FromStd::new(&mut file);
		esexpr_binary::parse_sync(&mut wrapped_file)
			.iter_static()
			.collect::<Result<Vec<_>, _>>()
			.expect("binary expressions should parse")
	};

	assert_eq!(
		expected, esx,
		"Test case {test_name} did not match. Expected: {expected:?}, actual: {esx:?}"
	);

	let mut reencoded = Vec::with_capacity(esx.len());
	for e in &esx {
		let mut data: Vec<u8> = Vec::new();
		let mut eg = esexpr_binary::ExprGenerator::new(&mut data);
		eg.generate(e).expect("expression should encode");

		reencoded.extend(
			esexpr_binary::parse_sync(&mut data.as_slice())
				.iter_static()
				.collect::<Result<Vec<_>, _>>()
				.expect("binary expressions should parse"),
		);
	}
	assert_eq!(reencoded, expected, "sync round trip for {test_name}");
}

async fn encoding_test_async(expected: &[ESExpr<'static>], path: &Path) {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync};
	use futures::TryStreamExt;

	let test_name = path
		.file_stem()
		.expect("fixture should have a name")
		.to_str()
		.expect("fixture name should be UTF-8");

	let esx = {
		let mut file = tokio::fs::File::open(path)
			.await
			.expect("binary fixture should be readable");
		let mut wrapped_file = embedded_io_adapters::tokio_1::FromTokio::new(&mut file);

		esexpr_binary::parse_async(&mut wrapped_file)
			.iter_static()
			.try_collect::<Vec<_>>()
			.await
			.expect("binary expressions should parse")
	};

	assert_eq!(
		expected, esx,
		"Test case {test_name} did not match. Expected: {expected:?}, actual: {esx:?}"
	);

	let mut reencoded = Vec::with_capacity(esx.len());
	for e in &esx {
		let mut data: Vec<u8> = Vec::new();
		let mut eg = esexpr_binary::ExprGenerator::new(&mut data);
		eg.generate(e).await.expect("expression should encode");

		reencoded.extend(
			esexpr_binary::parse_sync(&mut data.as_slice())
				.iter_static()
				.collect::<Result<Vec<_>, _>>()
				.expect("binary expressions should parse"),
		);
	}
	assert_eq!(reencoded, expected, "async round trip for {test_name}");
}

#[tokio::test]
async fn encoding_tests() {
	for path in
		fs::read_dir(concat!(env!("CARGO_MANIFEST_DIR"), "/../../tests")).expect("fixture directory should be readable")
	{
		let path = path.expect("fixture directory entry should be readable").path();
		if path.extension().is_none_or(|extension| extension != "esxb") {
			continue;
		}

		encoding_test(&path).await;
	}
}
