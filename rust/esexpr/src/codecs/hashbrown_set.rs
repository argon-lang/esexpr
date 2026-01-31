use alloc::vec::Vec;
use core::ops::Deref;
use core::hash::Hash;
use hashbrown::HashSet;

use crate::cowstr::CowStr;
use crate::*;

set_encoded_eq_impl!(HashSet<V>, 'a, V: ESExprCodec<'a> + Eq + Hash);
set_codec_impl!(HashSet<V>, 'a, V: ESExprCodec<'a> + Eq + Hash);

#[cfg(test)]
mod tests {
    use super::*;
    use crate::esexpr;

    #[test]
    fn test_hashbrown_set_codec() {
        let mut set = HashSet::new();
        set.insert(1i64);

        let expr = set.encode_esexpr();
        assert_eq!(expr, esexpr! { (set 1) });

        let decoded: HashSet<i64> = ESExprCodec::decode_esexpr(expr).unwrap();
        assert_eq!(set, decoded);
    }
}
