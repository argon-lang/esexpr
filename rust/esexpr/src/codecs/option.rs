use alloc::borrow::Cow;

use num_bigint::BigUint;

use crate::{DecodeError, ESExpr, ESExprCodec, ESExprEncodedEq, ESExprOptionalFieldCodec, ESExprTag, ESExprTagCollection};

impl<A: ESExprEncodedEq> ESExprEncodedEq for Option<A> {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		match (self, other) {
			(Some(a), Some(b)) => a.is_encoded_eq(b),
			(None, None) => true,
			_ => false,
		}
	}
}

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for Option<A> {
	const TAGS: ESExprTagCollection = {
		if A::TAGS.is_empty() {
			ESExprTagCollection::Tags(&[])
		}
		else {
			ESExprTagCollection::Concat(&[ESExprTagCollection::Tags(&[ESExprTag::Null]), A::TAGS])
		}
	};

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		match self {
			Some(a) => match a.encode_esexpr() {
				ESExpr::Null(level) => ESExpr::Null(Cow::Owned(level.as_ref() + 1u32)),
				expr => expr,
			},

			None => ESExpr::Null(Cow::Owned(BigUint::ZERO)),
		}
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Null(mut level) => {
				if *level == BigUint::ZERO {
					Ok(None)
				}
				else {
					*level.to_mut() -= 1u32;
					A::decode_esexpr(ESExpr::Null(level)).map(Some)
				}
			},
			_ => A::decode_esexpr(expr).map(Some),
		}
	}
}

impl<'a, A: ESExprCodec<'a>> ESExprOptionalFieldCodec<'a> for Option<A> {
	type Element = A;

	fn encode_optional_field(&'a self) -> Option<ESExpr<'a>> {
		self.as_ref().map(A::encode_esexpr)
	}

	fn decode_optional_field(value: Option<ESExpr<'a>>) -> Result<Self, DecodeError> {
		value.map(|expr| A::decode_esexpr(expr)).transpose()
	}
}
