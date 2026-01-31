use alloc::borrow::ToOwned;
use alloc::collections::BTreeMap;
use hashbrown::HashMap;
use alloc::string::String;
use core::ops::Deref;
use crate::*;
use crate::cowstr::CowStr;

/**
 * A dictionary implementation using string keys.
 */
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct Dictionary<V>(HashMap<String, V>);

impl <V> Dictionary<V> {
	/**
	 * Create a new dictionary.
	 */
	pub fn new() -> Self {
		Dictionary(HashMap::new())
	}
}

impl <V> From<HashMap<String, V>> for Dictionary<V> {
	fn from(hashmap: HashMap<String, V>) -> Self {
		Dictionary(hashmap)
	}
}

impl <V> From<BTreeMap<String, V>> for Dictionary<V> {
	fn from(btree_map: BTreeMap<String, V>) -> Self {
		Dictionary(btree_map.into_iter().collect())
	}
}

#[cfg(feature = "std")]
impl <V> From<std::collections::HashMap<String, V>> for Dictionary<V> {
	fn from(hashmap: std::collections::HashMap<String, V>) -> Self {
		Dictionary(hashmap.into_iter().collect())
	}
}

impl <V> From<Dictionary<V>> for HashMap<String, V> {
	fn from(dictionary: Dictionary<V>) -> Self {
		dictionary.0
	}
}

impl <V> From<Dictionary<V>> for BTreeMap<String, V> {
	fn from(dictionary: Dictionary<V>) -> Self {
		dictionary.0.into_iter().collect()
	}
}

#[cfg(feature = "std")]
impl <V> From<Dictionary<V>> for std::collections::HashMap<String, V> {
	fn from(dictionary: Dictionary<V>) -> Self {
		dictionary.0.into_iter().collect()
	}
}

impl <V> Deref for Dictionary<V> {
	type Target = HashMap<String, V>;

	fn deref(&self) -> &Self::Target {
		&self.0
	}
}


impl<A: ESExprEncodedEq> ESExprEncodedEq for Dictionary<A> {
	fn is_encoded_eq(&self, other: &Self) -> bool {
		self.0.is_encoded_eq(&other.0)
	}
}

impl<'a, A: ESExprCodec<'a>> ESExprCodec<'a> for Dictionary<A> {
	const TAGS: ESExprTagSet = ESExprTagSet::Tags(&[ESExprTag::Constructor(CowStr::Static("dict"))]);

	fn encode_esexpr(&'a self) -> ESExpr<'a> {
		ESExpr::constructor(
			"dict",
			[],
			self.iter()
				.map(|(k, v)| (CowStr::Borrowed(k.as_str()), v.encode_esexpr()))
				.collect::<HashMap<_, _>>(),
		)
	}

	fn decode_esexpr(expr: ESExpr<'a>) -> Result<Self, DecodeError> {
		match expr {
			ESExpr::Constructor(ESExprConstructor { name, args, kwargs }) if name == "dict" => {
				if !args.is_empty() {
					return Err(DecodeError::new(
						DecodeErrorType::OutOfRange("Dict must not have positional arguments".to_owned()),
						DecodeErrorPath::Constructor(name.deref().to_owned()),
					));
				}

				let mut dict = HashMap::default();

				for (k, v) in kwargs {
					dict.insert(k.into_string(), A::decode_esexpr(v)?);
				}

				Ok(Dictionary(dict))
			},
			_ => Err(DecodeError::new(
				DecodeErrorType::UnexpectedExpr {
					expected_tags: Self::TAGS,
					actual_tag: expr.tag().into_owned(),
				},
				DecodeErrorPath::Current,
			)),
		}
	}
}


impl<'a, A: ESExprCodec<'a>> ESExprDictCodec<'a> for Dictionary<A> {
	type Element = A;

	fn encode_dict_element(&'a self, kwargs: &mut HashMap<CowStr<'a>, ESExpr<'a>>) {
		self.0.encode_dict_element(kwargs);
	}

	fn decode_dict_element(
		kwargs: &mut HashMap<CowStr<'a>, ESExpr<'a>>,
		constructor_name: &str,
	) -> Result<Self, DecodeError> {
		HashMap::<String, A>::decode_dict_element(kwargs, constructor_name).map(Dictionary)
	}
}

