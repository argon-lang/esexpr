
macro_rules! map_encoded_eq_impl {
    ($t: ty, $($bounds: tt)*) => {
        impl<$($bounds)*> ESExprEncodedEq for $t {
            fn is_encoded_eq(&self, other: &Self) -> bool {
                self.len() == other.len() &&
                    self.iter()
                        .zip(other.iter())
                        .all(|((k1, v1), (k2, v2))|
                             k1 == k2 && v1.is_encoded_eq(v2)
                        )
            }
        }
    };
}


macro_rules! map_codec_impl {
    ($t: ty, $($bounds: tt)*) => {
        impl<$($bounds)*> ESExprCodec<'a> for $t {
            const TAGS: ESExprTagSet = ESExprTagSet::Tags(&[ESExprTag::Constructor(CowStr::Static("map"))]);
        
            fn encode_esexpr(&'a self) -> ESExpr<'a> {
                ESExpr::constructor(
                    "map",
                    self.iter()
                        .flat_map(|(k, v)| [k.encode_esexpr(), v.encode_esexpr()])
                        .collect::<Vec<_>>(),
                    [],
                )
            }
        
            fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
                match expr {
                    ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) if name == "map" => {
                        if !kwargs.is_empty() {
                            return Err(DecodeError::new(
                                DecodeErrorType::OutOfRange("Map must not have keyword arguments".to_owned()),
                                DecodeErrorPath::Constructor(name.deref().to_owned()),
                            ));
                        }
        
                        if args.len() % 2 != 0 {
                            return Err(DecodeError::new(
                                DecodeErrorType::OutOfRange("Map must not have an odd number of arguments".to_owned()),
                                DecodeErrorPath::Constructor(name.deref().to_owned()),
                            ));
                        }
        
                        let mut map = Self::default();
        
                        for (k_expr, v_expr) in args.into_iter().tuples::<(_, _)>() {
                            let k = K::decode_esexpr(k_expr.clone())?;
                            let v = V::decode_esexpr(v_expr.clone())?;
                            map.insert(k, v);
                        }
        
                        Ok(map)
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

macro_rules! map_dict_codec_impl {
    ($t: ty, $key: ident, $encode_key: expr, $decode_key: expr, $($bounds: tt)*) => {
        impl<$($bounds)*> ESExprDictCodec<'a> for $t {
            type Element = V;

            fn encode_dict_element(&'a self, kwargs: &mut hashbrown::HashMap<CowStr<'a>, ESExpr<'a>>) {
                for ($key, v) in self {
                    kwargs.insert($encode_key, v.encode_esexpr());
                }
            }

            fn decode_dict_element(
                kwargs: &mut hashbrown::HashMap<CowStr<'a>, ESExpr<'a>>,
                constructor_name: &str,
            ) -> Result<Self, DecodeError> {
                kwargs.drain()
                    .map(|($key, v)| {
                        let value = V::decode_esexpr(v).map_err(|mut e| {
                            e.error_path_with(|old_path| {
                                DecodeErrorPath::Keyword(constructor_name.to_owned(), $key.deref().to_owned(), Box::new(old_path))
                            });
                            e
                        })?;

                        Ok(($decode_key, value))
                    })
                    .collect()
            }
        }
    };
}



