use alloc::borrow::Cow;
use alloc::collections::BTreeMap;
use alloc::string::String;
use alloc::vec::Vec;
use core::str::FromStr;

use esexpr::ESExpr;
use esexpr::cowstr::CowStr;
use half::f16;
use nom::branch::alt;
use nom::bytes::complete::{escaped_transform, tag, tag_no_case, take_until, take_while, take_while_m_n, take_while1};
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

	/// Invalid NaN payload bits
	InvalidNaNPayload(u64),
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
			opt(alt((tag("f16"), tag("F16"), tag("f"), tag("F"), tag("d"), tag("D")))),
			not(peek(alphanumeric1)),
		)),
		parse_dec_float,
	)
	.parse(input)
}

fn parse_dec_float(s: &str) -> ESExpr<'static> {
	if s.ends_with("f16") || s.ends_with("F16") {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
		let f = s.trim_end_matches("f16")
			.trim_end_matches("F16")
			.parse::<f16>()
			.unwrap();
		ESExpr::Float16(f)
	}
	else if s.ends_with('f') || s.ends_with('F') {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
		let f = s.trim_end_matches('f').trim_end_matches('F').parse::<f32>().unwrap();
		ESExpr::Float32(f)
	}
	else {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
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
			opt(alt((tag("f16"), tag("F16"), tag("f"), tag("F"), tag("d"), tag("D")))),
			not(peek(alphanumeric1)),
		)),
		parse_hex_float,
	)
	.parse(input)
}

fn parse_hex_float(s: &str) -> ESExpr<'static> {
	if s.ends_with("f16") || s.ends_with("F16") {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
		let repr: hexponent::FloatLiteral = s
			.trim_end_matches("f16")
			.trim_end_matches("f16")
			.parse::<hexponent::FloatLiteral>()
			.unwrap();
		let f = repr.convert().inner();

		ESExpr::Float16(f16::from_f32(f))
	}
	else if s.ends_with('f') || s.ends_with('F') {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
		let repr: hexponent::FloatLiteral = s
			.trim_end_matches('f')
			.trim_end_matches('F')
			.parse::<hexponent::FloatLiteral>()
			.unwrap();
		let f = repr.convert().inner();

		ESExpr::Float32(f)
	}
	else {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
		let repr: hexponent::FloatLiteral = s
			.trim_end_matches('d')
			.trim_end_matches('D')
			.parse::<hexponent::FloatLiteral>()
			.unwrap();
		let d = repr.convert().inner();
		ESExpr::Float64(d)
	}
}

fn float16_nan(input: &str) -> IResult<&str, ESExpr<'static>> {
	map_res(
		(
			tag("#float16:"),
			opt(one_of("+-")),
			tag("nan"),
			opt(preceded(
				tag(":"),
				nom::character::complete::u16
			))
		),
		|(_, sign, _, payload)| {
			let is_neg = sign.is_some_and(|sign| sign == '-');

			let Some(payload) = payload else {
				if is_neg {
					return Ok(ESExpr::Float16(-f16::NAN));
				}
				else {
					return Ok(ESExpr::Float16(f16::NAN));
				}
			};

			if (payload & 0xFC00) != 0 {
				return Err(LexErrorType::InvalidNaNPayload(u64::from(payload)))
			}

			let sign_bit: u16 = if is_neg { 0x8000 } else { 0 };
			let exponent: u16 = 0x7C00;

			let f = f16::from_bits(sign_bit | exponent | payload);

			Ok(ESExpr::Float16(f))
		}
	).parse(input)
}

fn float32_nan(input: &str) -> IResult<&str, ESExpr<'static>> {
	map_res(
		(
			tag("#float32:"),
			opt(one_of("+-")),
			tag("nan"),
			opt(preceded(
				tag(":"),
				nom::character::complete::u32
			))
		),
		|(_, sign, _, payload)| {
			let is_neg = sign.is_some_and(|sign| sign == '-');

			let Some(payload) = payload else {
				if is_neg {
					return Ok(ESExpr::Float32(-f32::NAN));
				}
				else {
					return Ok(ESExpr::Float32(f32::NAN));
				}
			};

			if (payload & 0xFF800000) != 0 {
				return Err(LexErrorType::InvalidNaNPayload(u64::from(payload)))
			}

			let sign_bit: u32 = if is_neg { 0x80000000 } else { 0 };
			let exponent: u32 = 0x7F800000;

			let f = f32::from_bits(sign_bit | exponent | payload);

			Ok(ESExpr::Float32(f))
		}
	).parse(input)
}

fn float64_nan(input: &str) -> IResult<&str, ESExpr<'static>> {
	map_res(
		(
			tag("#float64:"),
			opt(one_of("+-")),
			tag("nan"),
			opt(preceded(
				tag(":"),
				nom::character::complete::u64
			))
		),
		|(_, sign, _, payload)| {
			let is_neg = sign.is_some_and(|sign| sign == '-');

			let Some(payload) = payload else {
				if is_neg {
					return Ok(ESExpr::Float64(-f64::NAN));
				}
				else {
					return Ok(ESExpr::Float64(f64::NAN));
				}
			};

			if (payload & 0xFFF0000000000000) != 0 {
				return Err(LexErrorType::InvalidNaNPayload(payload));
			}

			let sign_bit: u64 = if is_neg { 0x8000000000000000 } else { 0 };
			let exponent: u64 = 0x7FF0000000000000;

			let f = f64::from_bits(sign_bit | exponent | payload);

			Ok(ESExpr::Float64(f))
		}
	).parse(input)
}

fn float<'a>(input: &'a str) -> IResult<&'a str, ESExpr<'static>> {
	preceded(
		skip_ws,
		alt((
			float_decimal,
			float_hex,
			float16_nan,
			atom(ESExpr::Float16(f16::INFINITY), "#float16:+inf"),
			atom(ESExpr::Float16(f16::NEG_INFINITY), "#float16:-inf"),
			float32_nan,
			atom(ESExpr::Float32(f32::INFINITY), "#float32:+inf"),
			atom(ESExpr::Float32(f32::NEG_INFINITY), "#float32:-inf"),
			float64_nan,
			atom(ESExpr::Float64(f64::INFINITY), "#float64:+inf"),
			atom(ESExpr::Float64(f64::NEG_INFINITY), "#float64:-inf"),
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
				#[expect(
					clippy::unwrap_used,
					reason = "Shouldn't fail because the parser should ensure the format is valid."
				)]
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

	let b: Vec<u8> = s
		.chars()
		.map(|c| {
			#[expect(
				clippy::unwrap_used,
				reason = "Shouldn't fail because the parser should ensure the format is valid."
			)]
			#[expect(
				clippy::cast_possible_truncation,
				reason = "Shouldn't be out of range because it is a single digit"
			)]
			{
				c.to_digit(radix).unwrap() as u8
			}
		})
		.collect();

	#[expect(
		clippy::unwrap_used,
		reason = "Shouldn't fail because the parser should ensure the format is valid."
	)]
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
						map_res(hex_digit1, |codepoint| -> Result<core::primitive::char, LexErrorType> {
							#[expect(
								clippy::unwrap_used,
								reason = "Shouldn't fail because the parser should ensure the format is valid."
							)]
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

fn binary(input: &str) -> IResult<&str, ESExpr<'static>> {
	alt((
		map(
			delimited(preceded(skip_ws, tag("#\"")), many0(hex_byte), cut(tag("\""))),
			|b| ESExpr::Array8(Cow::Owned(b)),
		),
		map(
			delimited(
				preceded(skip_ws, tag("#u8[")),
				many0(map_res(preceded(skip_ws, integer), u8::try_from)),
				preceded(skip_ws, cut(tag("]"))),
			),
			|b| ESExpr::Array8(Cow::Owned(b)),
		),
		map(
			delimited(
				preceded(skip_ws, tag("#u16[")),
				many0(map_res(preceded(skip_ws, integer), u16::try_from)),
				preceded(skip_ws, cut(tag("]"))),
			),
			|b| ESExpr::Array16(Cow::Owned(b)),
		),
		map(
			delimited(
				preceded(skip_ws, tag("#u32[")),
				many0(map_res(preceded(skip_ws, integer), u32::try_from)),
				preceded(skip_ws, cut(tag("]"))),
			),
			|b| ESExpr::Array32(Cow::Owned(b)),
		),
		map(
			delimited(
				preceded(skip_ws, tag("#u64[")),
				many0(map_res(preceded(skip_ws, integer), u64::try_from)),
				preceded(skip_ws, cut(tag("]"))),
			),
			|b| ESExpr::Array64(Cow::Owned(b)),
		),
		map(
			delimited(
				preceded(skip_ws, tag("#u128[")),
				many0(map_res(preceded(skip_ws, integer), u128::try_from)),
				preceded(skip_ws, cut(tag("]"))),
			),
			|b| ESExpr::Array128(Cow::Owned(b)),
		),
	))
	.parse(input)
}

fn hex_byte(input: &str) -> IResult<&str, u8> {
	map(take_while_m_n(2, 2, |c: char| c.is_ascii_hexdigit()), |s| {
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
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
	let mut kwargs = BTreeMap::new();

	for arg in ctor_args {
		match arg {
			ConstructorArg::Positional(value) => args.push(value),
			ConstructorArg::Keyword(name, value) => {
				kwargs.insert(CowStr::Owned(name), value);
			},
		}
	}

	ESExpr::constructor(name, args, kwargs)
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
		#[expect(
			clippy::unwrap_used,
			reason = "Shouldn't fail because the parser should ensure the format is valid."
		)]
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
		map(string, |s| ESExpr::Str(CowStr::Owned(s))),
		binary,
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
