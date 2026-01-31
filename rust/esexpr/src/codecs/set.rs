
macro_rules! set_encoded_eq_impl {
    ($t: ty, $($bounds: tt)*) => {
        impl<$($bounds)*> ESExprEncodedEq for $t {
            fn is_encoded_eq(&self, other: &Self) -> bool {
                self.len() == other.len() &&
                    self.iter()
                        .zip(other.iter())
                        .all(|(v1, v2)|
                             v1.is_encoded_eq(v2)
                        )
            }
        }
    };
}


macro_rules! set_codec_impl {
    ($t: ty, $($bounds: tt)*) => {
        impl<$($bounds)*> ESExprCodec<'a> for $t {
            const TAGS: ESExprTagSet = ESExprTagSet::Tags(&[ESExprTag::Constructor(CowStr::Static("set"))]);
        
            fn encode_esexpr(&'a self) -> ESExpr<'a> {
                ESExpr::constructor(
                    "set",
                    self.iter()
                        .map(|v| v.encode_esexpr())
                        .collect::<Vec<_>>(),
                    [],
                )
            }
        
            fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
                match expr {
                    ESExpr::Constructor(crate::ESExprConstructor { name, args, kwargs }) if name == "set" => {
                        if !kwargs.is_empty() {
                            return Err(DecodeError::new(
                                DecodeErrorType::OutOfRange(alloc::borrow::ToOwned::to_owned("Set must not have keyword arguments")),
                                DecodeErrorPath::Constructor(alloc::borrow::ToOwned::to_owned(name.deref())),
                            ));
                        }
        
                        let mut set = Self::default();
        
                        for v_expr in args {
                            let v = V::decode_esexpr(v_expr)?;
                            set.insert(v);
                        }
        
                        Ok(set)
                    },
                    _ => Err(DecodeError::new(
                        DecodeErrorType::UnexpectedExpr {
                            expected_tags: <Self as ESExprCodec>::TAGS,
                            actual_tag: expr.tag().into_owned(),
                        },
                        DecodeErrorPath::Current,
                    )),
                }
            }
        }
    }
}
