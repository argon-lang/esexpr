use alloc::borrow::{Cow, ToOwned};
use alloc::boxed::Box;
use alloc::collections::BTreeMap;
use alloc::string::String;
use core::ops::Deref;

use crate::cowstr::CowStr;
use crate::{
	DecodeError,
	DecodeErrorPath,
	DecodeErrorType,
	ESExpr,
	ESExprCodec,
	ESExprConstructor,
	ESExprDictCodec,
	ESExprTag,
	ESExprTagCollection,
};

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for BTreeMap<String, A> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(CowStr::Static("dict"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::constructor(
			"dict",
			[],
			self.iter()
				.map(|(k, v)| (CowStr::Borrowed(k), v.encode_esexpr()))
				.collect::<BTreeMap<_, _>>(),
		)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) if name == "dict" => {
				if !args.is_empty() {
					return Err(DecodeError::new(
						DecodeErrorType::OutOfRange("Dict must not have positional arguments".to_owned()),
						DecodeErrorPath::Constructor(name.deref().to_owned()),
					));
				}

				let mut dict = BTreeMap::default();

				for (k, v) in kwargs {
					dict.insert(k.into_string(), A::decode_esexpr(v)?);
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

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for BTreeMap<Cow<'a, str>, A> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(CowStr::Static("dict"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		let mut kwargs = BTreeMap::new();

		for (k, v) in self {
			kwargs.insert(CowStr::Borrowed(k.as_ref()), v.encode_esexpr());
		}

		ESExpr::constructor(
			"dict",
			[],
			self.iter()
				.map(|(k, v)| (CowStr::Borrowed(k.as_ref()), v.encode_esexpr()))
				.collect::<BTreeMap<_, _>>(),
		)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) if name == "dict" => {
				if !args.is_empty() {
					return Err(DecodeError::new(
						DecodeErrorType::OutOfRange("Dict must not have positional arguments".to_owned()),
						DecodeErrorPath::Constructor(name.deref().to_owned()),
					));
				}

				let mut dict = BTreeMap::default();

				for (k, v) in kwargs {
					dict.insert(Cow::from(k), A::decode_esexpr(v)?);
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

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for BTreeMap<CowStr<'a>, A> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(CowStr::Static("dict"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::constructor(
			"dict",
			[],
			self.iter()
				.map(|(k, v)| (CowStr::Borrowed(k.as_ref()), v.encode_esexpr()))
				.collect::<BTreeMap<_, _>>(),
		)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) if name == "dict" => {
				if !args.is_empty() {
					return Err(DecodeError::new(
						DecodeErrorType::OutOfRange("Dict must not have positional arguments".to_owned()),
						DecodeErrorPath::Constructor(name.deref().to_owned()),
					));
				}

				let mut dict = BTreeMap::default();

				for (k, v) in kwargs {
					dict.insert(k, A::decode_esexpr(v)?);
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
	type Element = A;

	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<CowStr<'a>, ESExpr<'a>>) {
		for (k, v) in self {
			kwargs.insert(CowStr::Borrowed(k.as_ref()), v.encode_esexpr());
		}
	}

	fn decode_dict_element(
		kwargs: &mut BTreeMap<CowStr<'a>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		core::mem::take(kwargs)
			.into_iter()
			.map(|(k, v)| {
				let value = A::decode_esexpr(v).map_err(|mut e| {
					e.error_path_with(|old_path| {
						DecodeErrorPath::Keyword(constructor_name.to_owned(), k.deref().to_owned(), Box::new(old_path))
					});
					e
				})?;

				Ok((Cow::from(k), value))
			})
			.collect()
	}
}

impl<'a, A: ESExprCodec<'a>> ESExprDictCodec<'a> for BTreeMap<String, A> {
	type Element = A;


	fn encode_dict_element(&'a self, kwargs: &mut BTreeMap<CowStr<'a>, ESExpr<'a>>) {
		for (k, v) in self {
			kwargs.insert(CowStr::Borrowed(k.as_ref()), v.encode_esexpr());
		}
	}

	fn decode_dict_element(
		kwargs: &mut BTreeMap<CowStr<'a>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		core::mem::take(kwargs)
			.into_iter()
			.map(|(k, v)| {
				let value = A::decode_esexpr(v).map_err(|mut e| {
					e.error_path_with(|old_path| {
						DecodeErrorPath::Keyword(constructor_name.to_owned(), k.deref().to_owned(), Box::new(old_path))
					});
					e
				})?;

				Ok((k.into_string(), value))
			})
			.collect()
	}
}
