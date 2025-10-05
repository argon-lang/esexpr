use crate::cowstr::CowStr;

/// An expression tag.
#[derive(Debug, Clone, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub enum ESExprTag<'a> {
	/// A tag for a constructor with a specified name.
	Constructor(CowStr<'a>),

	/// A tag for a bool value.
	Bool,

	/// A tag for a int value.
	Int,

	/// A tag for a str value.
	Str,

	/// A tag for a float16 value.
	Float16,

	/// A tag for a float32 value.
	Float32,

	/// A tag for a float64 value.
	Float64,

	/// A tag for an array of 8-bit unsigned integers.
	Array8,

	/// A tag for an array of 16-bit unsigned integers.
	Array16,

	/// A tag for an array of 32-bit unsigned integers.
	Array32,

	/// A tag for an array of 64-bit unsigned integers.
	Array64,

	/// A tag for an array of 128-bit values.
	Array128,

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
			ESExprTag::Constructor(name) => ESExprTag::Constructor(name.into_owned_cowstr()),
			ESExprTag::Bool => ESExprTag::Bool,
			ESExprTag::Int => ESExprTag::Int,
			ESExprTag::Str => ESExprTag::Str,
			ESExprTag::Float16 => ESExprTag::Float16,
			ESExprTag::Float32 => ESExprTag::Float32,
			ESExprTag::Float64 => ESExprTag::Float64,
			ESExprTag::Array8 => ESExprTag::Array8,
			ESExprTag::Array16 => ESExprTag::Array16,
			ESExprTag::Array32 => ESExprTag::Array32,
			ESExprTag::Array64 => ESExprTag::Array64,
			ESExprTag::Array128 => ESExprTag::Array128,
			ESExprTag::Null => ESExprTag::Null,
		}
	}

	/// Ensures that a tag does not reference any external data.
	#[must_use]
	pub fn as_owned(&self) -> ESExprTag<'static> {
		match self {
			ESExprTag::Constructor(name) => ESExprTag::Constructor(name.as_owned_cowstr()),
			ESExprTag::Bool => ESExprTag::Bool,
			ESExprTag::Int => ESExprTag::Int,
			ESExprTag::Str => ESExprTag::Str,
			ESExprTag::Float16 => ESExprTag::Float16,
			ESExprTag::Float32 => ESExprTag::Float32,
			ESExprTag::Float64 => ESExprTag::Float64,
			ESExprTag::Array8 => ESExprTag::Array8,
			ESExprTag::Array16 => ESExprTag::Array16,
			ESExprTag::Array32 => ESExprTag::Array32,
			ESExprTag::Array64 => ESExprTag::Array64,
			ESExprTag::Array128 => ESExprTag::Array128,
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
			ESExprTag::Constructor(name) => ESExprTag::Constructor(name.as_borrowed()),
			ESExprTag::Bool => ESExprTag::Bool,
			ESExprTag::Int => ESExprTag::Int,
			ESExprTag::Str => ESExprTag::Str,
			ESExprTag::Float16 => ESExprTag::Float16,
			ESExprTag::Float32 => ESExprTag::Float32,
			ESExprTag::Float64 => ESExprTag::Float64,
			ESExprTag::Array8 => ESExprTag::Array8,
			ESExprTag::Array16 => ESExprTag::Array16,
			ESExprTag::Array32 => ESExprTag::Array32,
			ESExprTag::Array64 => ESExprTag::Array64,
			ESExprTag::Array128 => ESExprTag::Array128,
			ESExprTag::Null => ESExprTag::Null,
		}
	}

	const fn is_equal(&self, b: &ESExprTag) -> bool {
		match self {
			ESExprTag::Constructor(CowStr::Borrowed(c1) | CowStr::Static(c1)) => match b {
				ESExprTag::Constructor(CowStr::Borrowed(c2) | CowStr::Static(c2)) => {
					compare_str_bytes(c1.as_bytes(), c2.as_bytes())
				},
				ESExprTag::Constructor(CowStr::Owned(c2)) => compare_str_bytes(c1.as_bytes(), c2.as_bytes()),
				_ => false,
			},
			ESExprTag::Constructor(CowStr::Owned(c1)) => match b {
				ESExprTag::Constructor(CowStr::Borrowed(c2) | CowStr::Static(c2)) => {
					compare_str_bytes(c1.as_bytes(), c2.as_bytes())
				},
				ESExprTag::Constructor(CowStr::Owned(c2)) => compare_str_bytes(c1.as_bytes(), c2.as_bytes()),
				_ => false,
			},
			ESExprTag::Bool => matches!(b, ESExprTag::Bool),
			ESExprTag::Int => matches!(b, ESExprTag::Int),
			ESExprTag::Str => matches!(b, ESExprTag::Str),
			ESExprTag::Float16 => matches!(b, ESExprTag::Float16),
			ESExprTag::Float32 => matches!(b, ESExprTag::Float32),
			ESExprTag::Float64 => matches!(b, ESExprTag::Float64),
			ESExprTag::Array8 => matches!(b, ESExprTag::Array8),
			ESExprTag::Array16 => matches!(b, ESExprTag::Array16),
			ESExprTag::Array32 => matches!(b, ESExprTag::Array32),
			ESExprTag::Array64 => matches!(b, ESExprTag::Array64),
			ESExprTag::Array128 => matches!(b, ESExprTag::Array128),
			ESExprTag::Null => matches!(b, ESExprTag::Null),
		}
	}
}

const fn compare_str_bytes(s1: &[u8], s2: &[u8]) -> bool {
	if s1.len() != s2.len() {
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

/// A set of tags.
/// Used over standard collections to support const operations.
#[derive(Clone, Copy, Debug)]
pub enum ESExprTagSet {
	/// The set of all tags.
	All,

	/// A collection of tags.
	Tags(&'static [ESExprTag<'static>]),

	/// A compound collection of tags.
	Concat(&'static [ESExprTagSet]),
}

impl ESExprTagSet {
	/// Check if a tag collection is empty.
	#[must_use]
	pub const fn is_empty(self) -> bool {
		match self {
			ESExprTagSet::All => false,
			ESExprTagSet::Tags(tags) => tags.is_empty(),
			ESExprTagSet::Concat(mut collections) => loop {
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
			ESExprTagSet::All => true,
			ESExprTagSet::Tags(_) => false,
			ESExprTagSet::Concat(mut collections) => loop {
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
			ESExprTagSet::All => true,
			ESExprTagSet::Tags(mut tags) => loop {
				let Some((head, tail)) = tags.split_first()
				else {
					return false;
				};

				if head.is_equal(tag) {
					return true;
				}

				tags = tail;
			},
			ESExprTagSet::Concat(mut collections) => loop {
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
	pub const fn is_disjoint(self, other: ESExprTagSet) -> bool {
		match other {
			ESExprTagSet::All => self.is_empty(),
			ESExprTagSet::Tags(mut tags) => loop {
				let Some((head, tail)) = tags.split_first()
				else {
					return true;
				};

				if self.contains(head) {
					return false;
				}

				tags = tail;
			},
			ESExprTagSet::Concat(mut collections) => loop {
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
	pub const fn is_subset(self, other: ESExprTagSet) -> bool {
		match self {
			ESExprTagSet::All => other.is_all(),
			ESExprTagSet::Tags(mut tags) => loop {
				let Some((head, tail)) = tags.split_first()
				else {
					return true;
				};

				if !other.contains(head) {
					return false;
				}

				tags = tail;
			},
			ESExprTagSet::Concat(mut collections) => loop {
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
	pub const fn is_equal(self, other: ESExprTagSet) -> bool {
		self.is_subset(other) && other.is_subset(self)
	}
}

impl PartialEq for ESExprTagSet {
	#[inline]
	fn eq(&self, other: &Self) -> bool {
		self.is_equal(*other)
	}
}

impl Eq for ESExprTagSet {}

#[cfg(test)]
mod tests {
	use super::*;
	use crate::ESExprCodec;

	#[test]
	fn tag_collection_disjoint() {
		assert!(
			ESExprTagSet::Tags(&[ESExprTag::Int]).is_disjoint(ESExprTagSet::Tags(&[ESExprTag::Float32])),
		);

		assert!(
			ESExprTagSet::Concat(&[
				i32::TAGS,
				f32::TAGS,
			],)
			.is_disjoint(<alloc::string::String as ESExprCodec>::TAGS)
		);
	}

	#[test]
	fn tag_collection_disjoint_substr() {
		let t1: ESExprTagSet = ESExprTagSet::Tags(
			&[
				ESExprTag::Constructor(
					CowStr::Borrowed("record"),
				),
			],
		);

		let t2: ESExprTagSet = ESExprTagSet::Tags(
			&[
				ESExprTag::Constructor(
					CowStr::Borrowed("record-field-literal"),
				),
			],
		);

		assert!(t1.is_disjoint(t2));
	}
}
