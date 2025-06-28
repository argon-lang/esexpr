use alloc::borrow::Cow;
use alloc::boxed::Box;
use std::collections::{BTreeMap, VecDeque};
use std::prelude::rust_2015::Vec;

use crate::{
	DecodeError,
	ESExpr,
	ESExprCodec,
	ESExprDictCodec,
	ESExprOptionalFieldCodec,
	ESExprTagCollection,
	ESExprVarArgCodec,
};

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for Box<A> {
	const TAGS: ESExprTagCollection = A::TAGS;

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		A::encode_esexpr(&**self)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		A::decode_esexpr(expr).map(Box::new)
	}
}

impl<'a, F: ESExprOptionalFieldCodec<'a>> ESExprOptionalFieldCodec<'a> for Box<F> {
	const TAGS: ESExprTagCollection = F::TAGS;

	fn encode_optional_field(&'a self) -> Option<ESExpr<'a>> {
		(**self).encode_optional_field()
	}

	fn decode_optional_field(value: Option<ESExpr<'a>>) -> Result<Self, DecodeError> {
		F::decode_optional_field(value).map(Box::new)
	}
}

impl<'a, F: ESExprVarArgCodec<'a>> ESExprVarArgCodec<'a> for Box<F> {
	const TAGS: ESExprTagCollection = F::TAGS;

	fn encode_vararg_element(&'a self, args: &mut Vec<ESExpr<'a>>) {
		(**self).encode_vararg_element(args);
	}

	fn decode_vararg_element(
		args: &mut VecDeque<ESExpr<'a>>,
		constructor_name: &str,
		start_index: usize,
	) -> Result<Self, DecodeError> {
		F::decode_vararg_element(args, constructor_name, start_index).map(Box::new)
	}
}

impl<'a, F: ESExprDictCodec<'a>> ESExprDictCodec<'a> for Box<F> {
	const TAGS: ESExprTagCollection = F::TAGS;

	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>) {
		(**self).encode_dict_element(kwargs);
	}

	fn decode_dict_element(
		kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		F::decode_dict_element(kwargs, constructor_name).map(Box::new)
	}
}
