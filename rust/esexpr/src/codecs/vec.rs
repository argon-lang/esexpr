use alloc::borrow::ToOwned;
use alloc::boxed::Box;
use alloc::collections::VecDeque;
use alloc::vec::Vec;
use esexpr::expr::ESExprConstructor;
use crate::{DecodeError, DecodeErrorPath, DecodeErrorType, ESExpr, ESExprCodec, ESExprTag, ESExprTagCollection, ESExprVarArgCodec};
use crate::cowstr::CowStr;

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for Vec<A> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(CowStr::Static("list"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::<'a>::constructor(
			"list",
			self.iter().map(A::encode_esexpr).collect::<Vec<ESExpr<'a>>>(),
			[]
		)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) if *name == *"list" => {
				if !kwargs.is_empty() {
					return Err(DecodeError::new(
						DecodeErrorType::OutOfRange("List must not have keyword arguments".to_owned()),
						DecodeErrorPath::Constructor(name.into_string()),
					));
				}

				args.into_iter().map(A::decode_esexpr).collect::<Result<Vec<_>, _>>()
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

impl<'a, A: ESExprCodec<'a>> ESExprVarArgCodec<'a> for Vec<A> {
	const TAGS: ESExprTagCollection = A::TAGS;

	fn encode_vararg_element(&'a self, args: &mut Vec<ESExpr<'a>>) {
		for arg in self {
			args.push(arg.encode_esexpr());
		}
	}

	fn decode_vararg_element(
		args: &mut VecDeque<ESExpr<'a>>,
		constructor_name: &str,
		start_index: usize,
	) -> Result<Self, DecodeError> {
		args.drain(..)
			.enumerate()
			.map(|(i, a)| {
				A::decode_esexpr(a).map_err(|mut e| {
					e.error_path_with(|old_path| {
						DecodeErrorPath::Positional(constructor_name.to_owned(), start_index + i, Box::new(old_path))
					});
					e
				})
			})
			.collect()
	}
}
