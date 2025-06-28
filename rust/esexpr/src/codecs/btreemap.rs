use alloc::borrow::{Cow, ToOwned};
use alloc::boxed::Box;
use alloc::collections::BTreeMap;
use alloc::string::String;
use alloc::vec::Vec;

use crate::{
	DecodeError,
	DecodeErrorPath,
	DecodeErrorType,
	ESExpr,
	ESExprCodec,
	ESExprDictCodec,
	ESExprTag,
	ESExprTagCollection,
};

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for BTreeMap<Cow<'a, str>, A> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(Cow::Borrowed("dict"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		let mut kwargs = BTreeMap::new();

		for (k, v) in self {
			kwargs.insert(Cow::Borrowed(k.as_ref()), v.encode_esexpr());
		}

		ESExpr::Constructor {
			name: Cow::Borrowed("dict"),
			args: Cow::Owned(Vec::new()),
			kwargs: Cow::Owned(kwargs),
		}
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Constructor { name, args, kwargs } if name == "dict" => {
				if !args.is_empty() {
					return Err(DecodeError::new(
						DecodeErrorType::OutOfRange("Dict must not have positional arguments".to_owned()),
						DecodeErrorPath::Constructor(name.as_ref().to_owned()),
					));
				}

				let mut dict = BTreeMap::default();

				match kwargs {
					Cow::Borrowed(kwargs) => {
						for (k, v) in kwargs {
							dict.insert(Cow::Borrowed(k.as_ref()), A::decode_esexpr(v.as_borrowed())?);
						}
					},
					Cow::Owned(kwargs) => {
						for (k, v) in kwargs {
							dict.insert(k, A::decode_esexpr(v)?);
						}
					},
				}

				Ok(dict)
			},
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: <Self as ESExprCodec>::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

impl<'a, A: ESExprCodec<'a>> ESExprDictCodec<'a> for BTreeMap<Cow<'a, str>, A> {
	const TAGS: ESExprTagCollection = A::TAGS;

	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>) {
		for (k, v) in self {
			kwargs.insert(Cow::Borrowed(k.as_ref()), v.encode_esexpr());
		}
	}

	fn decode_dict_element(
		kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		core::mem::take(kwargs)
			.into_iter()
			.map(|(k, v)| {
				let value = A::decode_esexpr(v).map_err(|mut e| {
					e.error_path_with(|old_path| {
						DecodeErrorPath::Keyword(constructor_name.to_owned(), k.as_ref().to_owned(), Box::new(old_path))
					});
					e
				})?;

				Ok((k, value))
			})
			.collect()
	}
}

impl<'a, A: ESExprCodec<'a>> ESExprDictCodec<'a> for BTreeMap<String, A> {
	const TAGS: ESExprTagCollection = A::TAGS;

	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>) {
		for (k, v) in self {
			kwargs.insert(Cow::Borrowed(k.as_ref()), v.encode_esexpr());
		}
	}

	fn decode_dict_element(
		kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		core::mem::take(kwargs)
			.into_iter()
			.map(|(k, v)| {
				let value = A::decode_esexpr(v).map_err(|mut e| {
					e.error_path_with(|old_path| {
						DecodeErrorPath::Keyword(constructor_name.to_owned(), k.as_ref().to_owned(), Box::new(old_path))
					});
					e
				})?;

				Ok((k.into_owned(), value))
			})
			.collect()
	}
}
