use base64::{Engine, prelude::BASE64_STANDARD};
use esexpr::{ESExpr, ESExprCodec};
use num_bigint::{BigInt, BigUint};



use core::f32;
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
        int: JsonBigIntValue,
    },
    Str(String),
    Binary {
        base64: Base64Value,
    },
    Float32 {
        #[serde(deserialize_with="deserialize_f32", serialize_with="serialize_f32")]
        float32: f32,
    },
    Float64 {
        #[serde(deserialize_with="deserialize_f64", serialize_with="serialize_f64")]
        float64: f64,
    },
    Null(()),
    NullLevel {
        null: JsonBigIntValue,
    },

}

impl JsonEncodedESExpr {
    pub fn from_esexpr(expr: ESExpr) -> Self {
        match expr {
            ESExpr::Constructor { name, args, kwargs } =>
                JsonEncodedESExpr::Constructor {
                    constructor_name: name,
                    args: Some(
                        args
                            .into_iter()
                            .map(Self::from_esexpr)
                            .collect()
                    ),
                    kwargs: Some(
                        kwargs
                            .into_iter()
                            .map(|(k, v)| (k, Self::from_esexpr(v)))
                            .collect()
                    ),
                },
            ESExpr::Bool(b) => JsonEncodedESExpr::Bool(b),
            ESExpr::Int(i) => JsonEncodedESExpr::Int { int: JsonBigIntValue(i) },
            ESExpr::Str(s) => JsonEncodedESExpr::Str(s),
            ESExpr::Binary(b) => JsonEncodedESExpr::Binary { base64: Base64Value(b) },
            ESExpr::Float32(float32) => JsonEncodedESExpr::Float32 { float32 },
            ESExpr::Float64(float64) => JsonEncodedESExpr::Float64 { float64 },
            ESExpr::Null(level) if level == BigUint::ZERO => JsonEncodedESExpr::Null(()),
            ESExpr::Null(level) => JsonEncodedESExpr::NullLevel { null: JsonBigIntValue(level.into()) },
        }
    } 

    pub fn into_esexpr(self) -> ESExpr {
        match self {
            JsonEncodedESExpr::Constructor { constructor_name, args, kwargs } =>
                ESExpr::Constructor {
                    name: constructor_name,
                    args: args.unwrap_or_default()
                        .into_iter()
                        .map(Self::into_esexpr)
                        .collect(),
                    kwargs: kwargs.unwrap_or_default()
                        .into_iter()
                        .map(|(k, v)| (k, v.into_esexpr()))
                        .collect()
                },
            JsonEncodedESExpr::List(l) =>
                ESExpr::Constructor {
                    name: "list".to_owned(),
                    args: l.into_iter().map(Self::into_esexpr).collect(),
                    kwargs: Default::default(),
                },

            JsonEncodedESExpr::Bool(b) => ESExpr::Bool(b),
            JsonEncodedESExpr::Int { int } => ESExpr::Int(int.0),
            JsonEncodedESExpr::Str(s) => ESExpr::Str(s),
            JsonEncodedESExpr::Binary { base64 } => ESExpr::Binary(base64.0),
            JsonEncodedESExpr::Float32 { float32 } => ESExpr::Float32(float32),
            JsonEncodedESExpr::Float64 { float64 } => ESExpr::Float64(float64),
            JsonEncodedESExpr::Null(_) => ESExpr::Null(BigUint::ZERO),
            JsonEncodedESExpr::NullLevel { null } => ESExpr::Null(null.0.to_biguint().unwrap())
        }
    } 
}

#[derive(Debug, PartialEq)]
pub struct JsonBigIntValue(BigInt);


impl serde::Serialize for JsonBigIntValue {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.serialize_str(&self.0.to_string())
    }
}

struct BigIntVisitor;

impl<'de> serde::de::Visitor<'de> for BigIntVisitor {
    type Value = BigInt;

    fn expecting(&self, formatter: &mut std::fmt::Formatter) -> std::fmt::Result {
        formatter.write_str("a string containing a big integer")
    }

    fn visit_str<E: serde::de::Error>(self, value: &str) -> Result<BigInt, E> {
        BigInt::parse_bytes(value.as_bytes(), 10)
            .ok_or_else(|| serde::de::Error::invalid_value(serde::de::Unexpected::Str(value), &self))
    }
}

impl<'de> serde::Deserialize<'de> for JsonBigIntValue {
    fn deserialize<D: serde::Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let int = deserializer.deserialize_str(BigIntVisitor)?;
        Ok(JsonBigIntValue(int))
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



fn serialize_f32<S: serde::Serializer>(f: &f32, serializer: S) -> Result<S::Ok, S::Error> {
    match *f {
        f if f.is_nan() => serializer.serialize_str("nan"),
        f if f.is_infinite() && f.is_sign_positive() => serializer.serialize_str("+inf"),
        f if f.is_infinite() && f.is_sign_negative() => serializer.serialize_str("-inf"),
        f => serializer.serialize_f32(f)
    }
}

fn deserialize_f32<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<f32, D::Error> {
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


fn serialize_f64<S: serde::Serializer>(f: &f64, serializer: S) -> Result<S::Ok, S::Error> {
    match *f {
        f if f.is_nan() => serializer.serialize_str("nan"),
        f if f.is_infinite() && f.is_sign_positive() => serializer.serialize_str("+inf"),
        f if f.is_infinite() && f.is_sign_negative() => serializer.serialize_str("-inf"),
        f => serializer.serialize_f64(f)
    }
}

fn deserialize_f64<'de, D: serde::Deserializer<'de>>(deserializer: D) -> Result<f64, D::Error> {
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




