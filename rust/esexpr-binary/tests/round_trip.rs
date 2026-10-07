#![expect(missing_docs, reason = "Integration tests")]

use std::borrow::Cow;

use esexpr::{ESExpr, ESExprCodec};
use esexpr_binary::{ExprGenerator, ExprGeneratorSync, ExprParserSync, parse_existing_string_pool_sync, parse_sync};
use half::f16;
use num_bigint::{BigInt, BigUint};

proptest::proptest! {
	#[test]
	fn arbitrary_expressions_round_trip(
		expected in proptest::collection::vec(proptest::arbitrary::any::<ESExpr<'static>>(), 0..16),
	) {
		let data = encode_sync(&expected);
		let actual = parse_sync(&mut data.as_slice())
			.iter_static()
			.collect::<Result<Vec<_>, _>>()
			.map_err(|error| proptest::test_runner::TestCaseError::fail(format!("{error:?}")))?;
		proptest::prop_assert_eq!(actual, expected);
	}
}

fn expressions() -> Vec<ESExpr<'static>> {
	let mut result = vec![
		ESExpr::Bool(true),
		ESExpr::Bool(false),
		ESExpr::Str("héllo\0".into()),
		ESExpr::Array8(Cow::Owned(vec![0, 255])),
		ESExpr::Array16(Cow::Owned(vec![0, u16::MAX])),
		ESExpr::Array32(Cow::Owned(vec![0, u32::MAX])),
		ESExpr::Array64(Cow::Owned(vec![0, u64::MAX])),
		ESExpr::Array128(Cow::Owned(vec![0, u128::MAX])),
	];
	for name in ["string-table", "list", "map", "set", "custom", "custom", "another"] {
		result.push(ESExpr::constructor(
			name,
			[ESExpr::Bool(true)],
			[("key".into(), ESExpr::Str("value".into()))],
		));
	}
	for n in [0u64, 1, 15, 16, 127, 128, 2047, 2048, u64::MAX] {
		result.push(ESExpr::Int(Cow::Owned(BigInt::from(n))));
		result.push(ESExpr::Int(Cow::Owned(-BigInt::from(n) - 1)));
		result.push(ESExpr::Null(Cow::Owned(BigUint::from(n))));
	}
	result.push(ESExpr::Int(Cow::Owned(BigInt::from(1u8) << 256usize)));
	result.push(ESExpr::Null(Cow::Owned(BigUint::from(1u8) << 256usize)));
	for bits in [0u16, 0x8000, 0x7C00, 0xFC00, 0x7E01, 0xFE02] {
		result.push(ESExpr::Float16(f16::from_bits(bits)));
	}
	for bits in [0u32, 0x8000_0000, 0x7F80_0000, 0xFF80_0000, 0x7FC0_0001, 0xFFC0_0002] {
		result.push(ESExpr::Float32(f32::from_bits(bits)));
	}
	for bits in [
		0u64,
		0x8000_0000_0000_0000,
		0x7FF0_0000_0000_0000,
		0xFFF0_0000_0000_0000,
		0x7FF8_0000_0000_0001,
		0xFFF8_0000_0000_0002,
	] {
		result.push(ESExpr::Float64(f64::from_bits(bits)));
	}
	result
}

fn encode_sync(expressions: &[ESExpr<'_>]) -> Vec<u8> {
	let mut data = Vec::new();
	let mut generator = ExprGenerator::new(&mut data);
	for expr in expressions {
		generator.generate(expr).expect("expression should encode");
	}
	data
}

#[test]
fn persistent_pool_round_trip_sync() {
	let expected = expressions();
	let data = encode_sync(&expected);
	let actual = parse_sync(&mut data.as_slice())
		.iter_static()
		.collect::<Result<Vec<_>, _>>()
		.expect("stream should decode");
	assert_eq!(actual, expected);
}

#[cfg(feature = "async")]
#[tokio::test]
async fn persistent_pool_round_trip_async() {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync, parse_async};
	use futures::TryStreamExt;
	let expected = expressions();
	let mut data = Vec::new();
	let mut generator = ExprGenerator::new(&mut data);
	for expr in &expected {
		ExprGeneratorAsync::generate(&mut generator, expr)
			.await
			.expect("expression should encode");
	}
	assert_eq!(data, encode_sync(&expected), "sync/async encodings should match");
	let actual = parse_async(&mut data.as_slice())
		.iter_static()
		.try_collect::<Vec<_>>()
		.await
		.expect("stream should decode");
	assert_eq!(actual, expected);
}

#[test]
fn preseeded_pool_sync() {
	let pool = vec!["custom".to_owned(), "key".to_owned()];
	let expected = ESExpr::constructor("custom", [42i32.encode_esexpr()], [("key".into(), ESExpr::Bool(true))]);
	let mut data = Vec::new();
	ExprGenerator::new_with_string_pool(&mut data, pool.clone())
		.generate(&expected)
		.expect("preseeded generator");
	assert_eq!(data[0], 0, "existing constructor index needs no pool append");
	let actual = parse_existing_string_pool_sync(&mut data.as_slice(), pool)
		.read_next_expr()
		.expect("preseeded parser")
		.into_owned();
	assert_eq!(actual, expected);
}

#[cfg(feature = "async")]
#[tokio::test]
async fn preseeded_pool_async() {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync, parse_existing_string_pool_async};
	let pool = vec!["custom".to_owned(), "key".to_owned()];
	let expected = ESExpr::constructor("custom", [], [("key".into(), ESExpr::Bool(true))]);
	let mut data = Vec::new();
	ExprGeneratorAsync::generate(
		&mut ExprGenerator::new_with_string_pool(&mut data, pool.clone()),
		&expected,
	)
	.await
	.expect("preseeded generator");
	assert_eq!(data[0], 0);
	let actual = parse_existing_string_pool_async(&mut data.as_slice(), pool)
		.read_next_expr()
		.await
		.expect("preseeded parser")
		.into_owned();
	assert_eq!(actual, expected);
}
