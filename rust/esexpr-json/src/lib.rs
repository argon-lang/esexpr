use base64::{Engine, prelude::BASE64_STANDARD};
use esexpr::{ESExpr, ESExprCodec};
use num_bigint::{BigInt, BigUint};



use core::f32;
use std::borrow::Cow;
use std::collections::HashMap;

#[derive(ESExprCodec, Debug, PartialEq)]
pub enum JsonExpr {
    Obj {
        #[dict]
        values: HashMap<String, JsonExpr>,
    },

    #[inline_value]
    Arr(Vec<JsonExpr>),

    #[inline_value]
    Str(String),

    #[inline_value]
    Num(f64),

    #[inline_value]
    Bool(bool),

    #[inline_value]
    Null(()),
}

impl JsonExpr {
    pub fn from_json(value: serde_json::Value) -> JsonExpr {
        match value {
            serde_json::Value::Null => JsonExpr::Null(()),
            serde_json::Value::Bool(b) => JsonExpr::Bool(b),
            serde_json::Value::Number(n) => JsonExpr::Num(n.as_f64().unwrap()),
            serde_json::Value::String(s) => JsonExpr::Str(s),
            serde_json::Value::Array(arr) => JsonExpr::Arr(arr.into_iter().map(Self::from_json).collect()),
            serde_json::Value::Object(obj) => {
                let values: HashMap<_, _> = obj.into_iter()
                    .map(|(k, v)| (k, Self::from_json(v)))
                    .collect();

                JsonExpr::Obj { values }
            },
        }
    }

    pub fn into_json(self) -> serde_json::Value {
        match self {
            JsonExpr::Obj { values } => {
                let obj: serde_json::Map<_, _> = values.into_iter()
                    .map(|(k, v)| (k, v.into_json()))
                    .collect();

                serde_json::Value::Object(obj)
            },
            JsonExpr::Arr(arr) => serde_json::Value::Array(arr.into_iter().map(Self::into_json).collect()),
            JsonExpr::Str(s) => serde_json::Value::String(s),
            JsonExpr::Num(n) => serde_json::Value::Number(serde_json::Number::from_f64(n).unwrap()),
            JsonExpr::Bool(b) => serde_json::Value::Bool(b),
            JsonExpr::Null(_) => serde_json::Value::Null,
        }
    }
}


#[derive(serde::Serialize, serde::Deserialize, Debug, PartialEq)]
#[serde(untagged)]
pub enum JsonEncodedESExpr {
    Constructor {
        constructor_name: String,
        args: Option<Vec<JsonEncodedESExpr>>,
        kwargs: Option<HashMap<String, JsonEncodedESExpr>>,
    },
    List(Vec<JsonEncodedESExpr>),

    Bool(bool),
    Int {
        #[serde(with="serde_bigint")]
        int: BigInt,
    },
    Str(String),
    Binary {
        base64: Base64Value,
    },
    Float32 {
        #[serde(with="serde_f32")]
        float32: f32,
    },
    Float64 {
        #[serde(with="serde_f64")]
        float64: f64,
    },
    Null(()),
    NullLevel {
        #[serde(with="serde_biguint")]
        null: BigUint,
    },
}

impl JsonEncodedESExpr {
    pub fn from_esexpr(expr: ESExpr) -> Self {
        match expr {
            ESExpr::Constructor { name, args, kwargs } =>
                JsonEncodedESExpr::Constructor {
                    constructor_name: name.into_owned(),
                    args: Some(
                        match args {
                            Cow::Borrowed(args) =>
                                args
                                    .iter()
                                    .cloned()
                                    .map(Self::from_esexpr)
                                    .collect(),
                            Cow::Owned(args) =>
                                args
                                    .into_iter()
                                    .map(Self::from_esexpr)
                                    .collect(),
                        }
                    ),
                    kwargs: Some(
                        match kwargs {
                            Cow::Borrowed(kwargs) =>
                                kwargs
                                    .iter()
                                    .map(|(k, v)| (k.as_ref().to_owned(), Self::from_esexpr(v.clone())))
                                    .collect(),
                            Cow::Owned(kwargs) =>
                                kwargs
                                    .into_iter()
                                    .map(|(k, v)| (k.into_owned(), Self::from_esexpr(v)))
                                    .collect()
                        }
                    ),
                },
            ESExpr::Bool(b) => JsonEncodedESExpr::Bool(b),
            ESExpr::Int(i) => JsonEncodedESExpr::Int { int: i.into_owned() },
            ESExpr::Str(s) => JsonEncodedESExpr::Str(s.into_owned()),
            ESExpr::Binary(b) => JsonEncodedESExpr::Binary { base64: Base64Value(b.into_owned()) },
            ESExpr::Float32(float32) => JsonEncodedESExpr::Float32 { float32 },
            ESExpr::Float64(float64) => JsonEncodedESExpr::Float64 { float64 },
            ESExpr::Null(level) if *level == BigUint::ZERO => JsonEncodedESExpr::Null(()),
            ESExpr::Null(level) => JsonEncodedESExpr::NullLevel { null: level.into_owned() },
        }
    } 

    pub fn into_esexpr(self) -> ESExpr<'static> {
        match self {
            JsonEncodedESExpr::Constructor { constructor_name, args, kwargs } =>
                ESExpr::Constructor {
                    name: Cow::Owned(constructor_name),
                    args: Cow::Owned(
                        args.unwrap_or_default()
                            .into_iter()
                            .map(Self::into_esexpr)
                            .collect()
                    ),
                    kwargs: Cow::Owned(
                        kwargs.unwrap_or_default()
                            .into_iter()
                            .map(|(k, v)| (Cow::Owned(k), v.into_esexpr()))
                            .collect()
                    ),
                },
            JsonEncodedESExpr::List(l) =>
                ESExpr::Constructor {
                    name: Cow::Borrowed("list"),
                    args: l.into_iter().map(Self::into_esexpr).collect(),
                    kwargs: Default::default(),
                },

            JsonEncodedESExpr::Bool(b) => ESExpr::Bool(b),
            JsonEncodedESExpr::Int { int } => ESExpr::Int(Cow::Owned(int)),
            JsonEncodedESExpr::Str(s) => ESExpr::Str(Cow::Owned(s)),
            JsonEncodedESExpr::Binary { base64 } => ESExpr::Binary(Cow::Owned(base64.0)),
            JsonEncodedESExpr::Float32 { float32 } => ESExpr::Float32(float32),
            JsonEncodedESExpr::Float64 { float64 } => ESExpr::Float64(float64),
            JsonEncodedESExpr::Null(_) => ESExpr::Null(Cow::Owned(BigUint::ZERO)),
            JsonEncodedESExpr::NullLevel { null } => ESExpr::Null(Cow::Owned(null))
        }
    } 
}

#[derive(Debug, PartialEq)]
pub struct Base64Value(Vec<u8>);

impl serde::Serialize for Base64Value {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error>
    {
        let encoded = BASE64_STANDARD.encode(&self.0);
        serializer.serialize_str(&encoded)
    }
}

struct Base64Visitor;

impl<'de> serde::de::Visitor<'de> for Base64Visitor {
    type Value = Vec<u8>;

    fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
        formatter.write_str("a base64 encoded string")
    }

    fn visit_str<E: serde::de::Error>(self, value: &str) -> Result<Vec<u8>, E> {
        BASE64_STANDARD.decode(value).map_err(|_| serde::de::Error::invalid_value(serde::de::Unexpected::Str(value), &self))
    }
}

impl<'de> serde::Deserialize<'de> for Base64Value {
    fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let bytes = deserializer.deserialize_str(Base64Visitor)?;
        Ok(Base64Value(bytes))
    }
}


mod serde_f32 {
    pub fn serialize<S: serde::Serializer>(f: &f32, serializer: S) -> Result<S::Ok, S::Error> {
        match *f {
            f if f.is_nan() => serializer.serialize_str("nan"),
            f if f.is_infinite() && f.is_sign_positive() => serializer.serialize_str("+inf"),
            f if f.is_infinite() && f.is_sign_negative() => serializer.serialize_str("-inf"),
            f => serializer.serialize_f32(f)
        }
    }

    pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<f32, D::Error> {
        struct Float32ValueVisitor;

        impl<'de> serde::de::Visitor<'de> for Float32ValueVisitor {
            type Value = f32;

            fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
                formatter.write_str("a number or a string containing nan, +inf, or -inf")
            }

            fn visit_i64<E: serde::de::Error>(self, v: i64) -> Result<f32, E> {
                Ok(v as f32)
            }

            fn visit_u64<E: serde::de::Error>(self, v: u64) -> Result<f32, E> {
                Ok(v as f32)
            }

            fn visit_f64<E: serde::de::Error>(self, v: f64) -> Result<f32, E> {
                Ok(v as f32)
            }

            fn visit_str<E: serde::de::Error>(self, value: &str) -> Result<f32, E> {
                match value {
                    "nan" => Ok(f32::NAN),
                    "+inf" => Ok(f32::INFINITY),
                    "-inf" => Ok(f32::NEG_INFINITY),
                    _ => Err(serde::de::Error::invalid_value(serde::de::Unexpected::Str(value), &self)),
                }
            }
        }

        deserializer.deserialize_any(Float32ValueVisitor)
    }

}


mod serde_f64 {
    pub fn serialize<S: serde::Serializer>(f: &f64, serializer: S) -> Result<S::Ok, S::Error> {
        match *f {
            f if f.is_nan() => serializer.serialize_str("nan"),
            f if f.is_infinite() && f.is_sign_positive() => serializer.serialize_str("+inf"),
            f if f.is_infinite() && f.is_sign_negative() => serializer.serialize_str("-inf"),
            f => serializer.serialize_f64(f)
        }
    }

    pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<f64, D::Error> {
        struct Float64ValueVisitor;

        impl<'de> serde::de::Visitor<'de> for Float64ValueVisitor {
            type Value = f64;

            fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
                formatter.write_str("a number or a string containing nan, +inf, or -inf")
            }

            fn visit_i64<E: serde::de::Error>(self, v: i64) -> Result<f64, E> {
                Ok(v as f64)
            }

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
                    _ => Err(serde::de::Error::invalid_value(serde::de::Unexpected::Str(value), &self)),
                }
            }
        }

        deserializer.deserialize_any(Float64ValueVisitor)
    }
}

mod serde_bigint {
    use serde::Deserialize;
    use num_bigint::BigInt;

    pub fn serialize<S: serde::Serializer>(value: &BigInt, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&value.to_string())
    }

    pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<BigInt, D::Error> {
        let s = String::deserialize(deserializer)?;
        BigInt::parse_bytes(s.as_bytes(), 10)
            .ok_or_else(|| serde::de::Error::invalid_value(serde::de::Unexpected::Str(&s), &"a string containing a big integer"))
    }
}

mod serde_biguint {
    use serde::Deserialize;
    use num_bigint::BigUint;

    pub fn serialize<S: serde::Serializer>(value: &BigUint, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&value.to_string())
    }

    pub fn deserialize<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<BigUint, D::Error> {
        let s = String::deserialize(deserializer)?;
        BigUint::parse_bytes(s.as_bytes(), 10)
            .ok_or_else(|| serde::de::Error::invalid_value(serde::de::Unexpected::Str(&s), &"a string containing an unsigned big integer"))
    }
}

