use alloc::string::String;
use alloc::vec::Vec;

/// Defines value equality.
/// This is intended to provide an alternative comparison that defines
/// a total equality on types that do not implement Eq.
pub trait ValueEq<Rhs: ?Sized = Self> {
	/// Compare two values for equality.
	fn value_eq(&self, other: &Rhs) -> bool;
}

macro_rules! value_eq_scalar {
	($t: ty) => {
		impl ValueEq for $t {
			fn value_eq(&self, other: &Self) -> bool {
				*self == *other
			}
		}
	};
}

value_eq_scalar!(i8);
value_eq_scalar!(i16);
value_eq_scalar!(i32);
value_eq_scalar!(i64);
value_eq_scalar!(i128);
value_eq_scalar!(isize);

value_eq_scalar!(u8);
value_eq_scalar!(u16);
value_eq_scalar!(u32);
value_eq_scalar!(u64);
value_eq_scalar!(u128);
value_eq_scalar!(usize);

value_eq_scalar!(bool);
value_eq_scalar!(str);
value_eq_scalar!(String);

impl ValueEq<String> for str {
	fn value_eq(&self, other: &String) -> bool {
		self == other.as_str()
	}
}

impl ValueEq<str> for String {
	fn value_eq(&self, other: &str) -> bool {
		self.as_str() == other
	}
}

value_eq_scalar!(num_bigint::BigInt);
value_eq_scalar!(num_bigint::BigUint);

impl ValueEq for f32 {
	fn value_eq(&self, other: &Self) -> bool {
		self.to_bits() == other.to_bits()
	}
}

impl ValueEq for f64 {
	fn value_eq(&self, other: &Self) -> bool {
		self.to_bits() == other.to_bits()
	}
}

impl<T: ValueEq> ValueEq for Option<T> {
	fn value_eq(&self, other: &Self) -> bool {
		match (self, other) {
			(Some(a), Some(b)) => a.value_eq(b),
			(None, None) => true,
			_ => false,
		}
	}
}

impl<T: ValueEq> ValueEq for Vec<T> {
	fn value_eq(&self, other: &Self) -> bool {
		self.as_slice().value_eq(other.as_slice())
	}
}

impl<T: ValueEq> ValueEq for [T] {
	fn value_eq(&self, other: &Self) -> bool {
		if self.len() != other.len() {
			return false;
		}
		self.iter().zip(other.iter()).all(|(a, b)| a.value_eq(b))
	}
}

impl<T: ValueEq> ValueEq<[T]> for Vec<T> {
	fn value_eq(&self, other: &[T]) -> bool {
		self.as_slice().value_eq(other)
	}
}

impl<T: ValueEq> ValueEq<Vec<T>> for [T] {
	fn value_eq(&self, other: &Vec<T>) -> bool {
		self.value_eq(other.as_slice())
	}
}

#[cfg(feature = "std")]
impl<K: Eq + std::hash::Hash, V: ValueEq> ValueEq for std::collections::HashMap<K, V> {
	fn value_eq(&self, other: &Self) -> bool {
		self.len() == other.len() &&
			self.iter()
				.all(|(k, v)| other.get(k).is_some_and(|other_v| v.value_eq(other_v)))
	}
}

impl<K: Ord, V: ValueEq> ValueEq for alloc::collections::BTreeMap<K, V> {
	fn value_eq(&self, other: &Self) -> bool {
		self.len() == other.len() &&
			self.iter()
				.all(|(k, v)| other.get(k).is_some_and(|other_v| v.value_eq(other_v)))
	}
}

impl<T: ValueEq> ValueEq for alloc::boxed::Box<T> {
	fn value_eq(&self, other: &Self) -> bool {
		T::value_eq(self, other)
	}
}

impl<'a, T: ValueEq> ValueEq for &'a T {
	fn value_eq(&self, other: &Self) -> bool {
		T::value_eq(self, other)
	}
}

impl<'a, T: ValueEq> ValueEq for &'a mut T {
	fn value_eq(&self, other: &Self) -> bool {
		T::value_eq(self, other)
	}
}
