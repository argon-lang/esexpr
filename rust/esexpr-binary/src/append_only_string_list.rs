use alloc::string::String;
use alloc::vec::Vec;
use core::cell::UnsafeCell;

use static_assertions::assert_not_impl_any;

pub struct AppendOnlyStringList {
	values: UnsafeCell<Vec<String>>,
}

impl AppendOnlyStringList {
	pub fn get(&self, i: usize) -> Option<&str> {
		unsafe { self.values.get().as_ref().expect("list storage should be initialized") }
			.get(i)
			.map(String::as_str)
	}

	pub fn append(&self, values: &mut Vec<String>) {
		let vec = unsafe { self.values.get().as_mut().expect("list storage should be initialized") };
		vec.append(values);
	}

	pub fn push(&self, value: String) {
		let vec = unsafe { self.values.get().as_mut().expect("list storage should be initialized") };
		vec.push(value);
	}
}

impl From<Vec<String>> for AppendOnlyStringList {
	fn from(values: Vec<String>) -> Self {
		Self {
			values: UnsafeCell::new(values),
		}
	}
}

assert_not_impl_any!(AppendOnlyStringList: Sync);

#[cfg(test)]
mod tests {
	use alloc::borrow::ToOwned;
	use alloc::vec;

	use super::*;

	#[test]
	fn test_new_list_is_empty() {
		let list = AppendOnlyStringList::from(vec![]);
		assert!(list.get(0).is_none());
	}

	#[test]
	fn test_append_and_get() {
		let list = AppendOnlyStringList::from(vec![]);
		list.push("Hello".to_owned());

		assert_eq!(list.get(0), Some("Hello"));
		assert_eq!(list.get(1), None);
	}

	#[test]
	fn test_multiple_appends() {
		let list = AppendOnlyStringList::from(vec![]);
		list.push("First".to_owned());
		list.push("Second".to_owned());
		list.push("Third".to_owned());

		assert_eq!(list.get(0), Some("First"));
		assert_eq!(list.get(1), Some("Second"));
		assert_eq!(list.get(2), Some("Third"));
		assert_eq!(list.get(3), None);
	}

	#[test]
	fn test_out_of_bounds() {
		let list = AppendOnlyStringList::from(vec![]);
		list.push("Test".to_owned());

		assert_eq!(list.get(100), None);
	}

	#[test]
	fn test_resize() {
		let list = AppendOnlyStringList::from(vec![]);

		unsafe {
			let values = list.values.get().as_mut().expect("list storage should be initialized");
			values.reserve_exact(1);
		}
		list.push("A".to_owned());
		let a1 = core::ptr::from_ref(list.get(0).expect("first string should exist"));
		let sp1: *const String =
			unsafe { core::ptr::from_ref(&list.values.get().as_ref().expect("list storage should be initialized")[0]) };

		let mut a2;
		loop {
			list.push("B".to_owned());

			a2 = core::ptr::from_ref(list.get(0).expect("first string should exist"));
			let sp2: *const String = unsafe {
				core::ptr::from_ref(&list.values.get().as_ref().expect("list storage should be initialized")[0])
			};

			if sp1 != sp2 {
				break;
			}
		}

		assert_eq!(a1, a2);
	}
}
