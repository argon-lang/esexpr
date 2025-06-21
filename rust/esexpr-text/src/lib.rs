//! `ESExpr` text format parser.
use esexpr::ESExpr;

/// Underlying nom parser.
pub mod parser;

/// Parse a string into an `ESExpr`.
///
/// # Errors
/// Returns `Err` when parsing fails.
pub fn parse<'input>(s: &'input str) -> Result<ESExpr<'static>, nom::Err<nom::error::Error<&'input str>>> {
	let (_, expr) = parser::expr_file(s)?;
	Ok(expr)
}

/// Parse a string into multiple `ESExpr`s.
///
/// # Errors
/// Returns `Err` when parsing fails.
pub fn parse_multi<'input>(s: &'input str) -> Result<Vec<ESExpr<'static>>, nom::Err<nom::error::Error<&'input str>>> {
	let (_, expr) = parser::multi_expr_file(s)?;
	Ok(expr)
}
