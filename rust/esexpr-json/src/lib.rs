//! Representations of `ESExpr` as JSON and vice versa.
#![no_std]

extern crate alloc;
extern crate core;

use alloc::borrow::{Cow, ToOwned};
use alloc::collections::BTreeMap;
use alloc::string::String;
use alloc::vec::Vec;
use core::f32;

use base64::Engine;
use base64::prelude::BASE64_STANDARD;
use esexpr::{ESExpr, ESExprCodec};
use num_bigint::{BigInt, BigUint};

/// An `ESExpr` representation of a JSON value.
#[derive(ESExprCodec, Debug, PartialEq)]
pub enum JsonExpr {
	/// A JSON Object
	Obj {
		/// The fields of the object.
		#[dict]
		values: BTreeMap<String, JsonExpr>,
	},

	/// A JSON Array
	#[inline_value]
	Arr(Vec<JsonExpr>),

	/// A JSON String
	#[inline_value]
	Str(String),

	/// A JSON Number
	#[inline_value]
	Num(f64),

	/// A JSON Boolean
	#[inline_value]
	Bool(bool),

	/// A JSON Null value
	#[inline_value]
	Null(()),
}

impl JsonExpr {
	/// Converts a `serde_json::Value` into a `JsonExpr`
	pub fn from_json(value: serde_json::Value) -> Option<JsonExpr> {
		Some(match value {
			serde_json::Value::Null => JsonExpr::Null(()),
			serde_json::Value::Bool(b) => JsonExpr::Bool(b),
			serde_json::Value::Number(n) => JsonExpr::Num(n.as_f64()?),
			serde_json::Value::String(s) => JsonExpr::Str(s),
			serde_json::Value::Array(arr) => {
				JsonExpr::Arr(arr.into_iter().map(Self::from_json).collect::<Option<Vec<_>>>()?)
			},
			serde_json::Value::Object(obj) => {
				let values = obj
					.into_iter()
					.map(|(k, v)| Some((k, Self::from_json(v)?)))
					.collect::<Option<BTreeMap<_, _>>>()?;

				JsonExpr::Obj { values }
			},
		})
	}

	/// Converts a `JsonExpr` into a `serde_json::Value`
	pub fn into_json(self) -> Option<serde_json::Value> {
		Some(match self {
			JsonExpr::Obj { values } => {
				let obj = values
					.into_iter()
					.map(|(k, v)| Some((k, v.into_json()?)))
					.collect::<Option<serde_json::Map<_, _>>>()?;

				serde_json::Value::Object(obj)
			},
			JsonExpr::Arr(arr) => {
				serde_json::Value::Array(arr.into_iter().map(Self::into_json).collect::<Option<Vec<_>>>()?)
			},
			JsonExpr::Str(s) => serde_json::Value::String(s),
			JsonExpr::Num(n) => serde_json::Value::Number(serde_json::Number::from_f64(n)?),
			JsonExpr::Bool(b) => serde_json::Value::Bool(b),
			JsonExpr::Null(_) => serde_json::Value::Null,
		})
	}
}

/// Represents an `ESExpr` encoded as JSON with type information
#[derive(serde::Serialize, serde::Deserialize, Debug, PartialEq)]
#[serde(untagged)]
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

	/// An arbitrary-precision integer
	Int {
		/// The integer value
		#[serde(with = "serde_bigint")]
		int: BigInt,
	},

	/// A string value
	Str(String),

	/// Binary data encoded as base64
	Binary {
		/// The base64 encoded data
		base64: Base64Value,
	},

	/// A 32-bit floating point number
	Float32 {
		/// The float32 value
		#[serde(with = "serde_f32")]
		float32: f32,
	},

	/// A 64-bit floating point number
	Float64 {
		/// The float64 value
		#[serde(with = "serde_f64")]
		float64: f64,
	},

	/// A null value
	Null(()),

	/// A null value with a level
	NullLevel {
		/// The level of the null value
		#[serde(with = "serde_biguint")]
		null: BigUint,
	},
}

impl JsonEncodedESExpr {
	/// Converts an `ESExpr` into a `JsonEncodedESExpr`
	pub fn from_esexpr(expr: ESExpr) -> Self {
		match expr {
			ESExpr::Constructor { name, args, kwargs } => JsonEncodedESExpr::Constructor {
				constructor_name: name.into_owned(),
				args: Some(match args {
					Cow::Borrowed(args) => args.iter().cloned().map(Self::from_esexpr).collect(),
					Cow::Owned(args) => args.into_iter().map(Self::from_esexpr).collect(),
				}),
				kwargs: Some(match kwargs {
					Cow::Borrowed(kwargs) => kwargs
						.iter()
						.map(|(k, v)| (k.as_ref().to_owned(), Self::from_esexpr(v.clone())))
						.collect(),
					Cow::Owned(kwargs) => kwargs
						.into_iter()
						.map(|(k, v)| (k.into_owned(), Self::from_esexpr(v)))
						.collect(),
				}),
			},
			ESExpr::Bool(b) => JsonEncodedESExpr::Bool(b),
			ESExpr::Int(i) => JsonEncodedESExpr::Int { int: i.into_owned() },
			ESExpr::Str(s) => JsonEncodedESExpr::Str(s.into_owned()),
			ESExpr::Binary(b) => JsonEncodedESExpr::Binary {
				base64: Base64Value(b.into_owned()),
			},
			ESExpr::Float32(float32) => JsonEncodedESExpr::Float32 { float32 },
			ESExpr::Float64(float64) => JsonEncodedESExpr::Float64 { float64 },
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
			} => ESExpr::Constructor {
				name: Cow::Owned(constructor_name),
				args: Cow::Owned(args.unwrap_or_default().into_iter().map(Self::into_esexpr).collect()),
				kwargs: Cow::Owned(
					kwargs
						.unwrap_or_default()
						.into_iter()
						.map(|(k, v)| (Cow::Owned(k), v.into_esexpr()))
						.collect(),
				),
			},
			JsonEncodedESExpr::List(l) => ESExpr::Constructor {
				name: Cow::Borrowed("list"),
				args: l.into_iter().map(Self::into_esexpr).collect(),
				kwargs: Cow::default(),
			},

			JsonEncodedESExpr::Bool(b) => ESExpr::Bool(b),
			JsonEncodedESExpr::Int { int } => ESExpr::Int(Cow::Owned(int)),
			JsonEncodedESExpr::Str(s) => ESExpr::Str(Cow::Owned(s)),
			JsonEncodedESExpr::Binary { base64 } => ESExpr::Binary(Cow::Owned(base64.0)),
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

impl serde::Serialize for Base64Value {
	fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
		let encoded = BASE64_STANDARD.encode(&self.0);
		serializer.serialize_str(&encoded)
	}
}

struct Base64Visitor;

impl<'de> serde::de::Visitor<'de> for Base64Visitor {
	type Value = Vec<u8>;

	fn expecting(&self, formatter: &mut core::fmt::Formatter) -> core::fmt::Result {
		formatter.write_str("a base64 encoded string")
	}

	fn visit_str<E: serde::de::Error>(self, value: &str) -> Result<Vec<u8>, E> {
		BASE64_STANDARD
			.decode(value)
			.map_err(|_| serde::de::Error::invalid_value(serde::de::Unexpected::Str(value), &self))
	}
}

impl<'de> serde::Deserialize<'de> for Base64Value {
	fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
		let bytes = deserializer.deserialize_str(Base64Visitor)?;
		Ok(Base64Value(bytes))
	}
}

// Helper modules for serialization/deserialization

/// Module for serializing and deserializing f32 values, handling special cases like NaN and infinities
mod serde_f32 {
	#[expect(clippy::trivially_copy_pass_by_ref, reason = "serde requires this to be a reference")]
	pub fn serialize<S: serde::Serializer>(f: &f32, serializer: S) -> Result<S::Ok, S::Error> {
		match *f {
			f if f.is_nan() => serializer.serialize_str("nan"),
			f if f.is_infinite() && f.is_sign_positive() => serializer.serialize_str("+inf"),
			f if f.is_infinite() && f.is_sign_negative() => serializer.serialize_str("-inf"),
			f => serializer.serialize_f32(f),
		}
	}

	pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<f32, D::Error> {
		struct Float32ValueVisitor;

		impl<'de> serde::de::Visitor<'de> for Float32ValueVisitor {
			type Value = f32;

			fn expecting(&self, formatter: &mut core::fmt::Formatter) -> core::fmt::Result {
				formatter.write_str("a number or a string containing nan, +inf, or -inf")
			}

			#[expect(clippy::cast_precision_loss, reason = "The format makes it explicit this is a f32")]
			fn visit_i64<E: serde::de::Error>(self, v: i64) -> Result<f32, E> {
				Ok(v as f32)
			}

			#[expect(clippy::cast_precision_loss, reason = "The format makes it explicit this is a f32")]
			fn visit_u64<E: serde::de::Error>(self, v: u64) -> Result<f32, E> {
				Ok(v as f32)
			}

			#[expect(
				clippy::cast_possible_truncation,
				reason = "The format makes it explicit this is a f64"
			)]
			fn visit_f64<E: serde::de::Error>(self, v: f64) -> Result<f32, E> {
				Ok(v as f32)
			}

			fn visit_str<E: serde::de::Error>(self, value: &str) -> Result<f32, E> {
				match value {
					"nan" => Ok(f32::NAN),
					"+inf" => Ok(f32::INFINITY),
					"-inf" => Ok(f32::NEG_INFINITY),
					_ => Err(serde::de::Error::invalid_value(
						serde::de::Unexpected::Str(value),
						&self,
					)),
				}
			}
		}

		deserializer.deserialize_any(Float32ValueVisitor)
	}
}

/// Module for serializing and deserializing f64 values, handling special cases like NaN and infinities
mod serde_f64 {
	#[expect(clippy::trivially_copy_pass_by_ref, reason = "serde requires this to be a reference")]
	pub fn serialize<S: serde::Serializer>(f: &f64, serializer: S) -> Result<S::Ok, S::Error> {
		match *f {
			f if f.is_nan() => serializer.serialize_str("nan"),
			f if f.is_infinite() && f.is_sign_positive() => serializer.serialize_str("+inf"),
			f if f.is_infinite() && f.is_sign_negative() => serializer.serialize_str("-inf"),
			f => serializer.serialize_f64(f),
		}
	}

	pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<f64, D::Error> {
		struct Float64ValueVisitor;

		impl<'de> serde::de::Visitor<'de> for Float64ValueVisitor {
			type Value = f64;

			fn expecting(&self, formatter: &mut core::fmt::Formatter) -> core::fmt::Result {
				formatter.write_str("a number or a string containing nan, +inf, or -inf")
			}

			#[expect(clippy::cast_precision_loss, reason = "The format makes it explicit this is a f64")]
			fn visit_i64<E: serde::de::Error>(self, v: i64) -> Result<f64, E> {
				Ok(v as f64)
			}

			#[expect(clippy::cast_precision_loss, reason = "The format makes it explicit this is a f64")]
			fn visit_u64<E: serde::de::Error>(self, v: u64) -> Result<f64, E> {
				Ok(v as f64)
			}

			fn visit_f64<E: serde::de::Error>(self, v: f64) -> Result<f64, E> {
				Ok(v)
			}

			fn visit_str<E: serde::de::Error>(self, value: &str) -> Result<f64, E> {
				match value {
					"nan" => Ok(f64::NAN),
					"+inf" => Ok(f64::INFINITY),
					"-inf" => Ok(f64::NEG_INFINITY),
					_ => Err(serde::de::Error::invalid_value(
						serde::de::Unexpected::Str(value),
						&self,
					)),
				}
			}
		}

		deserializer.deserialize_any(Float64ValueVisitor)
	}
}

/// Module for serializing and deserializing arbitrary-precision integers
mod serde_bigint {
	use alloc::string::{String, ToString};

	use num_bigint::BigInt;
	use serde::Deserialize;

	pub fn serialize<S: serde::Serializer>(value: &BigInt, serializer: S) -> Result<S::Ok, S::Error> {
		serializer.serialize_str(&value.to_string())
	}

	pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<BigInt, D::Error> {
		let s = String::deserialize(deserializer)?;
		BigInt::parse_bytes(s.as_bytes(), 10).ok_or_else(|| {
			serde::de::Error::invalid_value(serde::de::Unexpected::Str(&s), &"a string containing a big integer")
		})
	}
}

/// Module for serializing and deserializing arbitrary-precision unsigned integers
mod serde_biguint {
	use alloc::string::{String, ToString};

	use num_bigint::BigUint;
	use serde::Deserialize;

	pub fn serialize<S: serde::Serializer>(value: &BigUint, serializer: S) -> Result<S::Ok, S::Error> {
		serializer.serialize_str(&value.to_string())
	}

	pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<BigUint, D::Error> {
		let s = String::deserialize(deserializer)?;
		BigUint::parse_bytes(s.as_bytes(), 10).ok_or_else(|| {
			serde::de::Error::invalid_value(
				serde::de::Unexpected::Str(&s),
				&"a string containing an unsigned big integer",
			)
		})
	}
}
