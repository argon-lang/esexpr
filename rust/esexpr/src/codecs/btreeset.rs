use alloc::collections::BTreeSet;
use alloc::vec::Vec;
use core::ops::Deref;

use crate::cowstr::CowStr;
use crate::*;

set_encoded_eq_impl!(BTreeSet<V>, V: ESExprEncodedEq + Ord);
set_codec_impl!(BTreeSet<V>, 'a, V: ESExprCodec<'a> + Ord);

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_btreeset_codec() {
        let mut set = BTreeSet::new();
        set.insert(1i64);
        set.insert(2i64);

        let expr = set.encode_esexpr();
        assert_eq!(expr, esexpr! { (set 1 2) });

        let decoded: BTreeSet<i64> = ESExprCodec::decode_esexpr(expr).unwrap();
        assert_eq!(set, decoded);
    }
}
