use alloc::borrow::Cow;
use alloc::collections::BTreeMap;
use alloc::vec;

use half::f16;
use num_bigint::{BigInt, BigUint};
use proptest::prelude::*;

use crate::ESExpr;

impl Arbitrary for ESExpr<'static> {
	type Parameters = ();
	type Strategy = BoxedStrategy<Self>;

	fn arbitrary_with((): Self::Parameters) -> Self::Strategy {
		let bytes = proptest::collection::vec(any::<u8>(), 0..64);
		let leaf = prop_oneof![
			any::<bool>().prop_map(ESExpr::Bool),
			(any::<bool>(), bytes.clone()).prop_map(|(negative, bytes)| {
				let magnitude = BigUint::from_bytes_le(&bytes);
				let value = BigInt::from(magnitude);
				ESExpr::Int(Cow::Owned(if negative { -value } else { value }))
			}),
			any::<alloc::string::String>().prop_map(|s| ESExpr::Str(s.into())),
			any::<u16>().prop_map(|bits| ESExpr::Float16(f16::from_bits(bits))),
			any::<u32>().prop_map(|bits| ESExpr::Float32(f32::from_bits(bits))),
			any::<u64>().prop_map(|bits| ESExpr::Float64(f64::from_bits(bits))),
			bytes.clone().prop_map(|v| ESExpr::Array8(Cow::Owned(v))),
			proptest::collection::vec(any::<u16>(), 0..32).prop_map(|v| ESExpr::Array16(Cow::Owned(v))),
			proptest::collection::vec(any::<u32>(), 0..32).prop_map(|v| ESExpr::Array32(Cow::Owned(v))),
			proptest::collection::vec(any::<u64>(), 0..32).prop_map(|v| ESExpr::Array64(Cow::Owned(v))),
			proptest::collection::vec(any::<u128>(), 0..32).prop_map(|v| ESExpr::Array128(Cow::Owned(v))),
			bytes.prop_map(|bytes| ESExpr::Null(Cow::Owned(BigUint::from_bytes_le(&bytes)))),
		];

		leaf.prop_recursive(4, 64, 8, |inner| {
			(
				any::<alloc::string::String>(),
				proptest::collection::vec(inner.clone(), 0..8),
				proptest::collection::btree_map(any::<alloc::string::String>(), inner, 0..8),
			)
				.prop_map(|(name, args, kwargs)| {
					let kwargs: BTreeMap<_, _> = kwargs.into_iter().map(|(key, value)| (key.into(), value)).collect();
					ESExpr::constructor(name, args, kwargs)
				})
		})
		.boxed()
	}
}
