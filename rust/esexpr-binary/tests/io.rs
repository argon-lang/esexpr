#![expect(missing_docs, reason = "Integration tests")]

use std::borrow::Cow;

use embedded_io::{ErrorKind, ErrorType, Read, Write};
use esexpr::{ESExpr, ESExprCodec};
use esexpr_binary::{ExprGenerator, ExprGeneratorSync, ExprParserSync, GeneratorError, ParseError, parse_sync};
use half::f16;

struct ChunkedIo<'a> {
	input: &'a [u8],
	output: Vec<u8>,
	remaining: Option<usize>,
}

impl ErrorType for ChunkedIo<'_> {
	type Error = ErrorKind;
}

impl ChunkedIo<'_> {
	fn operation(&mut self) -> Result<(), ErrorKind> {
		if let Some(remaining) = &mut self.remaining {
			if *remaining == 0 {
				return Err(ErrorKind::Other);
			}
			*remaining -= 1;
		}
		Ok(())
	}
}

impl Read for ChunkedIo<'_> {
	fn read(&mut self, bytes: &mut [u8]) -> Result<usize, ErrorKind> {
		self.operation()?;
		let n = bytes.len().min(self.input.len()).min(1);
		bytes[..n].copy_from_slice(&self.input[..n]);
		self.input = &self.input[n..];
		Ok(n)
	}
}

impl Write for ChunkedIo<'_> {
	fn write(&mut self, bytes: &[u8]) -> Result<usize, ErrorKind> {
		self.operation()?;
		let n = bytes.len().min(1);
		self.output.extend_from_slice(&bytes[..n]);
		Ok(n)
	}
	fn flush(&mut self) -> Result<(), ErrorKind> {
		Ok(())
	}
}

#[cfg(feature = "async")]
impl embedded_io_async::Read for ChunkedIo<'_> {
	fn read(&mut self, bytes: &mut [u8]) -> impl std::future::Future<Output = Result<usize, ErrorKind>> {
		std::future::ready(Read::read(self, bytes))
	}
}

#[cfg(feature = "async")]
impl embedded_io_async::Write for ChunkedIo<'_> {
	fn write(&mut self, bytes: &[u8]) -> impl std::future::Future<Output = Result<usize, ErrorKind>> {
		std::future::ready(Write::write(self, bytes))
	}
	fn flush(&mut self) -> impl std::future::Future<Output = Result<(), ErrorKind>> {
		std::future::ready(Ok(()))
	}
}

fn expression() -> ESExpr<'static> {
	ESExpr::constructor(
		"chunked",
		[
			ESExpr::Str("multiple bytes".into()),
			42i32.encode_esexpr(),
			ESExpr::Float16(f16::from_f32(1.5)),
			ESExpr::Float32(1.5),
			ESExpr::Array8(Cow::Owned(vec![1, 2])),
			ESExpr::Array16(Cow::Owned(vec![1, 2])),
			ESExpr::Array32(Cow::Owned(vec![1, 2])),
			ESExpr::Array64(Cow::Owned(vec![1, 2])),
			ESExpr::Array128(Cow::Owned(vec![1, 2])),
		],
		[("key".into(), ESExpr::Float64(1.5))],
	)
}

#[test]
fn sync_short_reads_and_writes() {
	let expected = expression();
	let mut writer = ChunkedIo {
		input: &[],
		output: Vec::new(),
		remaining: None,
	};
	ExprGenerator::new(&mut writer)
		.generate(&expected)
		.expect("short writes should be retried");
	let mut reader = ChunkedIo {
		input: &writer.output,
		output: Vec::new(),
		remaining: None,
	};
	assert_eq!(
		parse_sync(&mut reader)
			.read_next_expr()
			.expect("short reads should be retried"),
		expected
	);
}

#[test]
fn sync_io_errors_propagate() {
	let expected = expression();
	for remaining in [0, 1, 5, 15] {
		let mut writer = ChunkedIo {
			input: &[],
			output: Vec::new(),
			remaining: Some(remaining),
		};
		assert!(matches!(
			ExprGenerator::new(&mut writer).generate(&expected),
			Err(GeneratorError::IOError(ErrorKind::Other))
		));
		let mut reader = ChunkedIo {
			input: &[0xE5, 0, 0, 0, 0, 0, 0, 0, 0],
			output: Vec::new(),
			remaining: Some(remaining.min(5)),
		};
		assert!(matches!(
			parse_sync(&mut reader).read_next_expr(),
			Err(ParseError::IOError(ErrorKind::Other))
		));
	}
}

#[cfg(feature = "async")]
#[tokio::test]
async fn async_short_reads_and_writes() {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync, parse_async};
	let expected = expression();
	let mut writer = ChunkedIo {
		input: &[],
		output: Vec::new(),
		remaining: None,
	};
	ExprGeneratorAsync::generate(&mut ExprGenerator::new(&mut writer), &expected)
		.await
		.expect("short writes should be retried");
	let mut reader = ChunkedIo {
		input: &writer.output,
		output: Vec::new(),
		remaining: None,
	};
	assert_eq!(
		parse_async(&mut reader)
			.read_next_expr()
			.await
			.expect("short reads should be retried"),
		expected
	);
}

#[cfg(feature = "async")]
#[tokio::test]
async fn async_io_errors_propagate() {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync, parse_async};
	let expected = expression();
	for remaining in [0, 1, 5, 15] {
		let mut writer = ChunkedIo {
			input: &[],
			output: Vec::new(),
			remaining: Some(remaining),
		};
		assert!(matches!(
			ExprGeneratorAsync::generate(&mut ExprGenerator::new(&mut writer), &expected).await,
			Err(GeneratorError::IOError(ErrorKind::Other))
		));
		let mut reader = ChunkedIo {
			input: &[0xE5, 0, 0, 0, 0, 0, 0, 0, 0],
			output: Vec::new(),
			remaining: Some(remaining.min(5)),
		};
		assert!(matches!(
			parse_async(&mut reader).read_next_expr().await,
			Err(ParseError::IOError(ErrorKind::Other))
		));
	}
}

fn encoded_expressions() -> Vec<Vec<u8>> {
	use num_bigint::{BigInt, BigUint};
	let mut expressions = vec![
		expression(),
		ESExpr::Bool(true),
		ESExpr::Bool(false),
		ESExpr::constructor("string-table", [], []),
		ESExpr::constructor("list", [], []),
		ESExpr::constructor("map", [], []),
		ESExpr::constructor("set", [], []),
		ESExpr::constructor("one-new-pool-entry", [], []),
		ESExpr::Int(Cow::Owned(BigInt::from(1u8) << 256usize)),
		ESExpr::Int(Cow::Owned(-(BigInt::from(1u8) << 256usize))),
		ESExpr::Null(Cow::Owned(BigUint::ZERO)),
		ESExpr::Null(Cow::Owned(1u8.into())),
		ESExpr::Null(Cow::Owned(2u8.into())),
		ESExpr::Null(Cow::Owned(3u8.into())),
		ESExpr::Null(Cow::Owned(BigUint::from(1u8) << 256usize)),
	];
	expressions.push(ESExpr::Array8(Cow::Owned(vec![1; 128])));
	expressions
		.into_iter()
		.map(|expr| {
			let mut bytes = Vec::new();
			ExprGenerator::new(&mut bytes)
				.generate(&expr)
				.expect("test expression should encode");
			bytes
		})
		.collect()
}

#[test]
fn sync_io_errors_at_every_stream_byte() {
	for bytes in encoded_expressions() {
		let expected = parse_sync(&mut bytes.as_slice())
			.read_next_expr()
			.expect("baseline should decode")
			.into_owned();
		for offset in 0..bytes.len() {
			let mut writer = ChunkedIo {
				input: &[],
				output: Vec::new(),
				remaining: Some(offset),
			};
			assert!(
				matches!(
					ExprGenerator::new(&mut writer).generate(&expected),
					Err(GeneratorError::IOError(ErrorKind::Other))
				),
				"write offset {offset}"
			);
			assert_eq!(writer.output, bytes[..offset], "partial output at offset {offset}");
			let mut reader = ChunkedIo {
				input: &bytes,
				output: Vec::new(),
				remaining: Some(offset),
			};
			assert!(
				matches!(
					parse_sync(&mut reader).read_next_expr(),
					Err(ParseError::IOError(ErrorKind::Other))
				),
				"read offset {offset}"
			);
		}
	}
}

#[cfg(feature = "async")]
#[tokio::test]
async fn async_io_errors_at_every_stream_byte() {
	use esexpr_binary::{ExprGeneratorAsync, ExprParserAsync, parse_async};
	for bytes in encoded_expressions() {
		let expected = parse_sync(&mut bytes.as_slice())
			.read_next_expr()
			.expect("baseline should decode")
			.into_owned();
		for offset in 0..bytes.len() {
			let mut writer = ChunkedIo {
				input: &[],
				output: Vec::new(),
				remaining: Some(offset),
			};
			assert!(
				matches!(
					ExprGeneratorAsync::generate(&mut ExprGenerator::new(&mut writer), &expected).await,
					Err(GeneratorError::IOError(ErrorKind::Other))
				),
				"write offset {offset}"
			);
			assert_eq!(writer.output, bytes[..offset], "partial output at offset {offset}");
			let mut reader = ChunkedIo {
				input: &bytes,
				output: Vec::new(),
				remaining: Some(offset),
			};
			assert!(
				matches!(
					parse_async(&mut reader).read_next_expr().await,
					Err(ParseError::IOError(ErrorKind::Other))
				),
				"read offset {offset}"
			);
		}
	}
}
