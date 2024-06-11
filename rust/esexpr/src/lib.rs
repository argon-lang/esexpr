//! esexpr is a library that implements the ESExpr format.


use std::collections::{HashMap, HashSet};
use num_bigint::{BigInt, BigUint};

pub use esexpr_derive::ESExprCodec;

#[cfg(feature = "text-format")]
pub mod text_format;

#[cfg(feature = "binary-format")]
pub mod binary_format;

/// Representation of an ESExpr value.
/// Must be one of a constructor, bool, int, string, binary, float32, float64, or null.
#[derive(Debug, Clone, PartialEq)]
pub enum ESExpr {
    /// A constructor expression.
    /// Can contain positional and keyword arguments.
    Constructor {
        /// The constructor name.
        name: String,

        /// Positional argument values.
        args: Vec<ESExpr>,

        /// Keyword argument values.
        kwargs: HashMap<String, ESExpr>,
    },

    /// A bool value.
    Bool(bool),

    /// An integer value.
    Int(BigInt),

    /// A string value.
    Str(String),

    /// A binary value.
    Binary(Vec<u8>),

    /// A float32 value.
    Float32(f32),

    /// A float64 value.
    Float64(f64),

    /// A Null value.
    Null,
}

impl ESExpr {
    /// Get the tag of an expression.
    pub fn tag(&self) -> ESExprTag {
        match self {
            ESExpr::Constructor { name, .. } => ESExprTag::Constructor(name.clone()),
            ESExpr::Bool(_) => ESExprTag::Bool,
            ESExpr::Int(_) => ESExprTag::Int,
            ESExpr::Str(_) => ESExprTag::Str,
            ESExpr::Binary(_) => ESExprTag::Binary,
            ESExpr::Float32(_) => ESExprTag::Float32,
            ESExpr::Float64(_) => ESExprTag::Float64,
            ESExpr::Null => ESExprTag::Null,
        }
    } 
}

/// An expression tag.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum ESExprTag {
    /// A tag for a constructor with a specified name.
    Constructor(String),

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

impl ESExprTag {
    /// Checks whether the tag is for a constructor value.
    pub fn is_constructor(&self, s: &str) -> bool {
        match self {
            ESExprTag::Constructor(name) => name == s,
            _ => false,
        }
    }
}

/// A codec that encodes and decodes ESExpr values.
pub trait ESExprCodec where Self : Sized {
    /// The tags that this type is expected to be encoded as.
    fn tags() -> HashSet<ESExprTag>;

    /// Encode this value into an expression.
    fn encode_esexpr(self) -> ESExpr;

    /// Decode an expression into a value.
    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError>;
}

impl ESExprCodec for ESExpr {
    fn tags() -> HashSet<ESExprTag> {
        HashSet::new()
    }

    fn encode_esexpr(self) -> ESExpr {
        self
    }

    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
        Ok(expr)
    }
}

impl ESExprCodec for bool {
    fn tags() -> HashSet<ESExprTag> {
        HashSet::from([ESExprTag::Bool])
    }

    fn encode_esexpr(self) -> ESExpr {
        ESExpr::Bool(self)
    }

    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
        match expr {
            ESExpr::Bool(b) => Ok(b),
            _  => Err(DecodeError(DecodeErrorType::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Bool]), actual_tag: expr.tag() }, DecodeErrorPath::Current))
        }
    }
}

macro_rules! int_codec {
    ($T: ty) => {
        impl ESExprCodec for $T {
            fn tags() -> HashSet<ESExprTag> {
                HashSet::from([ESExprTag::Int])
            }
        
            fn encode_esexpr(self) -> ESExpr {
                ESExpr::Int(BigInt::from(self))
            }

            fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
                match expr {
                    ESExpr::Int(i) =>
                        match <$T>::try_from(i) {
                            Ok(i) => Ok(i),
                            Err(_) => Err(DecodeError(DecodeErrorType::OutOfRange(format!("Unexpected integer value for {}", stringify!($T))), DecodeErrorPath::Current)),
                        },
                    _  => Err(DecodeError(DecodeErrorType::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Int]), actual_tag: expr.tag() }, DecodeErrorPath::Current))
                }
            }
        }
    };
}

int_codec!(BigInt);
int_codec!(BigUint);
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

impl ESExprCodec for String {
    fn tags() -> HashSet<ESExprTag> {
        HashSet::from([ESExprTag::Str])
    }

    fn encode_esexpr(self) -> ESExpr {
        ESExpr::Str(self)
    }

    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
        match expr {
            ESExpr::Str(s) => Ok(s),
            _  => Err(DecodeError(DecodeErrorType::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Str]), actual_tag: expr.tag() }, DecodeErrorPath::Current))
        }       
    }
}

impl ESExprCodec for f32 {
    fn tags() -> HashSet<ESExprTag> {
        HashSet::from([ESExprTag::Float32])
    }

    fn encode_esexpr(self) -> ESExpr {
        ESExpr::Float32(self)
    }

    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
        match expr {
            ESExpr::Float32(f) => Ok(f),
            _  => Err(DecodeError(DecodeErrorType::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Float32]), actual_tag: expr.tag() }, DecodeErrorPath::Current))
        }
    }
}

impl ESExprCodec for f64 {
    fn tags() -> HashSet<ESExprTag> {
        HashSet::from([ESExprTag::Float64])
    }

    fn encode_esexpr(self) -> ESExpr {
        ESExpr::Float64(self)
    }

    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
        match expr {
            ESExpr::Float64(f) => Ok(f),
            _  => Err(DecodeError(DecodeErrorType::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Float64]), actual_tag: expr.tag() }, DecodeErrorPath::Current))
        }
    }
}

impl <A: ESExprCodec> ESExprCodec for Vec<A> {
    fn tags() -> HashSet<ESExprTag> {
        HashSet::from([ESExprTag::Constructor("list".to_owned())])
    }

    fn encode_esexpr(self) -> ESExpr {
        ESExpr::Constructor {
            name: "list".to_owned(),
            args: self.into_iter().map(A::encode_esexpr).collect(),
            kwargs: HashMap::new(),
        }
    }

    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
        match expr {
            ESExpr::Constructor { name, args, kwargs } if name == "list" => {
                if !kwargs.is_empty() {
                    return Err(DecodeError(DecodeErrorType::OutOfRange("List must not have keyword arguments".to_owned()), DecodeErrorPath::Constructor(name)));
                }

                Ok(args.into_iter().map(A::decode_esexpr).collect::<Result<_, _>>()?)
            },
            _  => Err(DecodeError(DecodeErrorType::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Constructor("list".to_owned())]), actual_tag: expr.tag() }, DecodeErrorPath::Current)),
        }
    }
}

impl <A: ESExprCodec> ESExprCodec for Option<A> {
    fn tags() -> HashSet<ESExprTag> {
        let mut tags = A::tags();
        tags.insert(ESExprTag::Null);
        tags
    }

    fn encode_esexpr(self) -> ESExpr {
        match self {
            Some(a) => a.encode_esexpr(),
            None => ESExpr::Null,
        }
    }

    fn decode_esexpr(expr: ESExpr) -> Result<Self, DecodeError> {
        match expr {
            ESExpr::Null => Ok(None),
            _ => A::decode_esexpr(expr).map(Some),
        }
    }
}

/// A field codec for optional fields.
pub trait ESExprOptionalFieldCodec where Self : Sized {
    /// Encode an optional field or None when the value should be excluded.
    fn encode_optional_field(self) -> Option<ESExpr>;

    /// Decode an optional field value.
    fn decode_optional_field(value: Option<ESExpr>) -> Result<Self, DecodeError>;
}

impl <A: ESExprCodec> ESExprOptionalFieldCodec for Option<A> {
    fn encode_optional_field(self) -> Option<ESExpr> {
        self.map(A::encode_esexpr)
    }

    fn decode_optional_field(value: Option<ESExpr>) -> Result<Self, DecodeError> {
        value.map(A::decode_esexpr).transpose()
    }
}

/// A field codec for variable arguments.
pub trait ESExprVarArgCodec where Self : Sized {
    /// Encode variable arguments
    fn encode_vararg_element(self, args: &mut Vec<ESExpr>);

    /// Decode variable arguments.
    fn decode_vararg_element(args: &mut Vec<ESExpr>, constructor_name: &str, start_index: usize) -> Result<Self, DecodeError>;
}

impl <A: ESExprCodec> ESExprVarArgCodec for Vec<A> {
    fn encode_vararg_element(self, args: &mut Vec<ESExpr>) {
        for arg in self {
            args.push(arg.encode_esexpr());
        }
    }

    fn decode_vararg_element(args: &mut Vec<ESExpr>, constructor_name: &str, start_index: usize) -> Result<Self, DecodeError> {
        args.drain(..).enumerate().map(|(i, a)| {
            A::decode_esexpr(a).map_err(|mut e| {
                e.1 = DecodeErrorPath::Positional(constructor_name.to_owned(), start_index + i, Box::new(e.1));
                e
            })
        }).collect()
    }
}

/// A field codec for dictionary arguments.
pub trait ESExprDictCodec where Self : Sized {
    /// Encode dictionary arguments.
    fn encode_dict_element(self, kwargs: &mut HashMap<String, ESExpr>);

    /// Decode dictionary arguments.
    fn decode_dict_element(kwargs: &mut HashMap<String, ESExpr>, constructor_name: &str) -> Result<Self, DecodeError>;
}

impl <A: ESExprCodec> ESExprDictCodec for HashMap<String, A> {
    fn encode_dict_element(self, kwargs: &mut HashMap<String, ESExpr>) {
        for (k, v) in self {
            kwargs.insert(k.clone(), v.encode_esexpr());
        }
    }

    fn decode_dict_element(kwargs: &mut HashMap<String, ESExpr>, constructor_name: &str) -> Result<Self, DecodeError> {
        kwargs.drain()
            .map(|(k, v)| {
                let value = A::decode_esexpr(v)
                    .map_err(|mut e| {
                        e.1 = DecodeErrorPath::Keyword(constructor_name.to_owned(), k.clone(), Box::new(e.1));
                        e
                    })?;

                Ok((k, value))
            }).collect()
    }
}

/// An error that occurs when decoding expressions.
#[derive(Debug, Clone, PartialEq)]
pub struct DecodeError(pub DecodeErrorType, pub DecodeErrorPath);

/// The type of error that occurred while decoding.
#[derive(Debug, Clone, PartialEq)]
pub enum DecodeErrorType {
    /// An expression had a different tag than expected.
    UnexpectedExpr {
        /// The tags that were expected.
        expected_tags: HashSet<ESExprTag>,

        /// The actual tag of the expression.
        actual_tag: ESExprTag,
    },

    /// Indicates that a value was not valid for the decoded type.
    OutOfRange(String),

    /// Indicates that a keyword argument was missing.
    MissingKeyword(String),

    /// Indicates that a positional argument was missing.
    MissingPositional,
}


/// Specifies where in an expression an error occurred.
#[derive(Debug, Clone, PartialEq)]
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


