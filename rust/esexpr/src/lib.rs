//! esexpr is a library that implements the `ESExpr` format.

#![no_std]

mod codecs;
mod error;
mod expr;
mod tags;
mod value_eq;

#[cfg(feature = "std")]
extern crate std;

extern crate alloc;
extern crate core;

/// Exported dependency modules.
pub mod core_types {
	pub extern crate alloc;
	pub extern crate core;
}

pub use codecs::{ESExprCodec, ESExprDictCodec, ESExprOptionalFieldCodec, ESExprVarArgCodec};
pub use error::{DecodeError, DecodeErrorPath, DecodeErrorType};
pub use esexpr_derive::{ESExprCodec, ValueEq, esexpr_literal as esexpr};
pub use expr::ESExpr;
pub use tags::{ESExprTag, ESExprTagCollection};
pub use value_eq::ValueEq;
