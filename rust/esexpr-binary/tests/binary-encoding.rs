#![expect(missing_docs, reason = "Tests")]

use std::fs;
use std::path::{Path, PathBuf};
use esexpr::ESExpr;
use esexpr_binary::ExprParserSync;

async fn encoding_test(esx: &Path) {
	println!("Executing test case {}", esx.file_stem().unwrap().to_str().unwrap());

	let dir = esx.parent().unwrap();

	let mut json = PathBuf::from(dir);
	json.push(format!("{}.json", esx.file_stem().unwrap().to_str().unwrap()));
	let json = fs::read_to_string(json).unwrap();
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
	encoding_test_async(&json, &esx).await;
}

fn encoding_test_sync(expected: &[ESExpr<'static>], path: &Path) {
	use esexpr_binary::ExprGeneratorSync;

	let test_name = path.file_stem().unwrap().to_str().unwrap();

	let esx = {
		let mut file = fs::File::open(path).unwrap();
		let mut wrapped_file = embedded_io_adapters::std::FromStd::new(&mut file);
		esexpr_binary::parse_sync(&mut wrapped_file)
			.iter_static()
			.collect::<Result<Vec<_>, _>>()
			.unwrap()
	};

	assert_eq!(expected, esx, "Test case {test_name} did not match. Expected: {expected:?}, actual: {esx:?}");

	let mut reencoded = Vec::with_capacity(esx.len());
	for e in &esx {
		let mut data: Vec<u8> = Vec::new();
		let mut eg = esexpr_binary::ExprGenerator::new(&mut data);
		eg.generate(e).unwrap();

		reencoded.extend(
			esexpr_binary::parse_sync(&mut data.as_slice())
				.iter_static()
				.collect::<Result<Vec<_>, _>>()
				.unwrap(),
		);
	}
}

async fn encoding_test_async(expected: &[ESExpr<'static>], path: &Path) {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync};
	use futures::TryStreamExt;
	use futures::StreamExt;

	let test_name = path.file_stem().unwrap().to_str().unwrap();

	let esx = {
		let mut file = tokio::fs::File::open(path).await.unwrap();
		let mut wrapped_file = embedded_io_adapters::tokio_1::FromTokio::new(&mut file);

		esexpr_binary::parse_async(&mut wrapped_file)
			.iter_static()
			.try_collect::<Vec<_>>()
			.await
			.unwrap()
	};

	assert_eq!(expected, esx, "Test case {test_name} did not match. Expected: {expected:?}, actual: {esx:?}");

	let mut reencoded = Vec::with_capacity(esx.len());
	for e in &esx {
		let mut data: Vec<u8> = Vec::new();
		let mut eg = esexpr_binary::ExprGenerator::new(&mut data);
		eg.generate(e).await.unwrap();

		reencoded.extend(
			esexpr_binary::parse_sync(&mut data.as_slice())
				.iter_static()
				.collect::<Result<Vec<_>, _>>()
				.unwrap(),
		);
	}
}

#[tokio::test]
async fn encoding_tests() {
	for path in fs::read_dir(concat!(env!("CARGO_MANIFEST_DIR"), "/../../tests")).unwrap() {
		let path = path.unwrap().path();
		if path.extension().unwrap() != "esxb" {
			continue;
		}

		encoding_test(&path).await;
	}
}
