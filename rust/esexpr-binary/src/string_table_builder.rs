use alloc::collections::BTreeMap;
use alloc::string::String;
use alloc::vec::Vec;

use esexpr::ESExpr;

use crate::format::{inline_string_size, tagged_int_size};

#[derive(Default)]
struct Usage {
	required: usize,
	literal: usize,
}

/// Records expression strings to build a compact, frequency-ordered string table.
/// Dedicated constructor tags do not require table entries.
#[derive(Default)]
pub struct StringTableBuilder {
	strings: BTreeMap<String, Usage>,
}

impl StringTableBuilder {
	/// Create an empty builder.
	#[must_use]
	pub fn new() -> Self {
		Self::default()
	}

	/// Record all table uses and literal strings in an expression, recursively.
	/// The expression may be dropped after recording.
	pub fn record(&mut self, expr: &ESExpr<'_>) {
		match expr {
			ESExpr::Constructor(constructor) => {
				if !matches!(&*constructor.name, "list" | "map" | "set" | "string-table") {
					self.record_string(&constructor.name, true);
				}
				for arg in &constructor.args {
					self.record(&arg);
				}
				for (key, value) in &constructor.kwargs {
					self.record_string(&key, true);
					self.record(&value);
				}
			},
			ESExpr::Str(value) => self.record_string(value, false),
			_ => {},
		}
	}

	fn record_string(&mut self, value: &str, required: bool) {
		let usage = if let Some(usage) = self.strings.get_mut(value) {
			usage
		}
		else {
			self.strings.entry(String::from(value)).or_default()
		};
		let count = if required {
			&mut usage.required
		}
		else {
			&mut usage.literal
		};
		*count = count.saturating_add(1);
	}

	/// Consume the builder and move selected strings into a table.
	///
	/// Entries are ordered by decreasing combined frequency, with lexical ties.
	/// Constructor and keyword entries are mandatory. Literal-only entries must
	/// occur at least twice and save more bytes than their inline table-entry cost
	/// at their prospective index. Shared framing and effects on later indices
	/// are excluded from this heuristic.
	#[must_use]
	pub fn build(self) -> Vec<String> {
		let mut strings: Vec<_> = self.strings.into_iter().collect();
		strings.sort_unstable_by(|(a, au), (b, bu)| {
			bu.required
				.saturating_add(bu.literal)
				.cmp(&au.required.saturating_add(au.literal))
				.then_with(|| a.cmp(b))
		});
		let mut table = Vec::new();
		for (value, usage) in strings {
			let inline = inline_string_size(&value);
			let saving = inline.saturating_sub(tagged_int_size(table.len()));
			// Division avoids overflowing count * saving.
			if usage.required != 0 || (usage.literal > 1 && saving != 0 && usage.literal > inline / saving) {
				table.push(value);
			}
		}
		table
	}
}
