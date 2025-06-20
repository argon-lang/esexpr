use core::f32;
use std::borrow::Cow;
use std::collections::HashMap;
use std::str::FromStr;

use esexpr::ESExpr;
use hexfloat2::{HexFloat32, HexFloat64};
use nom::branch::alt;
use nom::bytes::complete::{escaped_transform, tag, tag_no_case, take_while, take_while_m_n, take_while1, take_until};
use nom::character::complete::{alphanumeric1, char, digit1, hex_digit1, multispace1, none_of, one_of};
use nom::combinator::{cut, eof, map, map_res, not, opt, peek, recognize, value};
use nom::multi::{many0, many0_count};
use nom::sequence::{delimited, pair, preceded, separated_pair, terminated};
use nom::{IResult, Parser};
use num_bigint::{BigInt, BigUint, Sign};

/// Represents a lexer error.
#[derive(Debug, Clone, PartialEq)]
pub enum LexErrorType {
	/// Unexpected token.
	UnexpectedToken,
	
	/// Unterminated string.
	UnterminatedString,
	
	/// Unterminated identifier string.
	UnterminatedIdentifierString,

	/// Invalid unicode codepoint.
	InvalidUnicodeCodePoint(u32),
}

/// Parser that skips whitespace and comments.
///
/// # Errors
/// Returns `Err` when parsing fails.
pub fn skip_ws(input: &str) -> IResult<&str, ()> {
	value((), many0_count(alt((value((), multispace1), comment)))).parse(input)
}

fn comment(input: &str) -> IResult<&str, ()> {
	value((), pair(tag("//"), take_until("\n"))).parse(input)
}

fn is_alpha(c: char) -> bool {
	c.is_ascii_lowercase()
}

fn is_alphanum(c: char) -> bool {
	c.is_ascii_lowercase() || c.is_ascii_digit()
}

/// Parser for a simple identifier.
///
/// # Errors
/// Returns `Err` when parsing fails.
pub fn simple_identifier(input: &str) -> IResult<&str, &str> {
	preceded(
		skip_ws,
		recognize((
			take_while1(is_alpha),
			take_while(is_alphanum),
			many0(pair(char('-'), take_while1(is_alphanum))),
		)),
	)
	.parse(input)
}

fn identifier(input: &str) -> IResult<&str, String> {
	alt((
		map(simple_identifier, String::from),
		preceded(skip_ws, string_impl('\'', "'\\")),
	))
	.parse(input)
}

fn float_decimal(input: &str) -> IResult<&str, ESExpr<'static>> {
	map(
		recognize((
			opt(one_of("+-")),
			digit1,
			char('.'),
			cut(digit1),
			opt((one_of("eE"), opt(one_of("+-")), digit1)),
			opt(one_of("fFdD")),
			not(peek(alphanumeric1)),
		)),
		parse_dec_float,
	)
	.parse(input)
}

fn parse_dec_float(s: &str) -> ESExpr<'static> {
	if s.ends_with('f') || s.ends_with('F') {
		#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
		let f = s.trim_end_matches('f').trim_end_matches('F').parse::<f32>().unwrap();
		ESExpr::Float32(f)
	}
	else {
		#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
		let d = s.trim_end_matches('f').trim_end_matches('F').parse::<f64>().unwrap();
		ESExpr::Float64(d)
	}
}

fn float_hex(input: &str) -> IResult<&str, ESExpr<'static>> {
	map(
		recognize((
			opt(one_of("+-")),
			tag_no_case("0x"),
			hex_digit1,
			char('.'),
			hex_digit1,
			cut(one_of("pP")),
			opt(one_of("+-")),
			digit1,
			opt(one_of("fFdD")),
			not(peek(alphanumeric1)),
		)),
		parse_hex_float,
	)
	.parse(input)
}

fn parse_hex_float(s: &str) -> ESExpr<'static> {
	if s.ends_with('f') || s.ends_with('F') {
		#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
		let f = s
			.trim_end_matches('f')
			.trim_end_matches('F')
			.parse::<HexFloat32>()
			.unwrap();
		ESExpr::Float32(*f)
	}
	else {
		#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
		let d = s
			.trim_end_matches('f')
			.trim_end_matches('F')
			.parse::<HexFloat64>()
			.unwrap();
		ESExpr::Float64(*d)
	}
}

fn float<'a>(input: &'a str) -> IResult<&'a str, ESExpr<'static>> {
	preceded(
		skip_ws,
		alt((
			float_decimal,
			float_hex,
			value(ESExpr::Float32(f32::NAN), tag("#float32:nan")),
			value(ESExpr::Float32(f32::INFINITY), tag("#float32:+inf")),
			value(ESExpr::Float32(f32::NEG_INFINITY), tag("#float32:-inf")),
			value(ESExpr::Float64(f64::NAN), tag("#float64:nan")),
			value(ESExpr::Float64(f64::INFINITY), tag("#float64:+inf")),
			value(ESExpr::Float64(f64::NEG_INFINITY), tag("#float64:-inf")),
		)),
	)
	.parse(input)
}

fn integer(input: &str) -> IResult<&str, BigInt> {
	preceded(
		skip_ws,
		alt((
			map(
				recognize((opt(one_of("+-")), tag_no_case("0x"), hex_digit1)),
				|s: &str| parse_int_base(s, 16),
			),
			map(recognize((opt(one_of("+-")), digit1)), |s: &str| {
				#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
				s.parse::<BigInt>().unwrap()
			}),
		)),
	)
	.parse(input)
}

fn parse_int_base(s: &str, radix: u32) -> BigInt {
	let sign = if s.starts_with('-') { Sign::Minus } else { Sign::Plus };

	let s = s
		.trim_start_matches('+')
		.trim_start_matches('-')
		.trim_start_matches("0x")
		.trim_start_matches("0X");

	let b: Vec<u8> = s.chars().map(|c| {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
		#[expect(
			clippy::cast_possible_truncation,
			reason = "Shouldn't be out of range because it is a single digit" 
		)]
		{ c.to_digit(radix).unwrap() as u8 }
	}).collect();

	#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
	BigInt::from_radix_be(sign, &b, radix).unwrap()
}

fn string(input: &str) -> IResult<&str, String> {
	preceded(skip_ws, string_impl('"', "\"\\")).parse(input)
}

fn string_impl<'a>(
	quote: char,
	non_normal_chars: &'static str,
) -> impl Parser<&'a str, Output = String, Error = nom::error::Error<&'a str>> {
	move |input| {
		delimited(
			char(quote),
			escaped_transform(
				none_of(non_normal_chars),
				'\\',
				alt((
					value('\x0C', char('f')),
					value('\n', char('n')),
					value('\r', char('r')),
					value('\t', char('t')),
					value('\\', char('\\')),
					value('"', char('"')),
					value('\'', char('\'')),
					delimited(
						tag("u{"),
						map_res(hex_digit1, |codepoint| {
							#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
							let codepoint = u32::from_str_radix(codepoint, 16).unwrap();
							char::from_u32(codepoint).ok_or(LexErrorType::InvalidUnicodeCodePoint(codepoint))
						}),
						char('}'),
					),
				)),
			),
			char(quote),
		)
		.parse(input)
	}
}

fn binary(input: &str) -> IResult<&str, Vec<u8>> {
	delimited(preceded(skip_ws, tag("#\"")), many0(hex_byte), cut(tag("\""))).parse(input)
}

fn hex_byte(input: &str) -> IResult<&str, u8> {
	map(take_while_m_n(2, 2, |c: char| c.is_ascii_hexdigit()), |s| {
		#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
		u8::from_str_radix(s, 16).unwrap()
	})
	.parse(input)
}

enum ConstructorArg {
	Positional(ESExpr<'static>),
	Keyword(String, ESExpr<'static>),
}

fn constructor(input: &str) -> IResult<&str, ESExpr<'static>> {
	map(
		delimited(
			preceded(skip_ws, char('(')),
			pair(identifier, many0(constructor_arg)),
			preceded(skip_ws, char(')')),
		),
		|(name, args)| build_constructor(name, args),
	)
	.parse(input)
}

fn build_constructor(name: String, ctor_args: Vec<ConstructorArg>) -> ESExpr<'static> {
	let mut args = Vec::new();
	let mut kwargs = HashMap::new();

	for arg in ctor_args {
		match arg {
			ConstructorArg::Positional(value) => args.push(value),
			ConstructorArg::Keyword(name, value) => {
				kwargs.insert(Cow::Owned(name), value);
			},
		}
	}

	ESExpr::Constructor {
		name: Cow::Owned(name),
		args: Cow::Owned(args),
		kwargs: Cow::Owned(kwargs),
	}
}

fn constructor_arg(input: &str) -> IResult<&str, ConstructorArg> {
	alt((
		map(
			separated_pair(preceded(skip_ws, identifier), preceded(skip_ws, char(':')), expr),
			|(name, value)| ConstructorArg::Keyword(name, value),
		),
		map(expr, ConstructorArg::Positional),
	))
	.parse(input)
}

fn null_atom(input: &str) -> IResult<&str, ESExpr<'static>> {
	map((skip_ws, tag("#null"), digit1, not(alphanumeric1)), |(_, _, n, _)| {
		#[expect(clippy::unwrap_used, reason = "Shouldn't fail because the parser should ensure the format is valid.")]
		ESExpr::Null(Cow::Owned(BigUint::from_str(n).unwrap()))
	})
	.parse(input)
}

fn atom<'a>(
	expr: ESExpr<'static>,
	s: &'static str,
) -> impl Parser<&'a str, Output = ESExpr<'static>, Error = nom::error::Error<&'a str>> {
	move |input| value(expr.clone(), preceded(skip_ws, terminated(tag(s), not(alphanumeric1)))).parse(input)
}

/// Parser for an `ESExpr` expression.
///
/// # Errors
/// Returns `Err` when parsing fails.
pub fn expr(input: &str) -> IResult<&str, ESExpr<'static>> {
	alt((
		float,
		map(integer, |i| ESExpr::Int(Cow::Owned(i))),
		map(string, |s| ESExpr::Str(Cow::Owned(s))),
		map(binary, |b| ESExpr::Binary(Cow::Owned(b))),
		atom(ESExpr::Bool(true), "#true"),
		atom(ESExpr::Bool(false), "#false"),
		null_atom,
		atom(ESExpr::Null(Cow::Owned(BigUint::ZERO)), "#null"),
		constructor,
	))
	.parse(input)
}

pub(crate) fn expr_file(input: &str) -> IResult<&str, ESExpr<'static>> {
	terminated(terminated(expr, skip_ws), eof).parse(input)
}


pub(crate) fn multi_expr_file(input: &str) -> IResult<&str, Vec<ESExpr<'static>>> {
	terminated(terminated(many0(expr), skip_ws), eof).parse(input)
}
