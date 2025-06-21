
//! Representations of `ESExpr` as JSON and vice versa.

use core::f32;
use std::borrow::Cow;
use std::collections::HashMap;

use base64::Engine;
use base64::prelude::BASE64_STANDARD;
use esexpr::{ESExpr, ESExprCodec};
use num_bigint::{BigInt, BigUint};

/// An enum representing the mapping between JSON values and ESExpr types.
/// Provides a way to convert between JSON and ESExpr representations.
#[derive(ESExprCodec, Debug, PartialEq)]
pub enum JsonExpr {
    /// Represents a JSON object containing key-value pairs
    Obj {
        /// Map of field names to their values
        #[dict]
        values: HashMap<String, JsonExpr>,
    },

    /// Represents a JSON array of values
    #[inline_value]
    Arr(Vec<JsonExpr>),

    /// Represents a JSON string value 
    #[inline_value]
    Str(String),

    /// Represents a JSON numeric value as f64
    #[inline_value]
    Num(f64),

    /// Represents a JSON boolean value
    #[inline_value]
    Bool(bool),

    /// Represents a JSON null value
    #[inline_value]
    Null(()),
}

impl JsonExpr {
    /// Converts a serde_json::Value into a JsonExpr
    pub fn from_json(value: serde_json::Value) -> JsonExpr {
        // ... implementation remains the same
    }

    /// Converts a JsonExpr back into a serde_json::Value
    pub fn into_json(self) -> serde_json::Value {
        // ... implementation remains the same
    }
}

/// Represents an ESExpr encoded as JSON with type information
#[derive(serde::Serialize, serde::Deserialize, Debug, PartialEq)]
#[serde(untagged)]
pub enum JsonEncodedESExpr {
    /// A constructor with a name and optional arguments
    Constructor {
        constructor_name: String,
        args: Option<Vec<JsonEncodedESExpr>>,
        kwargs: Option<HashMap<String, JsonEncodedESExpr>>,
    },
    /// A list of expressions
    List(Vec<JsonEncodedESExpr>),

    /// A boolean value
    Bool(bool),
    /// An arbitrary-precision integer
    Int {
        #[serde(with = "serde_bigint")]
        int: BigInt,
    },
    /// A string value
    Str(String),
    /// Binary data encoded as base64
    Binary {
        base64: Base64Value,
    },
    /// A 32-bit floating point number
    Float32 {
        #[serde(with = "serde_f32")]
        float32: f32,
    },
    /// A 64-bit floating point number
    Float64 {
        #[serde(with = "serde_f64")]
        float64: f64,
    },
    /// A null value
    Null(()),
    /// A null value with a level
    NullLevel {
        #[serde(with = "serde_biguint")]
        null: BigUint,
    },
}

/// Wrapper type for base64-encoded binary data
#[derive(Debug, PartialEq)]
pub struct Base64Value(Vec<u8>);

// Helper modules for serialization/deserialization

/// Module for serializing and deserializing f32 values, handling special cases like NaN and infinities
mod serde_f32 {
    // ... implementation remains the same
}

/// Module for serializing and deserializing f64 values, handling special cases like NaN and infinities
mod serde_f64 {
    // ... implementation remains the same
}

/// Module for serializing and deserializing arbitrary-precision integers
mod serde_bigint {
    // ... implementation remains the same
}

/// Module for serializing and deserializing arbitrary-precision unsigned integers
mod serde_biguint {
    // ... implementation remains the same
}