#![expect(missing_docs, reason = "Integration tests")]

use esexpr_binary::{ExprParserSync, ParseError, parse_sync};

#[derive(Clone, Copy, Debug)]
enum ExpectedError {
	Token(u8),
	Index,
	Length,
	Keyword,
	End,
	Eof,
	Pool,
	Utf8,
}

fn check<E: std::fmt::Debug>(error: &ParseError<E>, expected: ExpectedError) {
	assert!(
		match (error, expected) {
			(ParseError::InvalidTokenByte(actual), ExpectedError::Token(expected)) => *actual == expected,
			(ParseError::InvalidStringTableIndex, ExpectedError::Index) |
			(ParseError::InvalidLength, ExpectedError::Length) |
			(ParseError::UnexpectedKeywordToken, ExpectedError::Keyword) |
			(ParseError::UnexpectedConstructorEnd, ExpectedError::End) |
			(ParseError::UnexpectedEndOfFile, ExpectedError::Eof) |
			(ParseError::InvalidStringPool(_), ExpectedError::Pool) |
			(ParseError::Utf8Error(_), ExpectedError::Utf8) => true,
			_ => false,
		},
		"{error:?} != {expected:?}"
	);
}

fn cases() -> Vec<(Vec<u8>, ExpectedError)> {
	let mut cases = vec![
		(vec![0xFF], ExpectedError::Token(0xFF)),
		(vec![0x80], ExpectedError::Index),
		(vec![0x00], ExpectedError::Index),
		(vec![0xC0], ExpectedError::Keyword),
		(vec![0xE0], ExpectedError::End),
		(vec![0x61, 0xFF], ExpectedError::Utf8),
		(vec![0xEB, 0xE1], ExpectedError::Pool),
		(vec![0x30], ExpectedError::Eof),
		(vec![0x30, 0x80], ExpectedError::Eof),
		(vec![0x62, b'a'], ExpectedError::Eof),
		(vec![0xA2, 1], ExpectedError::Eof),
		(vec![0xEC, 0], ExpectedError::Eof),
		(vec![0xE4, 0, 0], ExpectedError::Eof),
		(vec![0xE5, 0, 0], ExpectedError::Eof),
		(vec![0xEA], ExpectedError::Eof),
		(vec![0xED, 1, 0], ExpectedError::Eof),
		(vec![0xEE, 1, 0], ExpectedError::Eof),
		(vec![0xEF, 1, 0], ExpectedError::Eof),
		(vec![0xF0, 1, 0], ExpectedError::Eof),
		(vec![0xE7, 0xE1], ExpectedError::Eof),
		// Append "k" to the pool, start list, then omit the keyword value.
		(vec![0xEB, 0x61, b'k', 0xE7, 0xC0], ExpectedError::Eof),
	];
	// Encode 2^128, which exceeds usize on every supported host.
	for (tag, expected) in [(0x90, ExpectedError::Index), (0x70, ExpectedError::Length)] {
		let mut bytes = vec![tag];
		bytes.extend([0x80; 17]);
		bytes.push(0x20);
		cases.push((bytes, expected));
	}
	// All variable-length/index forms must reject integers wider than usize.
	// 2^128 in a full varint: eighteen zero continuation groups, then 4.
	let mut full = vec![0x80; 18];
	full.push(4);
	for tag in [0xED, 0xEE, 0xEF, 0xF0] {
		let mut bytes = vec![tag];
		bytes.extend_from_slice(&full);
		cases.push((bytes, ExpectedError::Length));
	}
	for (tag, expected) in [
		(0x10, ExpectedError::Index),
		(0xD0, ExpectedError::Index),
		(0xB0, ExpectedError::Length),
	] {
		let mut bytes = vec![tag];
		bytes.extend([0x80; 17]);
		bytes.push(0x20);
		cases.push((bytes, expected));
	}
	// Constructor and keyword references just beyond a one-entry pool.
	cases.push((vec![0xEB, 0x61, b'k', 0x01], ExpectedError::Index));
	cases.push((vec![0xEB, 0x61, b'k', 0xE7, 0xC1, 0xE1, 0xE0], ExpectedError::Index));
	// Truncations inside a custom constructor or a string-pool append.
	cases.push((vec![0xEB, 0x61, b'k', 0x00], ExpectedError::Eof));
	cases.push((vec![0xEB], ExpectedError::Eof));
	cases.push((vec![0xEB, 0xE7, 0xE0], ExpectedError::Pool));
	cases
}

#[test]
fn malformed_binary_sync() {
	for (data, expected) in cases() {
		let error = parse_sync(&mut data.as_slice())
			.read_next_expr()
			.expect_err("malformed bytes must fail");
		check(&error, expected);
	}
}

#[cfg(feature = "async")]
#[tokio::test]
async fn malformed_binary_async() {
	use esexpr_binary::{ExprParserAsync, parse_async};
	for (data, expected) in cases() {
		let error = parse_async(&mut data.as_slice())
			.read_next_expr()
			.await
			.expect_err("malformed bytes must fail");
		check(&error, expected);
	}
}

#[test]
fn eof_sync() {
	let mut data = &[][..];
	let mut parser = parse_sync(&mut data);
	assert!(parser.try_read_next_expr().expect("clean EOF").is_none());
	assert!(matches!(parser.read_next_expr(), Err(ParseError::UnexpectedEndOfFile)));
	assert!(parse_sync(&mut &[][..]).iter_static().next().is_none());
}

#[cfg(feature = "async")]
#[tokio::test]
async fn eof_async() {
	use esexpr_binary::{ExprParserAsync, parse_async};
	use futures::StreamExt;
	let mut data = &[][..];
	let mut parser = parse_async(&mut data);
	assert!(parser.try_read_next_expr().await.expect("clean EOF").is_none());
	assert!(matches!(
		parser.read_next_expr().await,
		Err(ParseError::UnexpectedEndOfFile)
	));
	let mut empty = &[][..];
	let mut parser = parse_async(&mut empty);
	let stream = parser.iter_static();
	futures::pin_mut!(stream);
	assert!(stream.next().await.is_none());
}

#[cfg(feature = "std")]
#[test]
fn std_io_error_conversion_preserves_kind_and_message() {
	let error: ParseError<std::io::Error> =
		std::io::Error::new(std::io::ErrorKind::PermissionDenied, "test permission failure").into();
	let ParseError::IOError(error) = error
	else {
		panic!("expected I/O error")
	};
	assert_eq!(error.kind(), std::io::ErrorKind::PermissionDenied);
	assert_eq!(error.to_string(), "test permission failure");
}

#[test]
fn utf8_error_conversions_preserve_failure_position() {
	let invalid = vec![b'a', 0xFF];
	let error = String::from_utf8(invalid).expect_err("invalid UTF-8");
	let expected = error.utf8_error();
	let borrowed: ParseError<core::convert::Infallible> = expected.into();
	let owned: ParseError<core::convert::Infallible> = error.into();
	for error in [borrowed, owned] {
		let ParseError::Utf8Error(error) = error
		else {
			panic!("expected UTF-8 error")
		};
		assert_eq!(error.valid_up_to(), 1);
		assert_eq!(error.error_len(), Some(1));
	}
}

#[cfg(feature = "async")]
#[tokio::test]
async fn async_stream_preserves_parse_errors_and_terminates() {
	use esexpr_binary::{ExprParserAsync, parse_async};
	use futures::StreamExt;

	for (data, expected) in cases() {
		let mut input_bytes = vec![0xE1];
		input_bytes.extend(data);
		let mut input = input_bytes.as_slice();
		let mut parser = parse_async(&mut input);
		let stream = parser.iter_static();
		futures::pin_mut!(stream);
		assert_eq!(
			stream.next().await.expect("valid prefix").expect("valid expression"),
			esexpr::ESExpr::Bool(true)
		);
		let error = stream
			.next()
			.await
			.expect("stream must yield the error")
			.expect_err("malformed bytes must fail");
		check(&error, expected);
		assert!(stream.next().await.is_none(), "stream must terminate after an error");
	}
}
