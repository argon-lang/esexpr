#![expect(missing_docs, reason = "Integration tests")]

use esexpr::ESExpr;
use esexpr::cowstr::CowStr;
use esexpr_binary::{ExprGenerator, ExprGeneratorSync, ExprParserSync, StringTableBuilder, parse_sync};

fn literal(value: &str) -> ESExpr<'_> {
	ESExpr::Str(CowStr::Borrowed(value))
}

#[test]
fn records_nested_and_mixed_roles() {
	let mut builder = StringTableBuilder::new();
	builder.record(&ESExpr::constructor(
		"outer",
		[ESExpr::constructor("inner", [literal("shared"), literal("once")], [])],
		[("shared".into(), literal("shared"))],
	));
	assert_eq!(builder.build(), ["shared", "inner", "outer"]);
	assert_eq!(StringTableBuilder::default().build(), Vec::<String>::new());
}

#[test]
fn dedicated_names_only_count_as_keywords_and_literals() {
	let mut builder = StringTableBuilder::new();
	for name in ["list", "map", "set", "string-table"] {
		builder.record(&ESExpr::constructor(name, [], []));
	}
	builder.record(&ESExpr::constructor(
		"list",
		[literal("map"), literal("map")],
		[("set".into(), ESExpr::Bool(true))],
	));
	assert_eq!(builder.build(), ["map", "set"]);
}

#[test]
fn literal_costs_and_frequency_order() {
	let mut builder = StringTableBuilder::new();
	for value in [
		"",
		"x",
		"ab",
		"é",
		"only once even though long",
		"",
		"x",
		"ab",
		"é",
		"x",
	] {
		builder.record(&literal(value));
	}
	// Two one-byte literals break even; three save bytes. UTF-8 costs bytes.
	assert_eq!(builder.build(), ["x", "ab", "é"]);
	let mut builder = StringTableBuilder::new();
	builder.record(&literal("x"));
	builder.record(&literal("x"));
	assert_eq!(builder.build(), Vec::<String>::new());
}

fn mandatory_prefix(builder: &mut StringTableBuilder, count: usize) {
	for i in 0..count {
		let name = format!("a{i:04}");
		for _ in 0..5 {
			builder.record(&ESExpr::constructor(CowStr::Borrowed(&name), [], []));
		}
	}
}

#[test]
fn selection_accounts_for_index_width() {
	for (prefix, value, included) in [
		(15, "zz", true),
		(16, "zz", false),
		(2047, "zzz", true),
		(2048, "zzz", false),
	] {
		let mut builder = StringTableBuilder::new();
		mandatory_prefix(&mut builder, prefix);
		for _ in 0..3 {
			builder.record(&literal(value));
		}
		let table = builder.build();
		assert_eq!(table.iter().any(|s| s == value), included, "prefix {prefix}");
	}
}

fn compacted_stream(expressions: &[ESExpr<'_>]) -> Vec<u8> {
	let mut builder = StringTableBuilder::new();
	for expr in expressions {
		builder.record(expr);
	}
	let mut bytes = Vec::new();
	let mut generator = ExprGenerator::new(&mut bytes);
	generator.add_strings(builder.build()).expect("table should encode");
	for expr in expressions {
		generator.generate(expr).expect("expression should encode");
	}
	bytes
}

#[test]
fn compacted_stream_uses_references_and_decodes_with_fresh_reader() {
	let expressions = vec![ESExpr::constructor(
		"custom",
		[literal("repeated long value"), literal("repeated long value")],
		[("key".into(), literal("once"))],
	)];
	let bytes = compacted_stream(&expressions);
	assert!(bytes.windows(2).any(|pair| pair == [0x80, 0x80]));
	let actual = parse_sync(&mut bytes.as_slice())
		.iter_static()
		.collect::<Result<Vec<_>, _>>()
		.expect("fresh reader should decode");
	assert_eq!(actual, expressions);
	let mut ordinary = Vec::new();
	ExprGenerator::new(&mut ordinary)
		.generate(&expressions[0])
		.expect("ordinary encoding");
	assert!(bytes.len() < ordinary.len());
}

#[test]
fn additions_preserve_indices_and_skip_duplicates() {
	let mut bytes = Vec::new();
	let mut generator = ExprGenerator::new(&mut bytes);
	generator.add_strings(Vec::new()).expect("empty addition");
	generator
		.add_strings(vec!["first".into(), "first".into()])
		.expect("single addition");
	generator.generate(&literal("first")).expect("first reference");
	generator
		.add_strings(vec!["first".into(), "second".into(), "third".into(), "second".into()])
		.expect("multiple addition");
	generator.add_strings(vec!["third".into()]).expect("existing addition");
	for value in ["first", "second", "third"] {
		generator.generate(&literal(value)).expect("reference");
	}
	assert_eq!(
		bytes,
		[
			0xEB, 0x65, b'f', b'i', b'r', b's', b't', 0x80, 0xEB, 0xE6, 0x66, b's', b'e', b'c', b'o', b'n', b'd', 0x65,
			b't', b'h', b'i', b'r', b'd', 0xE0, 0x80, 0x81, 0x82
		]
	);
	let actual = parse_sync(&mut bytes.as_slice())
		.iter_static()
		.collect::<Result<Vec<_>, _>>()
		.expect("fresh reader");
	assert_eq!(
		actual,
		[literal("first"), literal("first"), literal("second"), literal("third")]
	);
}

#[test]
fn references_use_actual_index_width_and_inline_fallback() {
	for (index, value, expected) in [
		(15, "zz", vec![0x8F]),
		(16, "zz", vec![0x90, 0x01]),
		(2047, "zz", vec![0x9F, 0x7F]),
		(2048, "zz", vec![0x90, 0x80, 0x01]),
		(2048, "x", vec![0x61, b'x']),
	] {
		let mut pool: Vec<_> = (0..index).map(|i| format!("entry{i}")).collect();
		pool.push(value.into());
		let mut bytes = Vec::new();
		ExprGenerator::new_with_string_pool(&mut bytes, pool)
			.generate(&literal(value))
			.expect("preseeded reference");
		assert_eq!(bytes, expected);
	}
}

proptest::proptest! {
	#[test]
	fn compacted_expressions_round_trip(expressions in proptest::collection::vec(proptest::arbitrary::any::<ESExpr<'static>>(), 0..16)) {
		let bytes = compacted_stream(&expressions);
		let actual = parse_sync(&mut bytes.as_slice()).iter_static().collect::<Result<Vec<_>, _>>()
			.map_err(|e| proptest::test_runner::TestCaseError::fail(format!("{e:?}")))?;
		proptest::prop_assert_eq!(actual, expressions);
	}
}

#[cfg(feature = "async")]
#[tokio::test]
async fn async_compaction_and_additions_match_sync() {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync, parse_async};
	let expressions = [ESExpr::constructor(
		"custom",
		[literal("repeated"), literal("repeated")],
		[("key".into(), literal("custom"))],
	)];
	let mut builder = StringTableBuilder::new();
	builder.record(&expressions[0]);
	let table = builder.build();
	let mut sync = Vec::new();
	let mut generator = ExprGenerator::new(&mut sync);
	ExprGeneratorSync::add_strings(&mut generator, Vec::new()).expect("empty");
	ExprGeneratorSync::add_strings(&mut generator, vec!["existing".into()]).expect("single");
	ExprGeneratorSync::add_strings(&mut generator, table.clone()).expect("table");
	ExprGeneratorSync::add_strings(&mut generator, table.clone()).expect("duplicate table");
	ExprGeneratorSync::generate(&mut generator, &expressions[0]).expect("expression");
	ExprGeneratorSync::add_strings(&mut generator, vec!["existing".into(), "extra".into(), "extra".into()])
		.expect("later addition");
	ExprGeneratorSync::generate(&mut generator, &literal("extra")).expect("extra");
	let mut bytes = Vec::new();
	let mut generator = ExprGenerator::new(&mut bytes);
	ExprGeneratorAsync::add_strings(&mut generator, Vec::new())
		.await
		.expect("empty");
	ExprGeneratorAsync::add_strings(&mut generator, vec!["existing".into()])
		.await
		.expect("single");
	ExprGeneratorAsync::add_strings(&mut generator, table.clone())
		.await
		.expect("table");
	ExprGeneratorAsync::add_strings(&mut generator, table)
		.await
		.expect("duplicate table");
	ExprGeneratorAsync::generate(&mut generator, &expressions[0])
		.await
		.expect("expression");
	ExprGeneratorAsync::add_strings(&mut generator, vec!["existing".into(), "extra".into(), "extra".into()])
		.await
		.expect("later addition");
	ExprGeneratorAsync::generate(&mut generator, &literal("extra"))
		.await
		.expect("extra");
	assert_eq!(bytes, sync);
	let mut input = bytes.as_slice();
	let mut parser = parse_async(&mut input);
	assert_eq!(
		parser.read_next_expr().await.expect("fresh async reader"),
		expressions[0]
	);
	assert_eq!(parser.read_next_expr().await.expect("extra"), literal("extra"));
}

#[test]
fn tie_order_is_independent_of_recording_order() {
	let mut first = StringTableBuilder::new();
	let mut second = StringTableBuilder::new();
	for name in ["c", "b", "a"] {
		first.record(&ESExpr::constructor(name, [], []));
	}
	for name in ["a", "b", "c"] {
		second.record(&ESExpr::constructor(name, [], []));
	}
	assert_eq!(first.build(), second.build());
}

#[test]
fn literal_length_tags_match_encoding_costs() {
	for len in [15, 16, 2047, 2048] {
		let value = "z".repeat(len);
		let mut builder = StringTableBuilder::new();
		builder.record(&literal(&value));
		builder.record(&literal(&value));
		let table = builder.build();
		assert_eq!(table.as_slice(), std::slice::from_ref(&value));
		let mut bytes = Vec::new();
		let mut generator = ExprGenerator::new(&mut bytes);
		generator.add_strings(table).expect("length tag should encode");
		generator.generate(&literal(&value)).expect("reference should encode");
		assert_eq!(bytes.last(), Some(&0x80));
		assert_eq!(
			parse_sync(&mut bytes.as_slice())
				.read_next_expr()
				.expect("length tag should decode"),
			literal(&value)
		);
	}
}
