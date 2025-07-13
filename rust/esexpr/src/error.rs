use alloc::boxed::Box;
use alloc::string::String;

use crate::{ESExprTag, ESExprTagSet};

/// An error that occurs when decoding expressions.
#[derive(Debug, Clone)]
pub struct DecodeError(Box<(DecodeErrorType, DecodeErrorPath)>);

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
		core::mem::swap(&mut old_path, &mut self.0.1);
		self.0.1 = f(old_path);
	}
}

/// The type of error that occurred while decoding.
#[derive(Debug, Clone)]
pub enum DecodeErrorType {
	/// An expression had a different tag than expected.
	UnexpectedExpr {
		/// The tags that were expected.
		expected_tags: ESExprTagSet,

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
