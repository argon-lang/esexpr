use std::borrow::{Cow, ToOwned};
use std::boxed::Box;
use std::collections::{BTreeMap, HashMap};
use std::hash::BuildHasher;
use std::string::String;
use std::vec::Vec;

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

impl<'a, A: ESExprCodec<'a>, S: BuildHasher + Default + 'static> ESExprCodec<'a> for HashMap<String, A, S> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(Cow::Borrowed("dict"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		let mut kwargs = BTreeMap::new();

		for (k, v) in self {
			kwargs.insert(Cow::Borrowed(k.as_str()), v.encode_esexpr());
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

				let mut dict = HashMap::default();

				match kwargs {
					Cow::Borrowed(kwargs) => {
						for (k, v) in kwargs {
							dict.insert(k.as_ref().to_owned(), A::decode_esexpr(v.as_borrowed())?);
						}
					},
					Cow::Owned(kwargs) => {
						for (k, v) in kwargs {
							dict.insert(k.into_owned(), A::decode_esexpr(v)?);
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

impl<'a, A: ESExprCodec<'a>, S: BuildHasher + Default + 'static> ESExprDictCodec<'a> for HashMap<String, A, S> {
	const TAGS: ESExprTagCollection = A::TAGS;

	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>) {
		for (k, v) in self {
			kwargs.insert(Cow::Borrowed(k), v.encode_esexpr());
		}
	}

	fn decode_dict_element(
		kwargs: &mut BTreeMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		std::mem::take(kwargs)
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

impl<'a, A: ESExprCodec<'a>, S: BuildHasher + Default + 'static> ESExprDictCodec<'a> for HashMap<Cow<'a, str>, A, S> {
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
		std::mem::take(kwargs)
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
