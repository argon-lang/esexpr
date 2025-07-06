use alloc::borrow::Cow;
use alloc::format;
use alloc::string::String;
use half::f16;
use num_bigint::{BigInt, BigUint};
use esexpr::ESExprEncodedEq;
use crate::cowstr::CowStr;
use crate::{DecodeError, DecodeErrorPath, DecodeErrorType, ESExpr, ESExprCodec, ESExprTag, ESExprTagCollection};

macro_rules! encoded_as_partial_eq {
    ($t: ty) => {
		impl ESExprEncodedEq for $t {
			fn is_encoded_eq(&self, other: &Self) -> bool {
				*self == *other
			}
		}
	};
}

encoded_as_partial_eq!(bool);

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

encoded_as_partial_eq!(BigInt);

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

encoded_as_partial_eq!(BigUint);

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
		encoded_as_partial_eq!($T);
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



encoded_as_partial_eq!(String);

impl<'a> ESExprCodec<'a> for String {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Str]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Str(CowStr::Borrowed(self))
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Str(s) => Ok(s.into_string()),
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


impl <'a> ESExprEncodedEq for Cow<'a, str> {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self == other
	}
}

impl<'a> ESExprCodec<'a> for Cow<'a, str> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Str]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Str(CowStr::Borrowed(self.as_ref()))
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Str(s) => Ok(Cow::from(s)),
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


impl <'a> ESExprEncodedEq for CowStr<'a> {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self == other
	}
}

impl<'a> ESExprCodec<'a> for CowStr<'a> {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Str]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::Str(self.as_borrowed())
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Str(s) => Ok(s),
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



impl <'a> ESExprEncodedEq for f16 {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self.to_bits() == other.to_bits()
	}
}

impl<'a> ESExprCodec<'a> for f16 {
	const TAGS: ESExprTagCollection = ESExprTagCollection::Tags(&[ESExprTag::Float16]);

	fn encode_esexpr(&self) -> ESExpr<'a> {
		ESExpr::Float16(*self)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Float16(f) => Ok(f),
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

impl <'a> ESExprEncodedEq for f32 {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self.to_bits() == other.to_bits()
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

impl <'a> ESExprEncodedEq for f64 {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self.to_bits() == other.to_bits()
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


encoded_as_partial_eq!(());

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
