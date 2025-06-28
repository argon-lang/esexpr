use alloc::borrow::{Cow, ToOwned};
use std::collections::BTreeMap;

use num_bigint::{BigInt, BigUint};

use crate::{DecodeError, ESExprCodec, ESExprTag, ESExprTagCollection};

/// Representation of an `ESExpr` value.
/// Must be one of a constructor, bool, int, string, binary, float32, float64, or null.
#[derive(Debug, Clone)]
pub enum ESExpr<'a> {
	/// A constructor expression.
	/// Can contain positional and keyword arguments.
	Constructor {
		/// The constructor name.
		name: Cow<'a, str>,

		/// Positional argument values.
		args: Cow<'a, [ESExpr<'a>]>,

		/// Keyword argument values.
		kwargs: Cow<'a, BTreeMap<Cow<'a, str>, ESExpr<'a>>>,
	},

	/// A bool value.
	Bool(bool),

	/// An integer value.
	Int(Cow<'a, BigInt>),

	/// A string value.
	Str(Cow<'a, str>),

	/// A binary value.
	Binary(Cow<'a, [u8]>),

	/// A float32 value.
	Float32(f32),

	/// A float64 value.
	Float64(f64),

	/// A Null value.
	Null(Cow<'a, BigUint>),
}

impl<'a, 'b> PartialEq<ESExpr<'b>> for ESExpr<'a> {
	fn eq(&self, other: &ESExpr<'b>) -> bool {
		match self {
			ESExpr::Constructor {
				name: name1,
				args: args1,
				kwargs: kwargs1,
			} => {
				let ESExpr::Constructor {
					name: name2,
					args: args2,
					kwargs: kwargs2,
				} = other
				else {
					return false;
				};

				name1 == name2 && args1 == args2 && {
					kwargs1
						.iter()
						.all(|(k1, v1)| kwargs2.get(k1).is_some_and(|v2| v1 == v2)) &&
						kwargs2.keys().all(|k2| kwargs1.contains_key(k2))
				}
			},
			&ESExpr::Bool(b1) => matches!(other, &ESExpr::Bool(b2) if b1 == b2),
			ESExpr::Int(i1) => matches!(other, ESExpr::Int(i2) if i1 == i2),
			ESExpr::Str(s1) => matches!(other, ESExpr::Str(s2) if s1 == s2),
			ESExpr::Binary(b1) => matches!(other, ESExpr::Binary(b2) if b1 == b2),
			&ESExpr::Float32(f1) => matches!(other, &ESExpr::Float32(f2) if f1 == f2),
			&ESExpr::Float64(f1) => matches!(other, &ESExpr::Float64(f2) if f1 == f2),
			ESExpr::Null(l1) => matches!(other, ESExpr::Null(l2) if l1 == l2),
		}
	}
}

impl<'a> ESExpr<'a> {
	/// Get the tag of an expression.
	#[must_use]
	pub fn tag(&self) -> ESExprTag {
		match self {
			ESExpr::Constructor { name, .. } => ESExprTag::Constructor(Cow::Borrowed(name)),
			ESExpr::Bool(_) => ESExprTag::Bool,
			ESExpr::Int(_) => ESExprTag::Int,
			ESExpr::Str(_) => ESExprTag::Str,
			ESExpr::Binary(_) => ESExprTag::Binary,
			ESExpr::Float32(_) => ESExprTag::Float32,
			ESExpr::Float64(_) => ESExprTag::Float64,
			ESExpr::Null(_) => ESExprTag::Null,
		}
	}

	/// Performs a deep clone of the value.
	#[must_use]
	pub fn as_owned(&self) -> ESExpr<'static> {
		match self {
			ESExpr::Constructor { name, args, kwargs } => ESExpr::Constructor {
				name: Cow::Owned(name.as_ref().to_owned()),
				args: args.iter().map(Self::as_owned).collect(),
				kwargs: Cow::Owned(
					kwargs
						.iter()
						.map(|(k, v)| (Cow::Owned(k.as_ref().to_owned()), v.as_owned()))
						.collect(),
				),
			},
			&ESExpr::Bool(b) => ESExpr::Bool(b),
			ESExpr::Int(i) => ESExpr::Int(Cow::Owned(i.as_ref().clone())),
			ESExpr::Str(s) => ESExpr::Str(Cow::Owned(s.as_ref().to_owned())),
			ESExpr::Binary(b) => ESExpr::Binary(Cow::Owned(b.as_ref().to_owned())),
			&ESExpr::Float32(f) => ESExpr::Float32(f),
			&ESExpr::Float64(f) => ESExpr::Float64(f),
			ESExpr::Null(level) => ESExpr::Null(Cow::Owned(level.as_ref().clone())),
		}
	}

	/// Ensures that any borrowed values are converted to owned values.
	#[must_use]
	pub fn into_owned(self) -> ESExpr<'static> {
		match self {
			ESExpr::Constructor { name, args, kwargs } => {
				let args: Cow<'static, [ESExpr<'static>]> = Cow::Owned(match args {
					Cow::Borrowed(args) => args.iter().map(ESExpr::as_owned).collect(),
					Cow::Owned(args) => args.into_iter().map(ESExpr::into_owned).collect(),
				});

				let kwargs: Cow<'static, BTreeMap<Cow<'static, str>, ESExpr<'static>>> = Cow::Owned(match kwargs {
					Cow::Borrowed(kwargs) => kwargs
						.iter()
						.map(|(k, v)| (Cow::Owned(k.as_ref().to_owned()), v.as_owned()))
						.collect(),

					Cow::Owned(kwargs) => kwargs
						.into_iter()
						.map(|(k, v)| (Cow::Owned(k.into_owned()), v.into_owned()))
						.collect(),
				});

				ESExpr::Constructor {
					name: Cow::Owned(name.into_owned()),
					args,
					kwargs,
				}
			},
			ESExpr::Bool(b) => ESExpr::Bool(b),
			ESExpr::Int(i) => ESExpr::Int(Cow::Owned(i.into_owned())),
			ESExpr::Str(s) => ESExpr::Str(Cow::Owned(s.into_owned())),
			ESExpr::Binary(b) => ESExpr::Binary(Cow::Owned(b.into_owned())),
			ESExpr::Float32(f) => ESExpr::Float32(f),
			ESExpr::Float64(f) => ESExpr::Float64(f),
			ESExpr::Null(level) => ESExpr::Null(Cow::Owned(level.into_owned())),
		}
	}

	/// Creates an `ESExpr` value from a reference without making a deep copy.
	#[must_use]
	pub fn as_borrowed(&'a self) -> ESExpr<'a> {
		match self {
			ESExpr::Constructor { name, args, kwargs } => ESExpr::Constructor {
				name: Cow::Borrowed(name.as_ref()),
				args: Cow::Borrowed(args.as_ref()),
				kwargs: Cow::Borrowed(kwargs.as_ref()),
			},
			&ESExpr::Bool(b) => ESExpr::Bool(b),
			ESExpr::Int(i) => ESExpr::Int(Cow::Borrowed(i.as_ref())),
			ESExpr::Str(s) => ESExpr::Str(Cow::Borrowed(s.as_ref())),
			ESExpr::Binary(b) => ESExpr::Binary(Cow::Borrowed(b.as_ref())),
			&ESExpr::Float32(f) => ESExpr::Float32(f),
			&ESExpr::Float64(f) => ESExpr::Float64(f),
			ESExpr::Null(level) => ESExpr::Null(Cow::Borrowed(level.as_ref())),
		}
	}
}

impl<'a> ESExprCodec<'a> for ESExpr<'a> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::All;

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		self.as_borrowed()
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		Ok(expr)
	}
}
