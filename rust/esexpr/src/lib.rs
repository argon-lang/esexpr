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
	pub extern crate num_bigint;
	pub extern crate num_traits;
	pub extern crate half;
	pub extern crate hashbrown;
}

pub use codecs::{ESExprEncodedEq, ESExprCodec, ESExprDictCodec, ESExprOptionalFieldCodec, ESExprVarArgCodec};
pub use error::{DecodeError, DecodeErrorPath, DecodeErrorType};
pub use esexpr_derive::{ESExprCodec, ESExprEncodedEq, esexpr_literal as esexpr, esexpr_flags};
pub use expr::{ConstructorArgs, ESExpr, ESExprConstructor, ESExprStatic, KeywordArgs};
pub use tags::{ESExprTag, ESExprTagSet};
pub use dict::Dictionary;
