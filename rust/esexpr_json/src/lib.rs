use base64::{Engine, prelude::BASE64_STANDARD};
use esexpr::{ESExpr, ESExprCodec};
use num_bigint::BigInt;



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
        constructor: String,
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
        float32: f32,
    },
    Float64 {
        float64: f64,
    },
    Null(()),

}

impl JsonEncodedESExpr {
    pub fn from_esexpr(expr: ESExpr) -> Self {
        match expr {
            ESExpr::Constructor { name, args, kwargs } =>
                JsonEncodedESExpr::Constructor {
                    constructor: name,
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
            ESExpr::Null => JsonEncodedESExpr::Null(()),
        }
    } 

    pub fn into_esexpr(self) -> ESExpr {
        match self {
            JsonEncodedESExpr::Constructor { constructor, args, kwargs } =>
                ESExpr::Constructor {
                    name: constructor,
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
            JsonEncodedESExpr::Null(_) => ESExpr::Null,
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
