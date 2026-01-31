use std::collections::HashSet;
use std::hash::{BuildHasher, Hash};
use std::ops::Deref;
use std::vec::Vec;
use esexpr::ESExprEncodedEq;

use crate::cowstr::CowStr;
use crate::{
	DecodeError,
	DecodeErrorPath,
	DecodeErrorType,
	ESExpr,
	ESExprCodec,
	ESExprTag,
	ESExprTagSet,
};

set_encoded_eq_impl!(HashSet<V, S>, 'a, V: ESExprCodec<'a> + Eq + Hash, S: BuildHasher);
set_codec_impl!(HashSet<V, S>, 'a, V: ESExprCodec<'a> + Eq + Hash, S: BuildHasher + Default + 'static);

#[cfg(test)]
mod tests {
    use super::*;
    use crate::esexpr;

    #[test]
    fn test_hashset_codec() {
        let mut set = HashSet::new();
        set.insert(1i64);

        let expr = set.encode_esexpr();
        assert_eq!(expr, esexpr! { (set 1) });

        let decoded: HashSet<i64> = ESExprCodec::decode_esexpr(expr).unwrap();
        assert_eq!(set, decoded);
    }
}
