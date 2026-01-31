#[macro_use]
mod map;
#[macro_use]
mod set;
mod boxed;
mod btreemap;
mod btreeset;
mod hashbrown_map;
mod hashbrown_set;
#[cfg(feature = "std")]
mod hashmap;
#[cfg(feature = "std")]
mod hashset;
mod option;
mod scalar;
mod vec;

use crate::cowstr::CowStr;
use crate::{DecodeError, ESExpr, ESExprTagSet};
use alloc::collections::VecDeque;
use alloc::vec::Vec;
use hashbrown::HashMap;

/// Equality for the encoded ESExpr representation.
pub trait ESExprEncodedEq {
	/// Determines whether two values are equal when encoded as `ESExpr`.
	fn is_encoded_eq(&self, other: &Self) -> bool;
}

/// A codec that encodes and decodes `ESExpr` values.
pub trait ESExprCodec<'a>: ESExprEncodedEq + Sized + 'a {
	/// The tags of the encoded expressions that this type can produce.
	const TAGS: ESExprTagSet;

	/// Encode this value into an expression.
	fn encode_esexpr(&'a self) -> ESExpr<'a>;

	/// Decode an expression into a value.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError>;
}

/// A field codec for optional fields.
pub trait ESExprOptionalFieldCodec<'a>: ESExprEncodedEq + Sized + 'a {
	/// The element type.
	type Element: ESExprCodec<'a> + 'a;

	/// Encode an optional field or None when the value should be excluded.
	fn encode_optional_field(&'a self) -> Option<ESExpr<'a>>;

	/// Decode an optional field value.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_optional_field(value: Option<ESExpr<'a>>) -> Result<Self, DecodeError>;
}

/// A field codec for variable arguments.
pub trait ESExprVarArgCodec<'a>: ESExprEncodedEq + Sized + 'a {
	/// The element type.
	type Element: ESExprCodec<'a> + 'a;

	/// Encode variable arguments
	fn encode_vararg_element(&'a self, args: &mut Vec<ESExpr<'a>>);

	/// Decode variable arguments.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_vararg_element(
		args: &mut VecDeque<ESExpr<'a>>,
		constructor_name: &str,
		start_index: &mut usize,
	) -> Result<Self, DecodeError>;
}

/// A field codec for dictionary arguments.
pub trait ESExprDictCodec<'a>
where
	Self: Sized + 'a,
{
	/// The element type.
	type Element: ESExprCodec<'a> + 'a;

	/// Encode dictionary arguments.
	fn encode_dict_element(&'a self, kwargs: &mut HashMap<CowStr<'a>, ESExpr<'a>>);

	/// Decode dictionary arguments.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_dict_element(
		kwargs: &mut HashMap<CowStr<'a>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError>;
}

