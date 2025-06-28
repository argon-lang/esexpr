//! Binary format for `ESExpr`.
#![no_std]

extern crate alloc;
extern crate core;
#[cfg(feature = "std")]
extern crate std;

mod append_only_string_list;

mod async_macros;
mod format;
/// IO Traits for `ESExpr`.
pub mod io;
mod reader;
mod writer;

pub use reader::*;
pub use writer::{ExprGenerator, ExprGeneratorAsync, ExprGeneratorSync};

#[cfg(test)]
mod test {
	use alloc::borrow::Cow;
	use alloc::vec::Vec;
	use core::convert::Infallible;
	use core::str::FromStr;

	use esexpr::{ESExpr, ESExprCodec};
	use num_bigint::{BigInt, BigUint};

	use super::{ExprGenerator, ExprGeneratorSync, ExprParserSync, parse_sync};

	#[test]
	fn encode_int() {
		fn check(n: &str, mut enc: &[u8]) {
			let n = BigUint::from_str(n).unwrap();

			let mut buff: Vec<u8> = Vec::new();

			let mut eg = ExprGenerator::<_, Infallible>::new(&mut buff);
			eg.generate(&ESExpr::Int(Cow::Owned(BigInt::from(n.clone())))).unwrap();

			assert_eq!(enc, &buff);

			let m = BigUint::decode_esexpr(parse_sync::<Infallible>(&mut enc).read_next_expr().unwrap()).unwrap();
			assert_eq!(n, m);
		}

		check("4", &[0x24]);
		check(
			"9223372036854775807",
			&[0x3F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x07],
		);
		check(
			"18446744073709551615",
			&[0x3F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F],
		);
		check(
			"12345678901234567890",
			&[0x32, 0xAD, 0xE1, 0xC7, 0xF5, 0x8C, 0xD3, 0xD2, 0xDA, 0x0A],
		);
		check(
			"98765432109876543210",
			&[0x3A, 0xEE, 0xCF, 0xC9, 0xF2, 0xB8, 0x9A, 0x95, 0xD5, 0x55],
		);
	}
}
