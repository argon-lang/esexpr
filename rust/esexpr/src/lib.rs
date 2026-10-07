//! esexpr is a library that implements the `ESExpr` format.

#![no_std]

extern crate self as esexpr;

mod codecs;
pub mod cowstr;
mod error;
mod expr;
mod tags;

mod dict;

#[cfg(feature = "std")]
extern crate std;

extern crate alloc;
extern crate core;

#[doc(hidden)]
pub mod core_types {
	pub extern crate alloc;
	pub extern crate core;
	pub extern crate half;
	pub extern crate hashbrown;
	pub extern crate num_bigint;
	pub extern crate num_traits;
}

pub use codecs::{ESExprCodec, ESExprDictCodec, ESExprEncodedEq, ESExprOptionalFieldCodec, ESExprVarArgCodec};
pub use dict::Dictionary;
pub use error::{DecodeError, DecodeErrorPath, DecodeErrorType};
pub use esexpr_derive::{ESExprCodec, ESExprEncodedEq, esexpr_flags, esexpr_literal as esexpr};
pub use expr::{ConstructorArgs, ESExpr, ESExprConstructor, ESExprStatic, KeywordArgs};
pub use tags::{ESExprTag, ESExprTagSet};
