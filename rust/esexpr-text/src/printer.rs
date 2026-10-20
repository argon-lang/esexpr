//! Compact, lossless text printing.
use alloc::format;
use alloc::string::String;
use alloc::vec::Vec;
use core::fmt::{self, Write};

use esexpr::ESExpr;

/// Indentation used for each nesting level in multiline output.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub enum Indentation {
	/// Compact output without line breaks or indentation.
	#[default]
	Compact,
	/// A fixed number of spaces per nesting level.
	Spaces(usize),
	/// A fixed number of tabs per nesting level.
	Tabs(usize),
}

/// Options controlling expression text formatting.
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
#[non_exhaustive]
pub struct ExprPrinter {
	/// Indentation for multiline constructors.
	/// `Indentation::Compact` (the default) prints compact text. `Indentation::Spaces(0)`
	/// adds line breaks without indentation. Arrays remain on a single line.
	pub indentation: Indentation,
}

impl ExprPrinter {
	/// Print an expression, preserving float bits and sorting keyword arguments.
	#[must_use]
	#[expect(clippy::missing_panics_doc, reason = "String formatting cannot fail")]
	pub fn format_as_string(&self, expr: &ESExpr<'_>) -> String {
		let mut output = String::new();
		self.write(&mut output, expr).expect("writing to a String cannot fail");
		output
	}

	/// Write an expression to a formatting sink using these options.
	///
	/// # Errors
	/// Returns an error if the sink fails to accept the output.
	pub fn write(&self, out: &mut impl Write, expr: &ESExpr<'_>) -> fmt::Result {
		PrinterState {
			writer: out,
			options: self,
			depth: 0,
		}
		.write_expr(expr)
	}
}

/// Print an expression using default compact formatting.
#[must_use]
pub fn format_as_string(expr: &ESExpr<'_>) -> String {
	ExprPrinter::default().format_as_string(expr)
}

struct PrinterState<'a, W> {
	writer: &'a mut W,
	options: &'a ExprPrinter,
	depth: usize,
}

impl<W: Write> PrinterState<'_, W> {
	fn line_break(&mut self) -> fmt::Result {
		self.writer.write_char('\n')?;
		for _ in 0..self.depth {
			match self.options.indentation {
				Indentation::Spaces(count) => {
					for _ in 0..count {
						self.writer.write_char(' ')?;
					}
				},
				Indentation::Tabs(count) => {
					for _ in 0..count {
						self.writer.write_char('\t')?;
					}
				},
				Indentation::Compact => {},
			}
		}
		Ok(())
	}

	fn separator(&mut self) -> fmt::Result {
		if self.options.indentation == Indentation::Compact {
			self.writer.write_char(' ')
		}
		else {
			self.line_break()
		}
	}

	fn quoted(&mut self, s: &str, quote: char) -> fmt::Result {
		self.writer.write_char(quote)?;
		for c in s.chars() {
			match c {
				'\\' => self.writer.write_str("\\\\")?,
				'\n' => self.writer.write_str("\\n")?,
				'\r' => self.writer.write_str("\\r")?,
				'\t' => self.writer.write_str("\\t")?,
				'\x0c' => self.writer.write_str("\\f")?,
				c if c == quote => {
					self.writer.write_char('\\')?;
					self.writer.write_char(c)?;
				},
				c if c.is_control() => write!(self.writer, "\\u{{{:x}}}", u32::from(c))?,
				c => self.writer.write_char(c)?,
			}
		}
		self.writer.write_char(quote)
	}

	fn identifier(&mut self, name: &str) -> fmt::Result {
		if crate::parser::simple_identifier(name).is_ok_and(|(rest, parsed)| rest.is_empty() && parsed == name) {
			self.writer.write_str(name)
		}
		else {
			self.quoted(name, '\'')
		}
	}

	fn float(&mut self, bits: u64, width: u32, finite: String, suffix: &str) -> fmt::Result {
		let (sign, exponent, payload) = match width {
			16 => (0x8000, 0x7C00, 0x3FF),
			32 => (0x8000_0000, 0x7F80_0000, 0x7F_FFFF),
			_ => (0x8000_0000_0000_0000, 0x7FF0_0000_0000_0000, 0xF_FFFF_FFFF_FFFF),
		};
		if bits & exponent == exponent {
			let sign = if bits & sign == 0 { "+" } else { "-" };
			if bits & payload == 0 {
				write!(self.writer, "#float{width}:{sign}inf")
			}
			else {
				write!(self.writer, "#float{width}:{sign}nan:{}", bits & payload)
			}
		}
		else {
			// The grammar requires digits on both sides of the decimal point.
			if finite.contains('.') {
				self.writer.write_str(&finite)?;
			}
			else if let Some(pos) = finite.find(['e', 'E']) {
				write!(self.writer, "{}.0{}", &finite[..pos], &finite[pos..])?;
			}
			else {
				write!(self.writer, "{finite}.0")?;
			}
			self.writer.write_str(suffix)
		}
	}

	fn array(&mut self, width: u32, values: &[impl fmt::Display]) -> fmt::Result {
		write!(self.writer, "#u{width}[")?;
		for (index, value) in values.iter().enumerate() {
			if index != 0 {
				self.writer.write_char(' ')?;
			}
			write!(self.writer, "{value}")?;
		}
		self.writer.write_char(']')
	}

	fn write_expr(&mut self, expr: &ESExpr<'_>) -> fmt::Result {
		match expr {
			ESExpr::Constructor(ctor) => {
				self.writer.write_char('(')?;
				self.identifier(&ctor.name)?;
				self.depth += 1;
				for arg in &ctor.args {
					self.separator()?;
					self.write_expr(&arg)?;
				}
				let mut kwargs: Vec<_> = ctor.kwargs.iter().collect();
				kwargs.sort_unstable_by(|(a, _), (b, _)| a.cmp(b));
				for (name, value) in kwargs {
					self.separator()?;
					self.identifier(&name)?;
					self.writer.write_str(": ")?;
					self.write_expr(&value)?;
				}
				self.depth -= 1;
				if self.options.indentation != Indentation::Compact &&
					(!ctor.args.is_empty() || !ctor.kwargs.is_empty())
				{
					self.line_break()?;
				}
				self.writer.write_char(')')
			},
			ESExpr::Bool(value) => self.writer.write_str(if *value { "#true" } else { "#false" }),
			ESExpr::Int(value) => write!(self.writer, "{value}"),
			ESExpr::Str(value) => self.quoted(value, '"'),
			ESExpr::Float16(value) => {
				self.float(u64::from(value.to_bits()), 16, format!("{:?}", value.to_f32()), "f16")
			},
			ESExpr::Float32(value) => self.float(u64::from(value.to_bits()), 32, format!("{value:?}"), "f"),
			ESExpr::Float64(value) => self.float(value.to_bits(), 64, format!("{value:?}"), "d"),
			ESExpr::Array8(value) => {
				self.writer.write_str("#\"")?;
				for byte in value.iter() {
					write!(self.writer, "{byte:02x}")?;
				}
				self.writer.write_char('"')
			},
			ESExpr::Array16(value) => self.array(16, value),
			ESExpr::Array32(value) => self.array(32, value),
			ESExpr::Array64(value) => self.array(64, value),
			ESExpr::Array128(value) => self.array(128, value),
			ESExpr::Null(value) => {
				if **value == num_bigint::BigUint::ZERO {
					self.writer.write_str("#null")
				}
				else {
					write!(self.writer, "#null{value}")
				}
			},
		}
	}
}
