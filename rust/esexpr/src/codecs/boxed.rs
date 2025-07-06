use alloc::boxed::Box;
use alloc::collections::{BTreeMap, VecDeque};
use alloc::vec::Vec;

use crate::cowstr::CowStr;
use crate::{DecodeError, ESExpr, ESExprCodec, ESExprDictCodec, ESExprEncodedEq, ESExprOptionalFieldCodec, ESExprTagCollection, ESExprVarArgCodec};

impl<A: ESExprEncodedEq> ESExprEncodedEq for Box<A> {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		A::is_encoded_eq(self, other)
	}
}

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
	type Element = F::Element;


	fn encode_optional_field(&'a self) -> Option<ESExpr<'a>> {
		(**self).encode_optional_field()
	}

	fn decode_optional_field(value: Option<ESExpr<'a>>) -> Result<Self, DecodeError> {
		F::decode_optional_field(value).map(Box::new)
	}
}

impl<'a, F: ESExprVarArgCodec<'a>> ESExprVarArgCodec<'a> for Box<F> {
	type Element = F::Element;

	fn encode_vararg_element(&'a self, args: &mut Vec<ESExpr<'a>>) {
		(**self).encode_vararg_element(args);
	}

	fn decode_vararg_element(
		args: &mut VecDeque<ESExpr<'a>>,
		constructor_name: &str,
		start_index: &mut usize,
	) -> Result<Self, DecodeError> {
		F::decode_vararg_element(args, constructor_name, start_index).map(Box::new)
	}
}

impl<'a, F: ESExprDictCodec<'a>> ESExprDictCodec<'a> for Box<F> {
	type Element = F::Element;

	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<CowStr<'a>, ESExpr<'a>>) {
		(**self).encode_dict_element(kwargs);
	}

	fn decode_dict_element(
		kwargs: &mut BTreeMap<CowStr<'a>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		F::decode_dict_element(kwargs, constructor_name).map(Box::new)
	}
}
