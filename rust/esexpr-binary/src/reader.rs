use alloc::string::String;
use alloc::vec::Vec;

use esexpr::{ESExpr, ESExprCodec};
use num_bigint::BigUint;

use crate::append_only_string_list::AppendOnlyStringList;

/// `ESExpr` binary format parse error.
#[derive(Debug)]
pub enum ParseError<IOError> {
	/// Invalid token byte.
	InvalidTokenByte(u8),

	/// Invalid string table index.
	InvalidStringTableIndex,

	/// Invalid length.
	InvalidLength,

	/// Unexpected keyword token.
	UnexpectedKeywordToken,

	/// Unexpected constructor end.
	UnexpectedConstructorEnd,

	/// Unexpected end of file.
	UnexpectedEndOfFile,

	/// Invalid string pool.
	InvalidStringPool(esexpr::DecodeError),

	/// IO error.
	IOError(IOError),

	/// Utf8 error.
	Utf8Error(core::str::Utf8Error),
}

#[cfg(feature = "std")]
impl From<std::io::Error> for ParseError<std::io::Error> {
	fn from(value: std::io::Error) -> Self {
		ParseError::IOError(value)
	}
}

impl<IOError> From<core::str::Utf8Error> for ParseError<IOError> {
	fn from(err: core::str::Utf8Error) -> Self {
		ParseError::Utf8Error(err)
	}
}

impl<IOError> From<alloc::string::FromUtf8Error> for ParseError<IOError> {
	fn from(value: alloc::string::FromUtf8Error) -> Self {
		ParseError::Utf8Error(value.utf8_error())
	}
}

#[derive(ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "string-table"]
struct FixedStringPool {
	#[vararg]
	pub strings: Vec<String>,
}

#[derive(ESExprCodec, Debug, PartialEq, Clone)]
enum AppendedStringPool {
	#[inline_value]
	Fixed(FixedStringPool),

	#[inline_value]
	Single(String),
}

enum ExprPlus<'a> {
	Expr(ESExpr<'a>),
	Keyword(usize),
	ConstructorEnd,
	AppendedToStringTable,
	EndOfFile,
}

fn get_string_table_index<E>(i: BigUint) -> Result<usize, ParseError<E>> {
	i.try_into().map_err(|_| ParseError::InvalidStringTableIndex)
}

fn get_length<E>(i: BigUint) -> Result<usize, ParseError<E>> {
	i.try_into().map_err(|_| ParseError::InvalidLength)
}

fn get_string<'a, E>(string_pool: &'a AppendOnlyStringList, i: usize) -> Result<&'a str, ParseError<E>> {
	string_pool.get(i).ok_or(ParseError::InvalidStringTableIndex)
}

macro_rules! reader_mod {
	($syncness: ident) => {
		use alloc::borrow::{Cow, ToOwned};
		use alloc::collections::BTreeMap;
		use alloc::vec;

		use esexpr::cowstr::CowStr;
		use num_bigint::{BigInt, Sign};

		use crate::async_macros::{do_await, if_async, maybe_async};
		use crate::format::*;

		maybe_async!(
			$syncness,
			pub(super) fn read_token_impl<E>(reader: &mut impl Read<E>) -> Result<Option<ExprToken>, ParseError<E>> {
				let mut b: [u8; 1] = [0];

				if do_await!($syncness, reader.read(&mut b)).map_err(ParseError::IOError)? == 0 {
					return Ok(None);
				}

				let b = b[0];

				Ok(Some(if (b & TAG_VARINT_MASK) == TAG_VARINT_MASK {
					match b {
						TAG_CONSTRUCTOR_END => ExprToken::ConstructorEnd,
						TAG_TRUE => ExprToken::BooleanValue(true),
						TAG_FALSE => ExprToken::BooleanValue(false),
						TAG_NULL0 => ExprToken::NullValue(BigUint::ZERO),
						TAG_NULL1 => ExprToken::NullValue(BigUint::from(1u32)),
						TAG_NULL2 => ExprToken::NullValue(BigUint::from(2u32)),
						TAG_NULLN => {
							let n = do_await!($syncness, read_int_full(reader))?;
							ExprToken::NullValue(n + 3u32)
						},
						TAG_FLOAT32 => {
							let buffer: [u8; 4] = do_await!($syncness, read_bytes(reader))?;
							ExprToken::Float32Value(f32::from_le_bytes(buffer))
						},
						TAG_FLOAT64 => {
							let buffer: [u8; 8] = do_await!($syncness, read_bytes(reader))?;
							ExprToken::Float64Value(f64::from_le_bytes(buffer))
						},
						TAG_CONSTRUCTOR_START_STRING_TABLE => ExprToken::ConstructorStartKnown("string-table"),
						TAG_CONSTRUCTOR_START_LIST => ExprToken::ConstructorStartKnown("list"),
						TAG_APPEND_STRING_TABLE => ExprToken::AppendStringTable,
						_ => {
							return Err(ParseError::InvalidTokenByte(b));
						},
					}
				}
				else {
					let tag = match b & TAG_VARINT_MASK {
						TAG_VARINT_CONSTRUCTOR_START => VarIntTag::ConstructorStart,
						TAG_VARINT_NON_NEG_INT => VarIntTag::NonNegIntValue,
						TAG_VARINT_NEG_INT => VarIntTag::NegIntValue,
						TAG_VARINT_STRING_LENGTH => VarIntTag::StringLengthValue,
						TAG_VARINT_STRING_POOL => VarIntTag::StringPoolValue,
						TAG_VARINT_BYTES_LENGTH => VarIntTag::BytesLengthValue,
						TAG_VARINT_KEYWORD => VarIntTag::KeywordArgument,
						_ => panic!("Should not be reachable"),
					};

					let mut n = do_await!($syncness, read_int(reader, b))?;

					match tag {
						VarIntTag::ConstructorStart => ExprToken::ConstructorStart(get_string_table_index(n)?),
						VarIntTag::NonNegIntValue => ExprToken::IntValue(BigInt::from_biguint(Sign::Plus, n)),
						VarIntTag::NegIntValue => {
							n += 1u32;
							ExprToken::IntValue(BigInt::from_biguint(Sign::Minus, n))
						},
						VarIntTag::StringLengthValue => {
							let len = get_length(n)?;
							let mut buff = vec![0u8; len];
							do_await!($syncness, read_exact(reader, &mut buff))?;
							ExprToken::StringValue(String::from_utf8(buff)?.to_owned())
						},
						VarIntTag::StringPoolValue => ExprToken::StringPoolValue(get_string_table_index(n)?),
						VarIntTag::BytesLengthValue => {
							let len = get_length(n)?;
							let mut buff = vec![0u8; len];
							do_await!($syncness, read_exact(reader, &mut buff))?;
							ExprToken::BinaryValue(buff)
						},
						VarIntTag::KeywordArgument => ExprToken::Keyword(get_string_table_index(n)?),
					}
				}))
			}
		);

		maybe_async!(
			$syncness,
			fn read_int<E>(reader: &mut impl Read<E>, initial: u8) -> Result<BigUint, ParseError<E>> {
				let current = initial & 0x0F;
				let bit_offset = 4;
				let has_next = (initial & 0x10) == 0x10;

				do_await!($syncness, read_int_rest(reader, current, bit_offset, has_next))
			}
		);

		maybe_async!(
			$syncness,
			fn read_int_full<E>(reader: &mut impl Read<E>) -> Result<BigUint, ParseError<E>> {
				let current = 0;
				let bit_offset = 0;
				let has_next = true;

				do_await!($syncness, read_int_rest(reader, current, bit_offset, has_next))
			}
		);

		maybe_async!(
			$syncness,
			fn read_int_rest<E>(
				reader: &mut impl Read<E>,
				mut current: u8,
				mut bit_offset: i32,
				mut has_next: bool,
			) -> Result<BigUint, ParseError<E>> {
				let mut buffer = Vec::new();

				while has_next {
					let b = do_await!($syncness, read_byte(reader))?;

					has_next = (b & 0x80) == 0x80;

					let value = b & 0x7F;
					let low = value << bit_offset;
					let high = if bit_offset > 1 {
						value >> (8 - bit_offset)
					}
					else {
						0
					};

					current |= low;
					bit_offset += 7;
					if bit_offset >= 8 {
						bit_offset -= 8;
						buffer.push(current);
						current = high;
					}
				}

				if bit_offset > 0 {
					buffer.push(current);
				}

				Ok(BigUint::from_bytes_le(&buffer))
			}
		);

		maybe_async!(
			$syncness,
			fn read_bytes<E, const N: usize>(reader: &mut impl Read<E>) -> Result<[u8; N], ParseError<E>> {
				let mut b: [u8; N] = [0; N];
				do_await!($syncness, read_exact(reader, &mut b))?;
				Ok(b)
			}
		);

		maybe_async!(
			$syncness,
			fn read_exact<E>(reader: &mut impl Read<E>, mut buf: &mut [u8]) -> Result<(), ParseError<E>> {
				while !buf.is_empty() {
					let n = do_await!($syncness, reader.read(buf)).map_err(ParseError::IOError)?;
					if n == 0 {
						return Err(ParseError::UnexpectedEndOfFile);
					}

					buf = &mut buf[n..];
				}

				Ok(())
			}
		);

		maybe_async!(
			$syncness,
			fn read_byte<E>(reader: &mut impl Read<E>) -> Result<u8, ParseError<E>> {
				Ok(do_await!($syncness, read_bytes::<E, 1>(reader))?[0])
			}
		);

		/// An expression parser
		pub trait ExprParser<E> {
			maybe_async!(
				$syncness,
				/// Try to read the next expression.
				///
				/// # Errors
				/// Returns `Err` if an error occurs during parsing.
				fn try_read_next_expr<'a>(&'a mut self) -> Result<Option<ESExpr<'a>>, ParseError<E>>;
			);

			maybe_async!(
				$syncness,
				/// Read the next expression
				///
				/// # Errors
				/// Returns `Err` if an error occurs during parsing, or if the end of the input is reached.
				fn read_next_expr<'a>(&'a mut self) -> Result<ESExpr<'a>, ParseError<E>>;
			);

			/// Read all expressions, copying values when needed.
			fn iter_static(
				&mut self,
			) -> if_async!(
				$syncness,
				impl Stream<Item = Result<ESExpr<'static>, ParseError<E>>>,
				impl Iterator<Item = Result<ESExpr<'static>, ParseError<E>>>
			) {
				if_async!(
					$syncness,
					stream::poll_fn(|ctx| core::pin::pin!(async {
						self.try_read_next_expr()
							.await
							.map(|res| res.map(ESExpr::into_owned))
							.transpose()
					})
					.poll(ctx)),
					core::iter::from_fn(move || {
						self.try_read_next_expr()
							.map(|res| res.map(ESExpr::into_owned))
							.transpose()
					})
				)
			}
		}

		struct ExprParserImpl<I> {
			string_pool: AppendOnlyStringList,
			iter: I,
		}

		impl<E, I: IterLike<Item = Result<ExprToken, ParseError<E>>> + Unpin> ExprParser<E> for ExprParserImpl<I> {
			maybe_async!(
				$syncness,
				fn try_read_next_expr<'a>(&'a mut self) -> Result<Option<ESExpr<'a>>, ParseError<E>> {
					do_await!(
						$syncness,
						try_read_next_expr_impl(&mut self.iter, &self.string_pool)
					)
				}
			);

			maybe_async!(
				$syncness,
				fn read_next_expr<'a>(&'a mut self) -> Result<ESExpr<'a>, ParseError<E>> {
					do_await!($syncness, read_next_expr_impl(&mut self.iter, &self.string_pool))
				}
			);
		}

		maybe_async!(
			$syncness,
			fn try_read_next_expr_impl<'a, E>(
				iter: &mut (impl IterLike<Item = Result<ExprToken, ParseError<E>>> + Unpin),
				string_pool: &'a AppendOnlyStringList,
			) -> Result<Option<ESExpr<'a>>, ParseError<E>> {
				loop {
					return match do_await!($syncness, read_expr_plus(iter, string_pool))? {
						ExprPlus::Expr(expr) => Ok(Some(expr)),
						ExprPlus::Keyword(_) => Err(ParseError::UnexpectedKeywordToken),
						ExprPlus::ConstructorEnd => Err(ParseError::UnexpectedConstructorEnd),
						ExprPlus::AppendedToStringTable => continue,
						ExprPlus::EndOfFile => Ok(None),
					};
				}
			}
		);

		maybe_async!(
			$syncness,
			fn read_next_expr_impl<'a, E>(
				iter: &mut (impl IterLike<Item = Result<ExprToken, ParseError<E>>> + Unpin),
				string_pool: &'a AppendOnlyStringList,
			) -> Result<ESExpr<'a>, ParseError<E>> {
				do_await!($syncness, try_read_next_expr_impl(iter, string_pool))?.ok_or(ParseError::UnexpectedEndOfFile)
			}
		);

		maybe_async!(
			$syncness,
			fn read_expr_plus<'a, 'b, E>(
				iter: &'b mut (impl IterLike<Item = Result<ExprToken, ParseError<E>>> + Unpin),
				string_pool: &'a AppendOnlyStringList,
			) -> Result<ExprPlus<'a>, ParseError<E>>
			where
				'a: 'b,
			{
				let Some(token) = do_await!($syncness, iter.next()).transpose()?
				else {
					return Ok(ExprPlus::EndOfFile);
				};

				let expr: ExprPlus<'a> = ExprPlus::Expr(match token {
					ExprToken::ConstructorStart(index) => {
						let name = get_string(string_pool, index)?;
						do_await!($syncness, read_expr_constructor(iter, string_pool, CowStr::Borrowed(name)))?
					},
					ExprToken::ConstructorStartKnown(name) => {
						do_await!($syncness, read_expr_constructor(iter, string_pool, CowStr::Static(name)))?
					},
					ExprToken::ConstructorEnd => return Ok(ExprPlus::ConstructorEnd),
					ExprToken::Keyword(index) => return Ok(ExprPlus::Keyword(index)),
					ExprToken::IntValue(i) => ESExpr::Int(Cow::Owned(i)),
					ExprToken::StringValue(s) => ESExpr::Str(CowStr::Owned(s)),
					ExprToken::StringPoolValue(index) => ESExpr::Str(CowStr::Borrowed(get_string(string_pool, index)?)),
					ExprToken::BinaryValue(b) => ESExpr::Binary(Cow::Owned(b)),
					ExprToken::Float32Value(f) => ESExpr::Float32(f),
					ExprToken::Float64Value(d) => ESExpr::Float64(d),
					ExprToken::BooleanValue(b) => ESExpr::Bool(b),
					ExprToken::NullValue(level) => ESExpr::Null(Cow::Owned(level)),
					ExprToken::AppendStringTable => {
						let new_string_table = do_await!($syncness, read_next_expr_impl(iter, string_pool))?;
						let new_string_table = AppendedStringPool::decode_esexpr(new_string_table)
							.map_err(ParseError::InvalidStringPool)?;

						match new_string_table {
							AppendedStringPool::Fixed(mut fixed_string_pool) => {
								string_pool.append(&mut fixed_string_pool.strings)
							},

							AppendedStringPool::Single(s) => string_pool.push(s),
						}

						return Ok(ExprPlus::AppendedToStringTable);
					},
				});

				Ok(expr)
			}
		);

		maybe_async!(
			$syncness,
			fn read_expr_constructor<'a, 'b, E>(
				iter: &'b mut (impl IterLike<Item = Result<ExprToken, ParseError<E>>> + Unpin),
				string_pool: &'a AppendOnlyStringList,
				name: CowStr<'a>,
			) -> Result<ESExpr<'a>, ParseError<E>> {
				let mut args = Vec::new();
				let mut kwargs = BTreeMap::new();

				loop {
					match do_await!($syncness, read_expr_plus(iter, string_pool))? {
						ExprPlus::Expr(expr) => args.push(expr),
						ExprPlus::Keyword(index) => {
							let kw = get_string(string_pool, index)?;
							let value = do_await!($syncness, read_next_expr_impl(iter, string_pool))?;
							kwargs.insert(CowStr::Borrowed(kw), value);
						},
						ExprPlus::ConstructorEnd => break,
						ExprPlus::AppendedToStringTable => {},
						ExprPlus::EndOfFile => return Err(ParseError::UnexpectedEndOfFile),
					}
				}

				Ok(ESExpr::constructor(name, args, kwargs))
			}
		);

		/// Parse binary input as `ESExpr` using an existing string pool
		pub fn parse_existing_string_pool<'a, R: Read<E>, E: 'static>(
			data: &'a mut R,
			string_pool: Vec<String>,
		) -> impl ExprParser<E> {
			ExprParserImpl {
				iter: if_async!($syncness, Box::pin(token_reader(data)), token_reader(data)),
				string_pool: AppendOnlyStringList::from(string_pool),
			}
		}

		/// Parse binary input as `ESExpr`
		pub fn parse<E: 'static>(data: &mut impl Read<E>) -> impl ExprParser<E> {
			parse_existing_string_pool(data, Vec::new())
		}
	};
}

mod reader_sync {
	use core::iter::{self, Iterator as IterLike, Iterator};

	use super::*;
	use crate::io::Read;

	fn token_reader<E, R: Read<E>>(read: &mut R) -> impl Iterator<Item = Result<ExprToken, ParseError<E>>> {
		iter::from_fn(|| read_token_impl(read).transpose())
	}

	reader_mod!(sync);
}

#[allow(async_fn_in_trait, reason = "No additional traits to add")]
mod reader_async {
	use alloc::boxed::Box;

	use futures::{Stream as IterLike, Stream, StreamExt, stream};

	use super::*;
	use crate::io::AsyncRead as Read;

	fn token_reader<E>(read: &mut impl Read<E>) -> impl Stream<Item = Result<ExprToken, ParseError<E>>> {
		stream::poll_fn(|ctx| core::pin::pin!(async { read_token_impl(read).await.transpose() }).poll(ctx))
	}

	reader_mod!(async);
}

pub use reader_async::{
	ExprParser as ExprParserAsync,
	parse as parse_async,
	parse_existing_string_pool as parse_existing_string_pool_async,
};
pub use reader_sync::{
	ExprParser as ExprParserSync,
	parse as parse_sync,
	parse_existing_string_pool as parse_existing_string_pool_sync,
};
