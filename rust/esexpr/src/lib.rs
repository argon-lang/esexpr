//! esexpr is a library that implements the `ESExpr` format.

#![no_std]

mod codecs;
mod error;
mod expr;
mod tags;

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
pub use esexpr_derive::{ESExprCodec, esexpr_literal as esexpr};
pub use expr::ESExpr;
pub use tags::{ESExprTag, ESExprTagCollection};

#[cfg(test)]
mod tests {
	use super::*;

	#[test]
	fn tag_collection_disjoint() {
		assert!(
			ESExprTagCollection::Tags(&[ESExprTag::Int]).is_disjoint(ESExprTagCollection::Tags(&[ESExprTag::Float32])),
		);

		assert!(
			ESExprTagCollection::Concat(&[
				<Option<i32> as ESExprOptionalFieldCodec>::TAGS,
				<Option<f32> as ESExprOptionalFieldCodec>::TAGS,
			],)
			.is_disjoint(<String as ESExprCodec>::TAGS)
		);
	}
}
