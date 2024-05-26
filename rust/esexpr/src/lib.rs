use std::collections::{HashMap, HashSet};
use num_bigint::{BigInt, BigUint};

pub use esexpr_derive::ESExprCodec;

#[cfg(feature = "text-format")]
pub mod text_format;

#[cfg(feature = "binary-format")]
pub mod binary_format;


#[derive(Debug, Clone, PartialEq)]
pub enum ESExpr {
    Constructor {
        name: String,
        args: Vec<ESExpr>,
        kwargs: HashMap<String, ESExpr>,
    },

    Bool(bool),
    Int(BigInt),
    Str(String),
    Binary(Vec<u8>),
    Float32(f32),
    Float64(f64),
    Null,
}

impl ESExpr {
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

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum ESExprTag {
    Constructor(String),
    Bool,
    Int,
    Str,
    Binary,
    Float32,
    Float64,
    Null,
}

impl ESExprTag {
    pub fn is_constructor(&self, s: &str) -> bool {
        match self {
            ESExprTag::Constructor(name) => name == s,
            _ => false,
        }
    }
}


pub trait ESExprCodec where Self : Sized {
    fn tags() -> HashSet<ESExprTag>;
    fn encode_esexpr(self) -> ESExpr;
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
            _  => Err(DecodeError::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Bool]), actual_tag: expr.tag() })
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
                            Err(_) => Err(DecodeError::OutOfRange(format!("Unexpected integer value for {}", stringify!($T)))),
                        },
                    _  => Err(DecodeError::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Int]), actual_tag: expr.tag() })
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
            _  => Err(DecodeError::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Str]), actual_tag: expr.tag() })
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
            _  => Err(DecodeError::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Float32]), actual_tag: expr.tag() })
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
            _  => Err(DecodeError::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Float64]), actual_tag: expr.tag() })
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
                    return Err(DecodeError::OutOfRange("List must not have keyword arguments".to_owned()));
                }

                Ok(args.into_iter().map(A::decode_esexpr).collect::<Result<_, _>>()?)
            },
            _  => Err(DecodeError::UnexpectedExpr { expected_tags: HashSet::from([ESExprTag::Constructor("list".to_owned())]), actual_tag: expr.tag() })
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

pub trait ESExprOptionalFieldCodec where Self : Sized {
    fn encode_optional_field(self) -> Option<ESExpr>;
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

pub trait ESExprVarArgCodec where Self : Sized {
    fn encode_vararg_element(self, args: &mut Vec<ESExpr>);
    fn decode_vararg_element(args: &mut Vec<ESExpr>) -> Result<Self, DecodeError>;
}

impl <A: ESExprCodec> ESExprVarArgCodec for Vec<A> {
    fn encode_vararg_element(self, args: &mut Vec<ESExpr>) {
        for arg in self {
            args.push(arg.encode_esexpr());
        }
    }

    fn decode_vararg_element(args: &mut Vec<ESExpr>) -> Result<Self, DecodeError> {
        args.drain(..).map(A::decode_esexpr).collect()
    }
}

pub trait ESExprDictCodec where Self : Sized {
    fn encode_dict_element(self, kwargs: &mut HashMap<String, ESExpr>);
    fn decode_dict_element(kwargs: &mut HashMap<String, ESExpr>) -> Result<Self, DecodeError>;
}

impl <A: ESExprCodec> ESExprDictCodec for HashMap<String, A> {
    fn encode_dict_element(self, kwargs: &mut HashMap<String, ESExpr>) {
        for (k, v) in self {
            kwargs.insert(k.clone(), v.encode_esexpr());
        }
    }

    fn decode_dict_element(kwargs: &mut HashMap<String, ESExpr>) -> Result<Self, DecodeError> {
        kwargs.drain().map(|(k, v)| Ok((k, A::decode_esexpr(v)?))).collect()
    }
}

#[derive(Debug, Clone, PartialEq)]
pub enum DecodeError {
    UnexpectedExpr {
        expected_tags: HashSet<ESExprTag>,
        actual_tag: ESExprTag,
    },
    OutOfRange(String),
    MissingKeyword(String),
    MissingPositional,
}



