use std::borrow::{Cow, ToOwned};
use std::boxed::Box;
use std::collections::HashMap;
use std::hash::{BuildHasher, Hash};
use std::ops::Deref;
use std::string::String;
use std::vec::Vec;
use itertools::Itertools;
use esexpr::{ESExprConstructor, ESExprEncodedEq};

use crate::cowstr::CowStr;
use crate::{
	DecodeError,
	DecodeErrorPath,
	DecodeErrorType,
	ESExpr,
	ESExprCodec,
	ESExprDictCodec,
	ESExprTag,
	ESExprTagSet,
};

map_encoded_eq_impl!(HashMap<K, V, S>, 'a, K: ESExprCodec<'a> + Eq + Hash, V: ESExprEncodedEq, S: BuildHasher);
map_codec_impl!(HashMap<K, V, S>, 'a, K: ESExprCodec<'a> + Eq + Hash, V: ESExprCodec<'a>, S: BuildHasher + Default + 'static);
map_dict_codec_impl!(HashMap<String, V, S>, k, CowStr::Borrowed(k), k.into_string(), 'a, V: ESExprCodec<'a>, S: BuildHasher + Default + 'static);
map_dict_codec_impl!(HashMap<Cow<'a, str>, V, S>, k, CowStr::Borrowed(k.as_ref()), Cow::from(k), 'a, V: ESExprCodec<'a>, S: BuildHasher + Default + 'static);
map_dict_codec_impl!(HashMap<CowStr<'a>, V, S>, k, k.as_borrowed(), k, 'a, V: ESExprCodec<'a>, S: BuildHasher + Default + 'static);

#[cfg(test)]
mod tests {
    use super::*;
    use crate::esexpr;

    #[test]
    fn test_hashmap_codec() {
        let mut map = HashMap::new();
        map.insert(1i64, "one".to_owned());

        let expr = map.encode_esexpr();
        assert_eq!(expr, esexpr! { (map 1 "one") });

        let decoded: HashMap<i64, String> = ESExprCodec::decode_esexpr(expr).unwrap();
        assert_eq!(map, decoded);
    }

    #[test]
    fn test_hashmap_dict_codec() {
        let mut map = HashMap::new();
        map.insert("one".to_owned(), 1i64);
        map.insert("two".to_owned(), 2i64);

        let mut kwargs = hashbrown::HashMap::new();
        map.encode_dict_element(&mut kwargs);

        assert_eq!(kwargs.len(), 2);
        assert_eq!(kwargs.get(&CowStr::Borrowed("one")).unwrap(), &esexpr! { 1 });
        assert_eq!(kwargs.get(&CowStr::Borrowed("two")).unwrap(), &esexpr! { 2 });

        let decoded: HashMap<String, i64> = ESExprDictCodec::decode_dict_element(&mut kwargs, "test").unwrap();
        assert_eq!(map, decoded);
    }
}
