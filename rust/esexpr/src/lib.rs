//! esexpr is a library that implements the `ESExpr` format.

use std::borrow::Cow;
use std::collections::{HashMap, VecDeque};
use std::hash::BuildHasher;

pub use esexpr_derive::{ESExprCodec, esexpr_literal as esexpr};
use num_bigint::{BigInt, BigUint};

/// Representation of an `ESExpr` value.
/// Must be one of a constructor, bool, int, string, binary, float32, float64, or null.
#[derive(Debug, Clone, PartialEq)]
pub enum ESExpr<'a> {
	/// A constructor expression.
	/// Can contain positional and keyword arguments.
	Constructor {
		/// The constructor name.
		name: Cow<'a, str>,

		/// Positional argument values.
		args: Cow<'a, [ESExpr<'a>]>,

		/// Keyword argument values.
		kwargs: Cow<'a, HashMap<Cow<'a, str>, ESExpr<'a>>>,
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

				let kwargs: Cow<'static, HashMap<Cow<'static, str>, ESExpr<'static>>> = Cow::Owned(match kwargs {
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

/// An expression tag.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum ESExprTag<'a> {
	/// A tag for a constructor with a specified name.
	Constructor(Cow<'a, str>),

	/// A tag for a bool value.
	Bool,

	/// A tag for a int value.
	Int,

	/// A tag for a str value.
	Str,

	/// A tag for a binary value.
	Binary,

	/// A tag for a float32 value.
	Float32,

	/// A tag for a float64 value.
	Float64,

	/// A tag for a null value.
	Null,
}

impl<'a> ESExprTag<'a> {
	/// Checks whether the tag is for a constructor value.
	#[must_use]
	pub fn is_constructor(&self, s: &str) -> bool {
		match self {
			ESExprTag::Constructor(name) => name == s,
			_ => false,
		}
	}

	/// Creates a copy of a tag without referencing the original.
	#[must_use]
	pub fn into_owned(self) -> ESExprTag<'static> {
		match self {
			ESExprTag::Constructor(name) => ESExprTag::Constructor(Cow::Owned(name.into_owned())),
			ESExprTag::Bool => ESExprTag::Bool,
			ESExprTag::Int => ESExprTag::Int,
			ESExprTag::Str => ESExprTag::Str,
			ESExprTag::Binary => ESExprTag::Binary,
			ESExprTag::Float32 => ESExprTag::Float32,
			ESExprTag::Float64 => ESExprTag::Float64,
			ESExprTag::Null => ESExprTag::Null,
		}
	}

	/// Ensures that a tag does not reference any external data.
	#[must_use]
	pub fn as_owned(&self) -> ESExprTag<'static> {
		match self {
			ESExprTag::Constructor(name) => ESExprTag::Constructor(Cow::Owned(name.as_ref().to_owned())),
			ESExprTag::Bool => ESExprTag::Bool,
			ESExprTag::Int => ESExprTag::Int,
			ESExprTag::Str => ESExprTag::Str,
			ESExprTag::Binary => ESExprTag::Binary,
			ESExprTag::Float32 => ESExprTag::Float32,
			ESExprTag::Float64 => ESExprTag::Float64,
			ESExprTag::Null => ESExprTag::Null,
		}
	}

	/// Creates a copy of a tag without making a deep copy.
	#[must_use]
	pub fn as_borrowed<'b>(&'b self) -> ESExprTag<'b>
	where
		'a: 'b,
	{
		match self {
			ESExprTag::Constructor(name) => ESExprTag::Constructor(Cow::Borrowed(name.as_ref())),
			ESExprTag::Bool => ESExprTag::Bool,
			ESExprTag::Int => ESExprTag::Int,
			ESExprTag::Str => ESExprTag::Str,
			ESExprTag::Binary => ESExprTag::Binary,
			ESExprTag::Float32 => ESExprTag::Float32,
			ESExprTag::Float64 => ESExprTag::Float64,
			ESExprTag::Null => ESExprTag::Null,
		}
	}

	const fn is_equal(&self, b: &ESExprTag) -> bool {
		match self {
			ESExprTag::Constructor(Cow::Borrowed(c1)) => match b {
				ESExprTag::Constructor(Cow::Borrowed(c2)) => compare_str_bytes(c1.as_bytes(), c2.as_bytes()),
				ESExprTag::Constructor(Cow::Owned(c2)) => compare_str_bytes(c1.as_bytes(), c2.as_bytes()),
				_ => false,
			},
			ESExprTag::Constructor(Cow::Owned(c1)) => match b {
				ESExprTag::Constructor(Cow::Borrowed(c2)) => compare_str_bytes(c1.as_bytes(), c2.as_bytes()),
				ESExprTag::Constructor(Cow::Owned(c2)) => compare_str_bytes(c1.as_bytes(), c2.as_bytes()),
				_ => false,
			},
			ESExprTag::Bool => matches!(b, ESExprTag::Bool),
			ESExprTag::Int => matches!(b, ESExprTag::Int),
			ESExprTag::Str => matches!(b, ESExprTag::Str),
			ESExprTag::Binary => matches!(b, ESExprTag::Binary),
			ESExprTag::Float32 => matches!(b, ESExprTag::Float32),
			ESExprTag::Float64 => matches!(b, ESExprTag::Float64),
			ESExprTag::Null => matches!(b, ESExprTag::Null),
		}
	}
}

const fn compare_str_bytes(s1: &[u8], s2: &[u8]) -> bool {
	if s1.len() != s1.len() {
		return false;
	}

	let mut i = 0;
	while i < s1.len() {
		if s1[i] != s2[i] {
			return false;
		}

		i += 1;
	}

	true
}

/// A collection of tags.
/// Used over standard collections to support const operations.
#[derive(Clone, Copy, Debug)]
pub enum ESExprTagCollection {
	/// The set of all tags.
	All,

	/// A collection of tags.
	Tags(&'static [ESExprTag<'static>]),

	/// A compound collection of tags.
	Concat(&'static [ESExprTagCollection]),
}

impl ESExprTagCollection {
	/// Check if a tag collection is empty.
	#[must_use]
	pub const fn is_empty(self) -> bool {
		match self {
			ESExprTagCollection::All => false,
			ESExprTagCollection::Tags(tags) => tags.is_empty(),
			ESExprTagCollection::Concat(mut collections) => loop {
				let Some((&head, tail)) = collections.split_first()
				else {
					return true;
				};

				if !head.is_empty() {
					return false;
				}

				collections = tail;
			},
		}
	}
	
	/// Check if a tag collection is the set of all tags.
	#[must_use]
	pub const fn is_all(self) -> bool {
		match self {
			ESExprTagCollection::All => true,
			ESExprTagCollection::Tags(_) => false,
			ESExprTagCollection::Concat(mut collections) => loop {
				let Some((&head, tail)) = collections.split_first()
				else {
					return false;
				};

				if head.is_all() {
					return true;
				}

				collections = tail;
			},
		}
	}

	/// Check if a tag collection contains a tag.
	#[must_use]
	pub const fn contains(self, tag: &ESExprTag) -> bool {
		match self {
			ESExprTagCollection::All => true,
			ESExprTagCollection::Tags(mut tags) => loop {
				let Some((head, tail)) = tags.split_first()
				else {
					return false;
				};

				if head.is_equal(tag) {
					return true;
				}

				tags = tail;
			},
			ESExprTagCollection::Concat(mut collections) => loop {
				let Some((&head, tail)) = collections.split_first()
				else {
					return false;
				};

				if head.contains(tag) {
					return true;
				}

				collections = tail;
			},
		}
	}

	/// Check if a tag collection is disjoint from another tag collection.
	#[must_use]
	pub const fn is_disjoint(self, other: ESExprTagCollection) -> bool {
		match other {
			ESExprTagCollection::All => self.is_empty(),
			ESExprTagCollection::Tags(mut tags) => loop {
				let Some((head, tail)) = tags.split_first()
				else {
					return true;
				};

				if self.contains(head) {
					return false;
				}

				tags = tail;
			},
			ESExprTagCollection::Concat(mut collections) => loop {
				let Some((&head, tail)) = collections.split_first()
				else {
					return true;
				};

				if !self.is_disjoint(head) {
					return false;
				}

				collections = tail;
			},
		}
	}
	
	/// Check if a tag collection is a subset of another tag collection.
	#[must_use]
	pub const fn is_subset(self, other: ESExprTagCollection) -> bool {
		match self {
			ESExprTagCollection::All => other.is_all(),
			ESExprTagCollection::Tags(mut tags) => loop {
				let Some((head, tail)) = tags.split_first()
				else {
					return true;
				};
				
				if !other.contains(head) {
					return false;
				}
				
				tags = tail;
			},
			ESExprTagCollection::Concat(mut collections) => loop {
				let Some((&head, tail)) = collections.split_first()
				else {
					return true;
				};
				
				if !head.is_subset(other) {
					return false;
				}
				
				collections = tail;
			},
		}
	}
	
	/// Check if a tag collection is equal to another tag collection.
	#[must_use]
	pub const fn is_equal(self, other: ESExprTagCollection) -> bool {
		self.is_subset(other) && other.is_subset(self)
	}
}

impl PartialEq for ESExprTagCollection {
	#[inline]
	fn eq(&self, other: &Self) -> bool {
		self.is_equal(*other)
	}
}

impl Eq for ESExprTagCollection {}

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

impl<'a> ESExprCodec<'a> for ESExpr<'a> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		self.as_borrowed()
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		Ok(expr)
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

impl<'a> ESExprCodec<'a> for bool {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Bool]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Bool(*self)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Bool(b) => Ok(b),
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

impl<'a> ESExprCodec<'a> for BigInt {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Int]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Int(Cow::Borrowed(self))
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Int(i) => Ok(i.into_owned()),
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

impl<'a> ESExprCodec<'a> for BigUint {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Int]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Int(Cow::Owned(BigInt::from(self.clone())))
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Int(i) => match BigUint::try_from(i.into_owned()) {
				Ok(i) => Ok(i),
				Err(_) => Err(DecodeError::new(
					DecodeErrorType::OutOfRange(format!("Unexpected integer value for {}", stringify!($T))),
					DecodeErrorPath::Current,
				)),
			},
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

macro_rules! int_codec {
	($T: ty) => {
		impl<'a> ESExprCodec<'a> for $T {
			const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Int]);

			fn encode_esexpr(&self) -> ESExpr<'a> {
				ESExpr::Int(Cow::Owned(BigInt::from(*self)))
			}

			fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
				match expr {
					ESExpr::Int(i) => match <$T>::try_from(i.into_owned()) {
						Ok(i) => Ok(i),
						Err(_) => Err(DecodeError::new(
							DecodeErrorType::OutOfRange(format!("Unexpected integer value for {}", stringify!($T))),
							DecodeErrorPath::Current,
						)),
					},
					_ => Err(DecodeError::new(
						DecodeErrorType::UnexpectedExpr {
							expected_tags: Self::TAGS,
							actual_tag: expr.tag().into_owned(),
						},
						DecodeErrorPath::Current,
					)),
				}
			}
		}
	};
}

int_codec!(isize);
int_codec!(usize);
int_codec!(i128);
int_codec!(u128);
int_codec!(i64);
int_codec!(u64);
int_codec!(i32);
int_codec!(u32);
int_codec!(i16);
int_codec!(u16);
int_codec!(i8);
int_codec!(u8);

impl<'a> ESExprCodec<'a> for String {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Str]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Str(Cow::Borrowed(self))
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Str(s) => Ok(s.into_owned()),
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

impl<'a> ESExprCodec<'a> for f32 {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Float32]);

	fn encode_esexpr(&self) -> ESExpr<'a> {
		ESExpr::Float32(*self)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Float32(f) => Ok(f),
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

impl<'a> ESExprCodec<'a> for f64 {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Float64]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Float64(*self)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Float64(f) => Ok(f),
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

impl<'a> ESExprCodec<'a> for () {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Null]);

	fn encode_esexpr(&self) -> ESExpr {
		ESExpr::Null(Cow::Owned(BigUint::ZERO))
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Null(level) if *level == BigUint::ZERO => Ok(()),
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for Vec<A> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(Cow::Borrowed("list"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Constructor {
			name: Cow::Borrowed("list"),
			args: self.iter().map(A::encode_esexpr).collect(),
			kwargs: Cow::Owned(HashMap::new()),
		}
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Constructor { name, args, kwargs } if name == "list" => {
				if !kwargs.is_empty() {
					return Err(DecodeError::new(
						DecodeErrorType::OutOfRange("List must not have keyword arguments".to_owned()),
						DecodeErrorPath::Constructor(name.into_owned()),
					));
				}

				Ok(match args {
					Cow::Borrowed(args) => args
						.iter()
						.map(|e| A::decode_esexpr(e.as_borrowed()))
						.collect::<Result<Vec<_>, _>>()?,
					Cow::Owned(args) => args.into_iter().map(A::decode_esexpr).collect::<Result<Vec<_>, _>>()?,
				})
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

impl<'a, A: ESExprCodec<'a>, S: BuildHasher + Default + 'static> ESExprCodec<'a> for HashMap<String, A, S> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Constructor(Cow::Borrowed("dict"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		let mut kwargs = HashMap::new();

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

impl<'a, A: ESExprCodec<'a>> ESExprOptionalFieldCodec<'a> for Option<A> {
	const TAGS: ESExprTagCollection = A::TAGS;

	fn encode_optional_field(&'a self) -> Option<ESExpr<'a>> {
		self.as_ref().map(A::encode_esexpr)
	}

	fn decode_optional_field(value: Option<ESExpr<'a>>) -> Result<Self, DecodeError> {
		value.map(|expr| A::decode_esexpr(expr)).transpose()
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

/// A field codec for dictionary arguments.
pub trait ESExprDictCodec<'a>
where
	Self: Sized + 'a,
{
	/// The tags of the encoded expressions that this type can produce.
	const TAGS: ESExprTagCollection;

	/// Encode dictionary arguments.
	fn encode_dict_element(&'a self, kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>);

	/// Decode dictionary arguments.
	///
	/// # Errors
	/// Will return `Err` if decoding fails.
	fn decode_dict_element(
		kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError>;
}

impl<'a, F: ESExprDictCodec<'a>> ESExprDictCodec<'a> for Box<F> {
	const TAGS: ESExprTagCollection = F::TAGS;

	fn encode_dict_element(&'a self, kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>) {
		(**self).encode_dict_element(kwargs);
	}

	fn decode_dict_element(
		kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		F::decode_dict_element(kwargs, constructor_name).map(Box::new)
	}
}

impl<'a, A: ESExprCodec<'a>, S: BuildHasher + Default + 'static> ESExprDictCodec<'a> for HashMap<String, A, S> {
	const TAGS: ESExprTagCollection = A::TAGS;

	fn encode_dict_element(&'a self, kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>) {
		for (k, v) in self {
			kwargs.insert(Cow::Borrowed(k), v.encode_esexpr());
		}
	}

	fn decode_dict_element(
		kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		kwargs
			.drain()
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

	fn encode_dict_element(&'a self, kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>) {
		for (k, v) in self {
			kwargs.insert(Cow::Borrowed(k.as_ref()), v.encode_esexpr());
		}
	}

	fn decode_dict_element(
		kwargs: &mut HashMap<Cow<'a, str>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		kwargs
			.drain()
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

/// An error that occurs when decoding expressions.
#[derive(Debug, Clone)]
pub struct DecodeError(pub Box<(DecodeErrorType, DecodeErrorPath)>);

impl DecodeError {
	/// Create a `DecodeError`.
	#[must_use]
	pub fn new(error_type: DecodeErrorType, path: DecodeErrorPath) -> Self {
		DecodeError(Box::new((error_type, path)))
	}

	/// Gets the error type.
	#[must_use]
	pub fn error_type(&self) -> &DecodeErrorType {
		&self.0.0
	}

	/// Gets the error path.
	#[must_use]
	pub fn error_path(&self) -> &DecodeErrorPath {
		&self.0.1
	}

	/// Updates the error path, based on the original.
	pub fn error_path_with(&mut self, f: impl FnOnce(DecodeErrorPath) -> DecodeErrorPath) {
		let mut old_path = DecodeErrorPath::Current;
		std::mem::swap(&mut old_path, &mut self.0.1);
		self.0.1 = f(old_path);
	}
}

/// The type of error that occurred while decoding.
#[derive(Debug, Clone)]
pub enum DecodeErrorType {
	/// An expression had a different tag than expected.
	UnexpectedExpr {
		/// The tags that were expected.
		expected_tags: ESExprTagCollection,

		/// The actual tag of the expression.
		actual_tag: ESExprTag<'static>,
	},

	/// Indicates that a value was not valid for the decoded type.
	OutOfRange(String),

	/// Indicates that a keyword argument was missing.
	MissingKeyword(String),

	/// Indicates that a positional argument was missing.
	MissingPositional,
}

/// Specifies where in an expression an error occurred.
#[derive(Debug, Clone)]
pub enum DecodeErrorPath {
	/// Error occurred at the current position in the object.
	Current,

	/// Error occurred at the current position in the object, within a constructor with the specified name.
	Constructor(String),

	/// Error occurred under a positional argument.
	Positional(String, usize, Box<DecodeErrorPath>),

	/// Error occurred under a keyword argument.
	Keyword(String, String, Box<DecodeErrorPath>),
}

#[cfg(test)]
mod tests {
	use super::*;

	#[test]
	fn tag_collection_disjoint() {
		assert!(
			ESExprTagCollection::Tags(&[ESExprTag::Int]).is_disjoint(ESExprTagCollection::Tags(&[ESExprTag::Float32])),
		);

		assert!(
			ESExprTagCollection::Concat(&[
				<Option<i32> as ESExprOptionalFieldCodec>::TAGS,
				<Option<f32> as ESExprOptionalFieldCodec>::TAGS,
			],)
			.is_disjoint(<String as ESExprCodec>::TAGS)
		);
	}
}
