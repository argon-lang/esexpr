use alloc::borrow::{Cow, ToOwned};
use alloc::collections::BTreeMap;
use alloc::vec::Vec;
use core::fmt::{Debug, Formatter};

use half::f16;
use hashbrown::HashMap;
use num_bigint::{BigInt, BigUint};

use crate::cowstr::CowStr;
use crate::*;

/// Representation of an `ESExpr` value.
/// Must be a constructor, bool, int, string, float32, float64, {int,uint}{8,16,32,64} or null.
#[derive(Debug, Clone)]
pub enum ESExpr<'a> {
	/// A constructor expression.
	/// Can contain positional and keyword arguments.
	Constructor(ESExprConstructor<'a>),

	/// A bool value.
	Bool(bool),

	/// An integer value.
	Int(Cow<'a, BigInt>),

	/// A string value.
	Str(CowStr<'a>),

	/// A float16 value.
	Float16(f16),

	/// A float32 value.
	Float32(f32),

	/// A float64 value.
	Float64(f64),

	/// An array of 8-bit values
	Array8(Cow<'a, [u8]>),

	/// An array of 16-bit values
	Array16(Cow<'a, [u16]>),

	/// An array of 32-bit values
	Array32(Cow<'a, [u32]>),

	/// An array of 64-bit values
	Array64(Cow<'a, [u64]>),

	/// An array of 128-bit values
	Array128(Cow<'a, [u128]>),

	/// A Null value.
	Null(Cow<'a, BigUint>),
}

impl<'a> ESExpr<'a> {
	/// Create a new constructor expression
	pub fn constructor<'b, S: Into<CowStr<'b>>, A: Into<ConstructorArgs<'a>>, K: Into<KeywordArgs<'a>>>(
		name: S,
		args: A,
		kwargs: K,
	) -> Self
	where
		'b: 'a,
	{
		ESExpr::Constructor(ESExprConstructor {
			name: name.into(),
			args: args.into(),
			kwargs: kwargs.into(),
		})
	}

	/// Get the tag of an expression.
	#[must_use]
	pub fn tag<'b>(&'b self) -> ESExprTag<'b> {
		match self {
			ESExpr::Constructor(ESExprConstructor { name, .. }) => ESExprTag::Constructor(name.as_borrowed()),
			ESExpr::Bool(_) => ESExprTag::Bool,
			ESExpr::Int(_) => ESExprTag::Int,
			ESExpr::Str(_) => ESExprTag::Str,
			ESExpr::Float16(_) => ESExprTag::Float16,
			ESExpr::Float32(_) => ESExprTag::Float32,
			ESExpr::Float64(_) => ESExprTag::Float64,
			ESExpr::Array8(_) => ESExprTag::Array8,
			ESExpr::Array16(_) => ESExprTag::Array16,
			ESExpr::Array32(_) => ESExprTag::Array32,
			ESExpr::Array64(_) => ESExprTag::Array64,
			ESExpr::Array128(_) => ESExprTag::Array128,
			ESExpr::Null(_) => ESExprTag::Null,
		}
	}

	/// Performs a deep clone of the value.
	#[must_use]
	pub fn as_owned(&self) -> ESExpr<'static> {
		match self {
			ESExpr::Constructor(constructor) => ESExpr::Constructor(constructor.as_owned()),
			&ESExpr::Bool(b) => ESExpr::Bool(b),
			ESExpr::Int(i) => ESExpr::Int(Cow::Owned(i.as_ref().clone())),
			ESExpr::Str(s) => ESExpr::Str(s.as_owned_cowstr()),
			&ESExpr::Float16(f) => ESExpr::Float16(f),
			&ESExpr::Float32(f) => ESExpr::Float32(f),
			&ESExpr::Float64(f) => ESExpr::Float64(f),
			ESExpr::Array8(b) => ESExpr::Array8(Cow::Owned(b.as_ref().to_owned())),
			ESExpr::Array16(b) => ESExpr::Array16(Cow::Owned(b.as_ref().to_owned())),
			ESExpr::Array32(b) => ESExpr::Array32(Cow::Owned(b.as_ref().to_owned())),
			ESExpr::Array64(b) => ESExpr::Array64(Cow::Owned(b.as_ref().to_owned())),
			ESExpr::Array128(b) => ESExpr::Array128(Cow::Owned(b.as_ref().to_owned())),
			ESExpr::Null(level) => ESExpr::Null(Cow::Owned(level.as_ref().clone())),
		}
	}

	/// Ensures that any borrowed values are converted to owned values.
	#[must_use]
	pub fn into_owned(self) -> ESExpr<'static> {
		match self {
			ESExpr::Constructor(constructor) => ESExpr::Constructor(constructor.into_owned()),
			ESExpr::Bool(b) => ESExpr::Bool(b),
			ESExpr::Int(i) => ESExpr::Int(Cow::Owned(i.into_owned())),
			ESExpr::Str(s) => ESExpr::Str(s.into_owned_cowstr()),
			ESExpr::Float16(f) => ESExpr::Float16(f),
			ESExpr::Float32(f) => ESExpr::Float32(f),
			ESExpr::Float64(f) => ESExpr::Float64(f),
			ESExpr::Array8(b) => ESExpr::Array8(Cow::Owned(b.into_owned())),
			ESExpr::Array16(b) => ESExpr::Array16(Cow::Owned(b.into_owned())),
			ESExpr::Array32(b) => ESExpr::Array32(Cow::Owned(b.into_owned())),
			ESExpr::Array64(b) => ESExpr::Array64(Cow::Owned(b.into_owned())),
			ESExpr::Array128(b) => ESExpr::Array128(Cow::Owned(b.into_owned())),
			ESExpr::Null(level) => ESExpr::Null(Cow::Owned(level.into_owned())),
		}
	}

	/// Creates an `ESExpr` value from a reference without making a deep copy.
	#[must_use]
	pub fn as_borrowed<'b>(&'b self) -> ESExpr<'b>
	where
		'a: 'b,
	{
		match self {
			ESExpr::Constructor(constructor) => ESExpr::Constructor(constructor.as_borrowed()),
			&ESExpr::Bool(b) => ESExpr::Bool(b),
			ESExpr::Int(i) => ESExpr::Int(Cow::Borrowed(i.as_ref())),
			ESExpr::Str(s) => ESExpr::Str(s.as_borrowed()),
			&ESExpr::Float16(f) => ESExpr::Float16(f),
			&ESExpr::Float32(f) => ESExpr::Float32(f),
			&ESExpr::Float64(f) => ESExpr::Float64(f),
			ESExpr::Array8(b) => ESExpr::Array8(Cow::Borrowed(b.as_ref())),
			ESExpr::Array16(b) => ESExpr::Array16(Cow::Borrowed(b.as_ref())),
			ESExpr::Array32(b) => ESExpr::Array32(Cow::Borrowed(b.as_ref())),
			ESExpr::Array64(b) => ESExpr::Array64(Cow::Borrowed(b.as_ref())),
			ESExpr::Array128(b) => ESExpr::Array128(Cow::Borrowed(b.as_ref())),
			ESExpr::Null(level) => ESExpr::Null(Cow::Borrowed(level.as_ref())),
		}
	}
}

impl<'a, 'b> PartialEq<ESExpr<'b>> for ESExpr<'a> {
	fn eq(&self, other: &ESExpr<'b>) -> bool {
		match self {
			ESExpr::Constructor(ESExprConstructor {
				name: name1,
				args: args1,
				kwargs: kwargs1,
			}) => {
				let ESExpr::Constructor(ESExprConstructor {
					name: name2,
					args: args2,
					kwargs: kwargs2,
				}) = other
				else {
					return false;
				};

				name1 == name2 &&
					args1 == args2 && kwargs1
					.iter()
					.zip(kwargs2.iter())
					.all(|((k1, v1), (k2, v2))| k1 == k2 && v1 == v2)
			},
			&ESExpr::Bool(b1) => matches!(other, &ESExpr::Bool(b2) if b1 == b2),
			ESExpr::Int(i1) => matches!(other, ESExpr::Int(i2) if i1 == i2),
			ESExpr::Str(s1) => matches!(other, ESExpr::Str(s2) if s1 == s2),
			&ESExpr::Float16(f1) => matches!(other, &ESExpr::Float16(f2) if f1.to_bits() == f2.to_bits()),
			&ESExpr::Float32(f1) => matches!(other, &ESExpr::Float32(f2) if f1.to_bits() == f2.to_bits()),
			&ESExpr::Float64(f1) => matches!(other, &ESExpr::Float64(f2) if f1.to_bits() == f2.to_bits()),
			ESExpr::Array8(a1) => matches!(other, ESExpr::Array8(a2) if a1 == a2),
			ESExpr::Array16(a1) => matches!(other, ESExpr::Array16(a2) if a1 == a2),
			ESExpr::Array32(a1) => matches!(other, ESExpr::Array32(a2) if a1 == a2),
			ESExpr::Array64(a1) => matches!(other, ESExpr::Array64(a2) if a1 == a2),
			ESExpr::Array128(a1) => matches!(other, ESExpr::Array128(a2) if a1 == a2),
			ESExpr::Null(l1) => matches!(other, ESExpr::Null(l2) if l1 == l2),
		}
	}
}

impl<'a> Eq for ESExpr<'a> {}

impl<'a> ESExprEncodedEq for ESExpr<'a> {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self == other
	}
}


impl<'a> ESExprCodec<'a> for ESExpr<'a> {
	const TAGS: ESExprTagSet = ESExprTagSet::All;

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		self.as_borrowed()
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		Ok(expr)
	}
}

/// A wrapper for a `ESExpr<'static>`
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ESExprStatic(ESExpr<'static>);

impl ESExprStatic {
	/// Create an `ESExprStatic`
	pub fn new(e: ESExpr<'static>) -> Self {
		ESExprStatic(e)
	}
	
	/// Get the underlying `ESExpr`.
	pub fn into_inner(self) -> ESExpr<'static> {
		self.0
	}
}

impl ESExprEncodedEq for ESExprStatic {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self == other
	}
}

impl<'a> ESExprCodec<'a> for ESExprStatic {
	const TAGS: ESExprTagSet = ESExprTagSet::All;

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		self.0.as_borrowed()
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		Ok(ESExprStatic(expr.into_owned()))
	}
}

/// A `ESExpr` constructor expression
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ESExprConstructor<'a> {
	/// The name of the constructor.
	pub name: CowStr<'a>,

	/// The constructor's positional arguments.
	pub args: ConstructorArgs<'a>,

	/// The constructor's keyword arguments.
	pub kwargs: KeywordArgs<'a>,
}

impl<'a> ESExprConstructor<'a> {
	fn into_owned(self) -> ESExprConstructor<'static> {
		ESExprConstructor {
			name: self.name.as_owned_cowstr(),
			args: self.args.into_owned(),
			kwargs: self.kwargs.into_owned(),
		}
	}

	fn as_owned(&self) -> ESExprConstructor<'static> {
		ESExprConstructor {
			name: self.name.as_owned_cowstr(),
			args: self.args.as_owned(),
			kwargs: self.kwargs.as_owned(),
		}
	}

	fn as_borrowed<'b>(&'b self) -> ESExprConstructor<'b> {
		ESExprConstructor {
			name: self.name.as_borrowed(),
			args: self.args.as_borrowed(),
			kwargs: self.kwargs.as_borrowed(),
		}
	}
}

/// Positional arguments of an `ESExprConstructor`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ConstructorArgs<'a> {
	args: ConstructorArgsInner<'a>,
}

impl<'a> ConstructorArgs<'a> {
	/// Iterates over constructor arguments.
	pub fn iter(&'a self) -> ConstructorArgsIntoIter<'a> {
		self.as_borrowed().into_iter()
	}

	/// The number of constructor arguments.
	#[must_use]
	pub fn len(&self) -> usize {
		self.args.as_slice().len()
	}

	/// Gets whether there are any arguments.
	#[must_use]
	pub fn is_empty(&self) -> bool {
		self.args.as_slice().is_empty()
	}

	fn into_owned(self) -> ConstructorArgs<'static> {
		ConstructorArgs {
			args: ConstructorArgsInner::Owned(self.into_iter().map(ESExpr::into_owned).collect()),
		}
	}

	fn as_owned(&self) -> ConstructorArgs<'static> {
		ConstructorArgs {
			args: ConstructorArgsInner::Owned(self.into_iter().map(ESExpr::into_owned).collect()),
		}
	}

	fn as_borrowed<'b>(&'b self) -> ConstructorArgs<'b> {
		ConstructorArgs {
			args: match &self.args {
				ConstructorArgsInner::Owned(args) => ConstructorArgsInner::Borrowed(args),
				ConstructorArgsInner::Borrowed(args) => ConstructorArgsInner::Borrowed(args),
			},
		}
	}
}

impl<'a> From<Vec<ESExpr<'a>>> for ConstructorArgs<'a> {
	fn from(args: Vec<ESExpr<'a>>) -> Self {
		ConstructorArgs {
			args: ConstructorArgsInner::Owned(args),
		}
	}
}

impl<'a> From<&'a [ESExpr<'a>]> for ConstructorArgs<'a> {
	fn from(args: &'a [ESExpr<'a>]) -> Self {
		ConstructorArgs {
			args: ConstructorArgsInner::Borrowed(args),
		}
	}
}

impl<'a, const N: usize> From<[ESExpr<'a>; N]> for ConstructorArgs<'a> {
	fn from(args: [ESExpr<'a>; N]) -> Self {
		ConstructorArgs {
			args: ConstructorArgsInner::Owned(args.to_vec()),
		}
	}
}

impl<'a> From<ConstructorArgs<'a>> for Vec<ESExpr<'a>> {
	fn from(args: ConstructorArgs<'a>) -> Self {
		match args.args {
			ConstructorArgsInner::Owned(args) => args,
			ConstructorArgsInner::Borrowed(args) => args.iter().map(ESExpr::as_borrowed).collect(),
		}
	}
}

impl<'a> IntoIterator for ConstructorArgs<'a> {
	type Item = ESExpr<'a>;
	type IntoIter = ConstructorArgsIntoIter<'a>;

	fn into_iter(self) -> Self::IntoIter {
		ConstructorArgsIntoIter {
			inner_iter: match self.args {
				ConstructorArgsInner::Owned(args) => ConstructorArgsInnerIntoIter::Owned(args.into_iter()),
				ConstructorArgsInner::Borrowed(args) => ConstructorArgsInnerIntoIter::Borrowed(args.iter()),
			},
		}
	}
}

impl<'a> IntoIterator for &'a ConstructorArgs<'a> {
	type Item = ESExpr<'a>;
	type IntoIter = ConstructorArgsIntoIter<'a>;

	fn into_iter(self) -> Self::IntoIter {
		self.as_borrowed().into_iter()
	}
}

/// `ESExpr` Constructor arguments
/// Used instead of `Cow` because of lifetime variance.
#[derive(Clone)]
enum ConstructorArgsInner<'a> {
	/// Constructor arguments owned by this constructor.
	Owned(Vec<ESExpr<'a>>),

	/// Constructor arguments borrowed from another constructor.
	Borrowed(&'a [ESExpr<'a>]),
}

impl<'a> ConstructorArgsInner<'a> {
	fn as_slice(&self) -> &[ESExpr<'a>] {
		match self {
			ConstructorArgsInner::Owned(args) => args,
			ConstructorArgsInner::Borrowed(args) => args,
		}
	}
}

impl<'a> Debug for ConstructorArgsInner<'a> {
	fn fmt(&self, f: &mut Formatter<'_>) -> core::fmt::Result {
		self.as_slice().fmt(f)
	}
}

impl<'a> PartialEq for ConstructorArgsInner<'a> {
	fn eq(&self, other: &Self) -> bool {
		self.as_slice() == other.as_slice()
	}
}

impl<'a> Eq for ConstructorArgsInner<'a> {}

#[must_use]
pub struct ConstructorArgsIntoIter<'a> {
	inner_iter: ConstructorArgsInnerIntoIter<'a>,
}

impl<'a> Iterator for ConstructorArgsIntoIter<'a> {
	type Item = ESExpr<'a>;

	fn next(&mut self) -> Option<Self::Item> {
		self.inner_iter.next()
	}
}

enum ConstructorArgsInnerIntoIter<'a> {
	Owned(alloc::vec::IntoIter<ESExpr<'a>>),
	Borrowed(alloc::slice::Iter<'a, ESExpr<'a>>),
}

impl<'a> Iterator for ConstructorArgsInnerIntoIter<'a> {
	type Item = ESExpr<'a>;

	fn next(&mut self) -> Option<Self::Item> {
		match self {
			ConstructorArgsInnerIntoIter::Owned(iter) => iter.next(),
			ConstructorArgsInnerIntoIter::Borrowed(iter) => iter.next().map(ESExpr::as_borrowed),
		}
	}
}

/// `ESExpr` constructor keyword arguments
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct KeywordArgs<'a> {
	kwargs: KeywordArgsInner<'a>,
}

impl<'a> KeywordArgs<'a> {
	/// Iterate keyword arguments
	pub fn iter(&'a self) -> KeywordArgsIntoIter<'a> {
		self.into_iter()
	}

	/// The number of keyword arguments.
	#[must_use]
	pub fn len(&self) -> usize {
		self.kwargs.as_map().len()
	}

	/// Checks if there are any keyword arguments
	#[must_use]
	pub fn is_empty(&self) -> bool {
		self.kwargs.as_map().is_empty()
	}

	fn into_owned(self) -> KeywordArgs<'static> {
		KeywordArgs {
			kwargs: KeywordArgsInner::Owned(
				self.into_iter()
					.map(|(k, v)| (k.into_owned_cowstr(), v.into_owned()))
					.collect(),
			),
		}
	}

	fn as_owned(&self) -> KeywordArgs<'static> {
		KeywordArgs {
			kwargs: KeywordArgsInner::Owned(
				self.iter()
					.map(|(k, v)| (k.into_owned_cowstr(), v.into_owned()))
					.collect(),
			),
		}
	}

	fn as_borrowed<'b>(&'b self) -> KeywordArgs<'b> {
		KeywordArgs {
			kwargs: match &self.kwargs {
				KeywordArgsInner::Owned(kwargs) => KeywordArgsInner::Borrowed(kwargs),
				KeywordArgsInner::Borrowed(kwargs) => KeywordArgsInner::Borrowed(kwargs),
			},
		}
	}
}

impl<'a> From<HashMap<CowStr<'a>, ESExpr<'a>>> for KeywordArgs<'a> {
	fn from(kwargs: HashMap<CowStr<'a>, ESExpr<'a>>) -> Self {
		KeywordArgs {
			kwargs: KeywordArgsInner::Owned(kwargs),
		}
	}
}

impl<'a> From<&'a HashMap<CowStr<'a>, ESExpr<'a>>> for KeywordArgs<'a> {
	fn from(kwargs: &'a HashMap<CowStr<'a>, ESExpr<'a>>) -> Self {
		KeywordArgs {
			kwargs: KeywordArgsInner::Borrowed(kwargs),
		}
	}
}

impl<'a> From<BTreeMap<CowStr<'a>, ESExpr<'a>>> for KeywordArgs<'a> {
	fn from(kwargs: BTreeMap<CowStr<'a>, ESExpr<'a>>) -> Self {
		KeywordArgs {
			kwargs: KeywordArgsInner::Owned(
				kwargs.into_iter()
					.map(|(k, v)| (k.into_owned_cowstr(), v))
					.collect::<HashMap<_, _>>()
			),
		}
	}
}

impl<'a> From<&'a BTreeMap<CowStr<'a>, ESExpr<'a>>> for KeywordArgs<'a> {
	fn from(kwargs: &'a BTreeMap<CowStr<'a>, ESExpr<'a>>) -> Self {
		KeywordArgs {
			kwargs: KeywordArgsInner::Owned(
				kwargs.iter()
					.map(|(k, v)| (k.as_borrowed(), v.as_borrowed()))
					.collect::<HashMap<_, _>>()
			),
		}
	}
}

impl<'a, const N: usize> From<[(CowStr<'a>, ESExpr<'a>); N]> for KeywordArgs<'a> {
	fn from(value: [(CowStr<'a>, ESExpr<'a>); N]) -> Self {
		KeywordArgs {
			kwargs: KeywordArgsInner::Owned(HashMap::from(value)),
		}
	}
}

impl<'a> From<KeywordArgs<'a>> for HashMap<CowStr<'a>, ESExpr<'a>> {
	fn from(kwargs: KeywordArgs<'a>) -> Self {
		match kwargs.kwargs {
			KeywordArgsInner::Owned(kwargs) => kwargs,
			KeywordArgsInner::Borrowed(kwargs) => {
				kwargs.iter().map(|(k, v)| (k.as_owned_cowstr(), v.clone())).collect()
			},
		}
	}
}

impl<'a> IntoIterator for KeywordArgs<'a> {
	type Item = (CowStr<'a>, ESExpr<'a>);
	type IntoIter = KeywordArgsIntoIter<'a>;

	fn into_iter(self) -> Self::IntoIter {
		KeywordArgsIntoIter {
			inner_iter: match self.kwargs {
				KeywordArgsInner::Owned(kwargs) => KeywordArgsInnerIntoIter::Owned(kwargs.into_iter()),
				KeywordArgsInner::Borrowed(kwargs) => KeywordArgsInnerIntoIter::Borrowed(kwargs.iter()),
			},
		}
	}
}

impl<'a> IntoIterator for &'a KeywordArgs<'a> {
	type Item = (CowStr<'a>, ESExpr<'a>);
	type IntoIter = KeywordArgsIntoIter<'a>;

	fn into_iter(self) -> Self::IntoIter {
		self.as_borrowed().into_iter()
	}
}

#[derive(Clone)]
enum KeywordArgsInner<'a> {
	Owned(HashMap<CowStr<'a>, ESExpr<'a>>),
	Borrowed(&'a HashMap<CowStr<'a>, ESExpr<'a>>),
}

impl<'a> KeywordArgsInner<'a> {
	fn as_map(&self) -> &HashMap<CowStr<'a>, ESExpr<'a>> {
		match self {
			KeywordArgsInner::Owned(kwargs) => kwargs,
			KeywordArgsInner::Borrowed(kwargs) => kwargs,
		}
	}
}

impl<'a> Debug for KeywordArgsInner<'a> {
	fn fmt(&self, f: &mut Formatter<'_>) -> core::fmt::Result {
		self.as_map().fmt(f)
	}
}

impl<'a> PartialEq for KeywordArgsInner<'a> {
	fn eq(&self, other: &Self) -> bool {
		self.as_map() == other.as_map()
	}
}

impl<'a> Eq for KeywordArgsInner<'a> {}

#[must_use]
pub struct KeywordArgsIntoIter<'a> {
	inner_iter: KeywordArgsInnerIntoIter<'a>,
}

impl<'a> Iterator for KeywordArgsIntoIter<'a> {
	type Item = (CowStr<'a>, ESExpr<'a>);

	fn next(&mut self) -> Option<Self::Item> {
		self.inner_iter.next()
	}
}

enum KeywordArgsInnerIntoIter<'a> {
	Owned(hashbrown::hash_map::IntoIter<CowStr<'a>, ESExpr<'a>>),
	Borrowed(hashbrown::hash_map::Iter<'a, CowStr<'a>, ESExpr<'a>>),
}

impl<'a> Iterator for KeywordArgsInnerIntoIter<'a> {
	type Item = (CowStr<'a>, ESExpr<'a>);

	fn next(&mut self) -> Option<Self::Item> {
		match self {
			KeywordArgsInnerIntoIter::Owned(iter) => iter.next(),
			KeywordArgsInnerIntoIter::Borrowed(iter) => {
				iter.next().map(|(k, v)| (k.as_borrowed(), ESExpr::as_borrowed(v)))
			},
		}
	}
}
