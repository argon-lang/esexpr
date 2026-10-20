//! Representations of `ESExpr` as JSON and vice versa.
#![no_std]

extern crate alloc;
extern crate core;

use alloc::borrow::Cow;
use alloc::collections::BTreeMap;
use alloc::string::String;
use alloc::vec::Vec;
use core::f32;

use base64::Engine;
use base64::prelude::BASE64_STANDARD;
use esexpr::cowstr::CowStr;
use esexpr::{ESExpr, ESExprCodec, ESExprConstructor, ESExprEncodedEq};
use half::f16;
use num_bigint::{BigInt, BigUint};

/// An `ESExpr` representation of a JSON value.
#[derive(ESExprCodec, ESExprEncodedEq, Debug, PartialEq)]
pub enum JsonExpr {
	/// A JSON Object
	Obj {
		/// The fields of the object.
		#[esexpr(dict)]
		values: BTreeMap<String, JsonExpr>,
	},

	/// A JSON Array
	#[esexpr(inline_value)]
	Arr(Vec<JsonExpr>),

	/// A JSON String
	#[esexpr(inline_value)]
	Str(String),

	/// A JSON Number
	#[esexpr(inline_value)]
	Num(f64),

	/// A JSON Boolean
	#[esexpr(inline_value)]
	Bool(bool),

	/// A JSON Null value
	#[esexpr(inline_value)]
	Null(()),
}

impl JsonExpr {
	/// Converts a dynamic value into a JSON expression, rejecting non-JSON types.
	#[expect(clippy::cast_precision_loss, reason = "JSON expressions represent numbers as f64")]
	pub fn from_json(value: deser_value::Value) -> Option<Self> {
		use deser_value::Kind;
		Some(match value.into_kind() {
			Kind::Null => Self::Null(()),
			Kind::Bool(b) => Self::Bool(b),
			Kind::Str(s) => Self::Str(s),
			Kind::U64(n) => Self::Num(n as f64),
			Kind::I64(n) => Self::Num(n as f64),
			Kind::F32(n) if n.is_finite() => Self::Num(f64::from(n)),
			Kind::F64(n) if n.is_finite() => Self::Num(n),
			Kind::Seq(arr) => Self::Arr(arr.into_iter().map(Self::from_json).collect::<Option<_>>()?),
			Kind::Map(obj) => Self::Obj {
				values: obj
					.into_iter()
					.map(|(k, v)| Some((k.as_str()?.into(), Self::from_json(v)?)))
					.collect::<Option<_>>()?,
			},
			_ => return None,
		})
	}

	/// Converts a JSON expression into a dynamic value, rejecting nonfinite numbers.
	pub fn into_json(self) -> Option<deser_value::Value> {
		Some(match self {
			Self::Null(()) => ().into(),
			Self::Bool(b) => b.into(),
			Self::Str(s) => s.into(),
			Self::Num(n) if n.is_finite() => n.into(),
			Self::Num(_) => return None,
			Self::Arr(arr) => arr.into_iter().map(Self::into_json).collect::<Option<Vec<_>>>()?.into(),
			Self::Obj { values } => values
				.into_iter()
				.map(|(k, v)| Some((k, v.into_json()?)))
				.collect::<Option<deser_value::Value>>()?,
		})
	}
}

/// Represents an `ESExpr` encoded as JSON with type information
#[derive(deser::Serialize, deser::Deserialize, Debug, PartialEq)]
#[deser(untagged)]
pub enum JsonEncodedESExpr {
	/// A constructor with a name and optional arguments
	Constructor {
		/// The name of the constructor
		constructor_name: String,
		/// The positional arguments
		args: Option<Vec<JsonEncodedESExpr>>,
		/// The keyword arguments
		kwargs: Option<BTreeMap<String, JsonEncodedESExpr>>,
	},
	/// A list of expressions
	List(Vec<JsonEncodedESExpr>),

	/// A bool value
	Bool(bool),

	/// A string value
	Str(#[deser(deserialize_as = StrictString)] String),

	/// An arbitrary-precision integer encoded directly as a JSON number.
	Int(#[deser(as = deser::adapters::FromInto<NativeBigInt>)] BigInt),

	/// Binary data encoded as base64
	BinaryBase64 {
		/// The base64 encoded data
		base64: Base64Value,
	},

	/// An array of 8-bit values.
	Array8 {
		///	The 8-bit values encoded as uint8.
		#[deser(as = Vec<deser::adapters::Same>)]
		array8: Vec<u8>,
	},

	/// An array of 16-bit values.
	Array16 {
		///    The 16-bit values encoded as uint16.
		array16: Vec<u16>,
	},

	/// An array of 32-bit values.
	Array32 {
		///    The 32-bit values encoded as uint32.
		array32: Vec<u32>,
	},

	/// An array of 64-bit values.
	Array64 {
		///    The 64-bit values encoded as uint64.
		array64: Vec<u64>,
	},

	/// An array of 128-bit values.
	Array128 {
		/// The 128-bit values, encoded as JSON integers.
		array128: Vec<u128>,
	},

	/// A 16-bit floating point number
	Float16 {
		/// The float32 value
		#[deser(as = Float16)]
		float16: f16,
	},

	/// A 32-bit floating point number
	Float32 {
		/// The float32 value
		#[deser(as = Float32)]
		float32: f32,
	},

	/// A 64-bit floating point number
	Float64 {
		/// The float64 value
		#[deser(as = Float64)]
		float64: f64,
	},

	/// A null value
	Null(()),

	/// A null value with a level
	NullLevel {
		/// The level of the null value
		#[deser(as = deser::adapters::TryFromInto<NativeBigInt>)]
		null: BigUint,
	},
}

impl JsonEncodedESExpr {
	/// Converts an `ESExpr` into a `JsonEncodedESExpr`
	pub fn from_esexpr(expr: ESExpr) -> Self {
		match expr {
			ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) => JsonEncodedESExpr::Constructor {
				constructor_name: name.into_string(),
				args: Some(args.into_iter().map(Self::from_esexpr).collect()),
				kwargs: Some(
					kwargs
						.into_iter()
						.map(|(k, v)| (k.into_string(), Self::from_esexpr(v)))
						.collect(),
				),
			},
			ESExpr::Bool(b) => JsonEncodedESExpr::Bool(b),
			ESExpr::Int(i) => JsonEncodedESExpr::Int(i.into_owned()),
			ESExpr::Str(s) => JsonEncodedESExpr::Str(s.into_string()),
			ESExpr::Float16(float16) => JsonEncodedESExpr::Float16 { float16 },
			ESExpr::Float32(float32) => JsonEncodedESExpr::Float32 { float32 },
			ESExpr::Float64(float64) => JsonEncodedESExpr::Float64 { float64 },
			ESExpr::Array8(b) => JsonEncodedESExpr::BinaryBase64 {
				base64: Base64Value(b.into_owned()),
			},
			ESExpr::Array16(b) => JsonEncodedESExpr::Array16 {
				array16: b.into_owned(),
			},
			ESExpr::Array32(b) => JsonEncodedESExpr::Array32 {
				array32: b.into_owned(),
			},
			ESExpr::Array64(b) => JsonEncodedESExpr::Array64 {
				array64: b.into_owned(),
			},
			ESExpr::Array128(b) => JsonEncodedESExpr::Array128 {
				array128: b.into_owned(),
			},
			ESExpr::Null(level) if *level == BigUint::ZERO => JsonEncodedESExpr::Null(()),
			ESExpr::Null(level) => JsonEncodedESExpr::NullLevel {
				null: level.into_owned(),
			},
		}
	}

	/// Converts a `JsonEncodedESExpr` into an `ESExpr`
	pub fn into_esexpr(self) -> ESExpr<'static> {
		match self {
			JsonEncodedESExpr::Constructor {
				constructor_name,
				args,
				kwargs,
			} => ESExpr::constructor(
				constructor_name,
				args.unwrap_or_default()
					.into_iter()
					.map(Self::into_esexpr)
					.collect::<Vec<_>>(),
				kwargs
					.unwrap_or_default()
					.into_iter()
					.map(|(k, v)| (CowStr::Owned(k), v.into_esexpr()))
					.collect::<BTreeMap<_, _>>(),
			),
			JsonEncodedESExpr::List(l) => {
				ESExpr::constructor("list", l.into_iter().map(Self::into_esexpr).collect::<Vec<_>>(), [])
			},

			JsonEncodedESExpr::Bool(b) => ESExpr::Bool(b),
			JsonEncodedESExpr::Int(int) => ESExpr::Int(Cow::Owned(int)),
			JsonEncodedESExpr::Str(s) => ESExpr::Str(CowStr::Owned(s)),
			JsonEncodedESExpr::BinaryBase64 { base64 } => ESExpr::Array8(Cow::Owned(base64.0)),
			JsonEncodedESExpr::Array8 { array8 } => ESExpr::Array8(Cow::Owned(array8)),
			JsonEncodedESExpr::Array16 { array16 } => ESExpr::Array16(Cow::Owned(array16)),
			JsonEncodedESExpr::Array32 { array32 } => ESExpr::Array32(Cow::Owned(array32)),
			JsonEncodedESExpr::Array64 { array64 } => ESExpr::Array64(Cow::Owned(array64)),
			JsonEncodedESExpr::Array128 { array128 } => ESExpr::Array128(Cow::Owned(array128)),
			JsonEncodedESExpr::Float16 { float16 } => ESExpr::Float16(float16),
			JsonEncodedESExpr::Float32 { float32 } => ESExpr::Float32(float32),
			JsonEncodedESExpr::Float64 { float64 } => ESExpr::Float64(float64),
			JsonEncodedESExpr::Null(_) => ESExpr::Null(Cow::Owned(BigUint::ZERO)),
			JsonEncodedESExpr::NullLevel { null } => ESExpr::Null(Cow::Owned(null)),
		}
	}
}

/// Wrapper type for base64-encoded binary data
#[derive(Debug, PartialEq)]
pub struct Base64Value(Vec<u8>);

use alloc::string::ToString;

use deser::de::{Slot, default_atom};
use deser::ser::Emit;
use deser::{Atom, Deserialize, Error, ErrorKind, Serialize, State};

impl Serialize for Base64Value {
	fn serialize<'a>(value: &'a Self, _state: &mut State) -> Result<Emit<'a>, Error> {
		Ok(Emit::Atom(Atom::Str(BASE64_STANDARD.encode(&value.0).into())))
	}
}
impl<'de> Deserialize<'de> for Base64Value {
	fn deserialize_atom(slot: &mut Slot<Self>, atom: Atom, state: &mut State) -> Result<(), Error> {
		if let Atom::Str(s) = atom {
			slot.set(Self(
				BASE64_STANDARD
					.decode(s.as_bytes())
					.map_err(|_| Error::new(ErrorKind::InvalidValue, "invalid base64"))?,
			));
			Ok(())
		}
		else {
			default_atom(slot, atom, state)
		}
	}
}

macro_rules! float_adapter {
	($name:ident, $ty:ty, $from:expr, $to:expr, $atom:expr) => {
		struct $name;
		impl Serialize<$ty> for $name {
			fn serialize<'a>(value: &'a $ty, _state: &mut State) -> Result<Emit<'a>, Error> {
				let f = ($to)(*value);
				Ok(Emit::Atom(if f.is_nan() {
					Atom::Str("nan".into())
				}
				else if f == f64::INFINITY {
					Atom::Str("+inf".into())
				}
				else if f == f64::NEG_INFINITY {
					Atom::Str("-inf".into())
				}
				else {
					($atom)(f)
				}))
			}
		}
		impl<'de> Deserialize<'de, $ty> for $name {
			#[expect(
				clippy::cast_precision_loss,
				reason = "Typed floats accept JSON integers with floating point precision"
			)]
			fn deserialize_atom(slot: &mut Slot<$ty, Self>, atom: Atom, state: &mut State) -> Result<(), Error> {
				let f = match atom {
					Atom::U64(n) => n as f64,
					Atom::I64(n) => n as f64,
					Atom::F64(n) => n,
					Atom::F32(n) => f64::from(n),
					Atom::Str(s) => match s.as_ref() {
						"nan" => f64::NAN,
						"+inf" => f64::INFINITY,
						"-inf" => f64::NEG_INFINITY,
						_ => return Err(Error::new(ErrorKind::InvalidValue, "invalid float")),
					},
					other => return default_atom(slot, other, state),
				};
				slot.set(($from)(f));
				Ok(())
			}
		}
	};
}
float_adapter!(Float16, f16, f16::from_f64, f64::from, float32_atom);
float_adapter!(Float32, f32, narrow_float, f64::from, float32_atom);
float_adapter!(Float64, f64, |f| f, |f: f64| f, Atom::F64);

#[expect(
	clippy::cast_possible_truncation,
	reason = "The encoded type explicitly requests f32 precision"
)]
fn narrow_float(value: f64) -> f32 {
	value as f32
}
fn float32_atom(value: f64) -> Atom<'static> {
	Atom::F32(narrow_float(value))
}

// Bridge num-bigint 0.5 to Deser's arbitrary precision integer extension.
#[derive(deser::Serialize, deser::Deserialize)]
#[deser(transparent)]
struct NativeBigInt(deser::ext::BigInt);

impl From<BigInt> for NativeBigInt {
	fn from(value: BigInt) -> Self {
		let (sign, magnitude) = value.to_bytes_be();
		Self(deser::ext::BigInt {
			negative: sign == num_bigint::Sign::Minus,
			magnitude,
		})
	}
}

impl From<NativeBigInt> for BigInt {
	fn from(value: NativeBigInt) -> Self {
		let sign = if value.0.negative {
			num_bigint::Sign::Minus
		}
		else {
			num_bigint::Sign::Plus
		};
		Self::from_bytes_be(sign, &value.0.magnitude)
	}
}

struct StrictString;
impl<'de> Deserialize<'de, String> for StrictString {
	fn deserialize_atom(slot: &mut Slot<String, Self>, atom: Atom, _state: &mut State) -> Result<(), Error> {
		if let Atom::Str(value) = atom {
			slot.set(value.to_string());
			Ok(())
		}
		else {
			Err(Error::new(ErrorKind::InvalidValue, "expected a JSON string"))
		}
	}
}

impl From<BigUint> for NativeBigInt {
	fn from(value: BigUint) -> Self {
		Self(deser::ext::BigInt {
			negative: false,
			magnitude: value.to_bytes_be(),
		})
	}
}
impl TryFrom<NativeBigInt> for BigUint {
	type Error = Error;
	fn try_from(value: NativeBigInt) -> Result<Self, Error> {
		if value.0.is_negative() {
			return Err(Error::new(ErrorKind::InvalidValue, "negative null level"));
		}
		Ok(Self::from_bytes_be(&value.0.magnitude))
	}
}
