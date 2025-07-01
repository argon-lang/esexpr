mod boxed;
mod btreemap;
#[cfg(feature = "std")]
mod hashmap;
mod option;
mod scalar;
mod vec;

use alloc::borrow::Cow;
use alloc::collections::{BTreeMap, VecDeque};
use alloc::vec::Vec;

use crate::{DecodeError, ESExpr, ESExprTagCollection};

/// A codec that encodes and decodes `ESExpr` values.
pub trait ESExprCodec<'a>
where
	Self: Sized + 'a,
{
	/// The tags of the encoded expressions that this type can produce.
	const TAGS: ESExprTagCollection;

	/// Encode this value into an expression.
	fn encode_esexpr(&'a self) -> ESExpr<'a>;

	/// Decode an expression into a value.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError>;
}

/// A field codec for optional fields.
pub trait ESExprOptionalFieldCodec<'a>
where
	Self: Sized + 'a,
{
	/// The tags of the encoded expressions that this type can produce.
	const TAGS: ESExprTagCollection;

	/// Encode an optional field or None when the value should be excluded.
	fn encode_optional_field(&'a self) -> Option<ESExpr<'a>>;

	/// Decode an optional field value.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_optional_field(value: Option<ESExpr<'a>>) -> Result<Self, DecodeError>;
}

/// A field codec for variable arguments.
pub trait ESExprVarArgCodec<'a>
where
	Self: Sized + 'a,
{
	/// The tags of the encoded expressions that this type can produce.
	const TAGS: ESExprTagCollection;

	/// Encode variable arguments
	fn encode_vararg_element(&'a self, args: &mut Vec<ESExpr<'a>>);

	/// Decode variable arguments.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_vararg_element(
		args: &mut VecDeque<ESExpr<'a>>,
		constructor_name: &str,
		start_index: usize,
	) -> Result<Self, DecodeError>;
}

/// A field codec for dictionary arguments.
pub trait ESExprDictCodec<'a>
where
	Self: Sized + 'a,
{
	/// The tags of the encoded expressions that this type can produce.
	const TAGS: ESExprTagCollection;

	/// Encode dictionary arguments.
	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>);

	/// Decode dictionary arguments.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_dict_element(
		kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError>;
}
