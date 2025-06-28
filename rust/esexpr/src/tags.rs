use alloc::borrow::{Cow, ToOwned};

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
