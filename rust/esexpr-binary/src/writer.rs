use alloc::borrow::ToOwned;
use alloc::string::String;
use alloc::vec::Vec;
use core::convert::Infallible;
use core::marker::PhantomData;

use derive_more::From;

/// Error type for `ExprGenerator`
#[derive(From, Debug)]
pub enum GeneratorError<E> {
	/// An IO error occurred
	IOError(E),
}

impl<E: 'static> GeneratorError<E> {
	/// Convert from an `Infallible` error
	pub fn from_infalliable(error: GeneratorError<Infallible>) -> Self {
		match error {
			GeneratorError::IOError(e) => match e {},
		}
	}
}

/// Generator for `ESExpr`'s binary format
pub struct ExprGenerator<'a, W, E> {
	out: &'a mut W,
	string_pool: Vec<String>,
	error: PhantomData<E>,
}

impl<'a, W, E> ExprGenerator<'a, W, E> {
	/// Create an `ExprGenerator`
	pub fn new(out: &'a mut W) -> Self {
		ExprGenerator {
			out,
			string_pool: Vec::new(),
			error: PhantomData,
		}
	}

	/// Create an `ExprGenerator` with an existing string pool
	pub fn new_with_string_pool(out: &'a mut W, string_pool: Vec<String>) -> Self {
		ExprGenerator {
			out,
			string_pool,
			error: PhantomData,
		}
	}
}

macro_rules! writer_mod {
	($syncness: ident) => {
		use core::convert::Infallible;
		use alloc::borrow::Borrow;

		use esexpr::{ESExpr, ESExprConstructor};
		use num_bigint::{BigUint, Sign};

		use super::*;
		use crate::async_macros::{do_await, maybe_async};
		use crate::format::*;

		/// Defines `ESExpr` generation for binary file format
		#[allow(async_fn_in_trait, reason = "No additional traits to add")]
		pub trait ExprGeneratorWrite<E> {
			maybe_async!(
				$syncness,
				/// Generate output for an expression
				///
				/// # Errors
				/// Returns `Err` if an error occurs during generation.
				fn generate(&mut self, expr: &ESExpr<'_>) -> Result<(), GeneratorError<E>>;
			);
		}

		pub(super) trait ExprGeneratorWriteExt<W, E>: ExprGeneratorWrite<E> {
			maybe_async!(
				$syncness,
				fn generate_expr(&mut self, expr: &ESExpr) -> Result<(), GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn get_string_pool_index<S: Borrow<str>>(&mut self, s: S) -> Result<usize, GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn write_int_tag(&mut self, tag: u8, i: &BigUint) -> Result<(), GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn write_int_tag_out(out: &mut W, tag: u8, i: &BigUint) -> Result<(), GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn write_int_full(out: &mut W, i: &BigUint) -> Result<(), GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn write_int_rest(
					out: &mut W,
					buff: &[u8],
					current: u8,
					bit_index: i32,
				) -> Result<(), GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn write_string_expr(out: &mut W, s: &str) -> Result<(), GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn write(&mut self, b: u8) -> Result<(), GeneratorError<E>>;
			);
			maybe_async!(
				$syncness,
				fn write_out(out: &mut W, b: u8) -> Result<(), GeneratorError<E>>;
			);
		}

		impl<'a, E: 'static, W: Write<E>> ExprGeneratorWrite<E> for ExprGenerator<'a, W, E> {
			maybe_async!(
				$syncness,
				fn generate(&mut self, expr: &ESExpr<'_>) -> Result<(), GeneratorError<E>> {
					let old_string_pool_end = self.string_pool.len();

					let mut generator: ExprGenerator<_, Infallible> = ExprGenerator {
						out: &mut crate::io::sink(),
						string_pool: Vec::new(),
						error: PhantomData,
					};

					core::mem::swap(&mut self.string_pool, &mut generator.string_pool);
					// Dummy generator to catch new strings
					<ExprGenerator<_, Infallible> as super::writer_sync::ExprGeneratorWriteExt<_, Infallible>>::generate_expr(
												&mut generator,
												expr,
											).map_err(GeneratorError::from_infalliable)?;

					core::mem::swap(&mut self.string_pool, &mut generator.string_pool);

					match &self.string_pool[old_string_pool_end..] {
						[] => {},
						[s] => {
							do_await!($syncness, Self::write_out(self.out, TAG_APPEND_STRING_TABLE))?;
							do_await!($syncness, Self::write_string_expr(self.out, s.as_str()))?;
						},
						new_strings => {
							do_await!($syncness, Self::write_out(self.out, TAG_APPEND_STRING_TABLE))?;
							do_await!(
								$syncness,
								Self::write_out(self.out, TAG_CONSTRUCTOR_START_STRING_TABLE)
							)?;
							for s in new_strings {
								do_await!($syncness, Self::write_string_expr(self.out, s))?;
							}
							do_await!($syncness, Self::write_out(self.out, TAG_CONSTRUCTOR_END))?;
						},
					}

					do_await!($syncness, self.generate_expr(expr))?;
					Ok(())
				}
			);
		}

		impl<'a, E: 'static, W: Write<E>> ExprGeneratorWriteExt<W, E> for ExprGenerator<'a, W, E> {
			maybe_async!(
				$syncness,
				fn generate_expr(&mut self, expr: &ESExpr<'_>) -> Result<(), GeneratorError<E>> {
					match expr {
						ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) => {
							match &**name {
								"string-table" => do_await!($syncness, self.write(TAG_CONSTRUCTOR_START_STRING_TABLE))?,
								"list" => do_await!($syncness, self.write(TAG_CONSTRUCTOR_START_LIST))?,
								_ => {
									let index = do_await!($syncness, self.get_string_pool_index(name))?;
									do_await!(
										$syncness,
										self.write_int_tag(TAG_VARINT_CONSTRUCTOR_START, &BigUint::from(index))
									)?;
								},
							}

							for arg in args.iter() {
								do_await!($syncness, self.generate_expr(&arg))?;
							}

							for (kw, value) in kwargs.iter() {
								let index = do_await!($syncness, self.get_string_pool_index(kw))?;
								do_await!(
									$syncness,
									self.write_int_tag(TAG_VARINT_KEYWORD, &BigUint::from(index))
								)?;
								do_await!($syncness, self.generate_expr(&value))?;
							}

							do_await!($syncness, self.write(TAG_CONSTRUCTOR_END))?;
						},
						ESExpr::Bool(true) => {
							do_await!($syncness, self.write(TAG_TRUE))?;
						},
						ESExpr::Bool(false) => {
							do_await!($syncness, self.write(TAG_FALSE))?;
						},
						ESExpr::Int(i) => {
							let (sign, mut magnitude) = i.as_ref().clone().into_parts();

							match sign {
								Sign::NoSign | Sign::Plus => {
									do_await!($syncness, self.write_int_tag(TAG_VARINT_NON_NEG_INT, &magnitude))?;
								},

								Sign::Minus => {
									magnitude -= 1usize;
									do_await!($syncness, self.write_int_tag(TAG_VARINT_NEG_INT, &magnitude))?;
								},
							}
						},
						ESExpr::Str(s) => {
							do_await!(
								$syncness,
								self.write_int_tag(TAG_VARINT_STRING_LENGTH, &BigUint::from(s.len()))
							)?;
							do_await!($syncness, self.out.write(s.as_bytes()))?;
						},
						ESExpr::Binary(b) => {
							do_await!(
								$syncness,
								self.write_int_tag(TAG_VARINT_BYTES_LENGTH, &BigUint::from(b.len()))
							)?;
							do_await!($syncness, self.out.write(b.as_ref()))?;
						},
						ESExpr::Float32(f) => {
							do_await!($syncness, self.write(TAG_FLOAT32))?;
							do_await!($syncness, self.out.write(&f32::to_le_bytes(*f)))?;
						},
						ESExpr::Float64(d) => {
							do_await!($syncness, self.write(TAG_FLOAT64))?;
							do_await!($syncness, self.out.write(&f64::to_le_bytes(*d)))?;
						},
						ESExpr::Null(level) => {
							let level: &BigUint = level.as_ref();

							if *level == BigUint::ZERO {
								do_await!($syncness, self.write(TAG_NULL0))?;
							}
							else if *level == BigUint::from(1u32) {
								do_await!($syncness, self.write(TAG_NULL1))?;
							}
							else if *level == BigUint::from(2u32) {
								do_await!($syncness, self.write(TAG_NULL2))?;
							}
							else {
								do_await!($syncness, self.write(TAG_NULLN))?;
								do_await!($syncness, Self::write_int_full(self.out, &(level - 3u32)))?;
							}
						},
					}

					Ok(())
				}
			);

			maybe_async!(
				$syncness,
				fn get_string_pool_index<S: Borrow<str>>(&mut self, s: S) -> Result<usize, GeneratorError<E>> {
					let s = s.borrow();
					if let Some(index) = self.string_pool.iter().position(|s2| s2 == s) {
						return Ok(index);
					}

					let index = self.string_pool.len();
					self.string_pool.push(s.to_owned());

					do_await!($syncness, self.write(TAG_APPEND_STRING_TABLE))?;
					do_await!($syncness, Self::write_string_expr(self.out, s))?;

					Ok(index)
				}
			);

			maybe_async!(
				$syncness,
				fn write_int_tag(&mut self, tag: u8, i: &BigUint) -> Result<(), GeneratorError<E>> {
					do_await!($syncness, Self::write_int_tag_out(self.out, tag, i))
				}
			);

			maybe_async!(
				$syncness,
				fn write_int_tag_out(out: &mut W, tag: u8, i: &BigUint) -> Result<(), GeneratorError<E>> {
					let buff = i.to_bytes_le();

					let b0 = buff.first().copied().unwrap_or_default();
					let mut current = tag | (b0 & 0x0F);
					if buff.len() < 2 && (b0 & 0xF0) == 0 {
						do_await!($syncness, Self::write_out(out, current))?;
						return Ok(());
					}

					current |= 0x10;
					do_await!($syncness, Self::write_out(out, current))?;

					current = b0 >> 4;
					let bit_index = 4;

					do_await!(
						$syncness,
						Self::write_int_rest(out, &buff[1..], current, bit_index)
					)
				}
			);

			maybe_async!(
				$syncness,
				fn write_int_full(out: &mut W, i: &BigUint) -> Result<(), GeneratorError<E>> {
					if *i == BigUint::ZERO {
						do_await!($syncness, Self::write_out(out, 0))?;
						return Ok(());
					}

					let buff = i.to_bytes_le();
					let current = 0;
					let bit_index = 0;

					do_await!($syncness, Self::write_int_rest(out, &buff, current, bit_index))
				}
			);

			maybe_async!(
				$syncness,
				fn write_int_rest(
					out: &mut W,
					buff: &[u8],
					mut current: u8,
					mut bit_index: i32,
				) -> Result<(), GeneratorError<E>> {
					for (i, b) in buff.iter().copied().enumerate() {
						let mut bit_index2 = 0;
						while bit_index2 < 8 {
							let written_bits = core::cmp::min(7 - bit_index, 8 - bit_index2);
							current |= ((b >> bit_index2) & 0x7F) << bit_index;

							bit_index += written_bits;
							bit_index2 += written_bits;
							if bit_index >= 7 {
								if i < buff.len() - 1 || (bit_index2 < 8 && (b >> bit_index2) != 0) {
									current |= 0x80;
								}

								do_await!($syncness, Self::write_out(out, current))?;
								bit_index = 0;
								current = 0;
							}
						}
					}

					if current != 0 {
						do_await!($syncness, Self::write_out(out, current))?;
					}

					Ok(())
				}
			);

			maybe_async!(
				$syncness,
				fn write_string_expr(out: &mut W, s: &str) -> Result<(), GeneratorError<E>> {
					do_await!(
						$syncness,
						Self::write_int_tag_out(out, TAG_VARINT_STRING_LENGTH, &BigUint::from(s.len()))
					)?;
					do_await!($syncness, out.write(s.as_bytes()))?;
					Ok(())
				}
			);

			maybe_async!(
				$syncness,
				fn write(&mut self, b: u8) -> Result<(), GeneratorError<E>> {
					do_await!($syncness, Self::write_out(self.out, b))
				}
			);

			maybe_async!(
				$syncness,
				fn write_out(out: &mut W, b: u8) -> Result<(), GeneratorError<E>> {
					Ok(do_await!($syncness, out.write(core::slice::from_ref(&b)))?)
				}
			);
		}
	};
}

mod writer_sync {
	use crate::io::Write;
	writer_mod!(sync);
}

mod writer_async {
	use crate::io::AsyncWrite as Write;
	writer_mod!(async);
}

pub use writer_async::ExprGeneratorWrite as ExprGeneratorAsync;
pub use writer_sync::ExprGeneratorWrite as ExprGeneratorSync;
