use core::f32;
use std::{collections::HashMap, str::FromStr};

use esexpr::ESExpr;
use hexfloat2::{HexFloat32, HexFloat64};
use nom::{
    IResult,
    branch::alt,
    character::complete::{alphanumeric1, char, digit1, hex_digit1, multispace1, none_of, one_of},
    bytes::complete::{take_while, take_while1, tag, tag_no_case, escaped_transform, take_while_m_n, take_till},
    combinator::{cut, eof, map, map_res, not, opt, peek, recognize, value},
    multi::{many0, many0_count},
    sequence::{delimited, pair, preceded, separated_pair, terminated, tuple},
};
use num_bigint::{BigInt, BigUint, Sign};




#[derive(Debug, Clone, PartialEq)]
pub enum LexErrorType {
    UnexpectedToken,
    UnterminatedString,
    UnterminatedIdentifierString,

    InvalidUnicodeCodePoint(u32),
}


fn skip_ws(input: &str) -> IResult<&str, ()> {
	value(
		(),
		many0_count(
			alt((
				value((), multispace1),
				comment,
			))
		),
	)(input)
}

fn comment(input: &str) -> IResult<&str, ()> {
	value(
		(),
		pair(
			tag("//"),
			take_till(|c| c == '\n'),
		),
	)(input)
}


fn is_alpha(c: char) -> bool {
    c.is_ascii_lowercase()
}

fn is_alphanum(c: char) -> bool {
    c.is_ascii_lowercase() || c.is_ascii_digit()
}


pub fn simple_identifier(input: &str) -> IResult<&str, &str> {
    preceded(
        skip_ws,
         recognize(tuple((
            take_while1(is_alpha),
            take_while(is_alphanum),
            many0(
                pair(
                    char('-'),
                    take_while1(is_alphanum),
                )
            ),
        )))
    )(input)
}



pub fn identifier(input: &str) -> IResult<&str, String> {
    alt((
        map(simple_identifier, String::from),
        preceded(skip_ws, string_impl('\'', "'\\")),
    ))(input)
}


fn float_decimal(input: &str) -> IResult<&str, ESExpr> {
    map(recognize(tuple((
        opt(one_of("+-")),
        digit1,
        char('.'),
        cut(digit1),
        opt(tuple((
            one_of("eE"),
            opt(one_of("+-")),
            digit1,
        ))),
        opt(one_of("fFdD")),
        not(peek(alphanumeric1)),
    ))), parse_dec_float)(input)
}

fn parse_dec_float(s: &str) -> ESExpr {
    if s.ends_with("f") || s.ends_with("F") {
        let f = s.trim_end_matches("f").trim_end_matches("F").parse::<f32>().unwrap();
        ESExpr::Float32(f)
    }
    else {
        let d = s.trim_end_matches("f").trim_end_matches("F").parse::<f64>().unwrap();
        ESExpr::Float64(d)
    }
}

fn float_hex(input: &str) -> IResult<&str, ESExpr> {
    map(recognize(tuple((
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
    ))), parse_hex_float)(input)
}

fn parse_hex_float(s: &str) -> ESExpr {
    if s.ends_with("f") || s.ends_with("F") {
        let f = s.trim_end_matches("f").trim_end_matches("F").parse::<HexFloat32>().unwrap();
        ESExpr::Float32(*f)
    }
    else {
        let d = s.trim_end_matches("f").trim_end_matches("F").parse::<HexFloat64>().unwrap();
        ESExpr::Float64(*d)
    }
}

pub fn float(input: &str) -> IResult<&str, ESExpr> {
    preceded(skip_ws, alt((
        float_decimal,
        float_hex,
        value(ESExpr::Float32(f32::NAN), tag("#float32:nan")),
        value(ESExpr::Float32(f32::INFINITY), tag("#float32:+inf")),
        value(ESExpr::Float32(f32::NEG_INFINITY), tag("#float32:-inf")),
        value(ESExpr::Float64(f64::NAN), tag("#float64:nan")),
        value(ESExpr::Float64(f64::INFINITY), tag("#float64:+inf")),
        value(ESExpr::Float64(f64::NEG_INFINITY), tag("#float64:-inf")),
    )))(input)
}


pub fn integer(input: &str) -> IResult<&str, BigInt> {
    preceded(skip_ws, alt((
        map(recognize(tuple((
            opt(one_of("+-")),
            tag_no_case("0x"),
            hex_digit1,
        ))), |s: &str| parse_int_base(s, 16)),

        map(recognize(tuple((
            opt(one_of("+-")),
            digit1,
        ))), |s: &str| s.parse::<BigInt>().unwrap()),
    )))(input)
}


fn parse_int_base(s: &str, radix: u32) -> BigInt {
    let sign = if s.starts_with("-") {
        Sign::Minus
    }
    else {
        Sign::Plus
    };

    let s = s
        .trim_start_matches('+')
        .trim_start_matches('-')
        .trim_start_matches("0x")
        .trim_start_matches("0X");

    let b: Vec<u8> = s.chars().map(|c| {
        c.to_digit(radix).unwrap() as u8
    }).collect();

    BigInt::from_radix_be(sign, &b, radix).unwrap()
}

pub fn string(input: &str) -> IResult<&str, String> {
    preceded(
        skip_ws,
        string_impl('"', "\"\\")
    )(input)
}

fn string_impl(quote: char, non_normal_chars: &'static str) -> impl Fn(&str) -> IResult<&str, String> {
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
                            let codepoint = u32::from_str_radix(codepoint, 16).unwrap();
                            char::from_u32(codepoint).ok_or(LexErrorType::InvalidUnicodeCodePoint(codepoint))
                        }),
                        char('}'),
                    ),
                ))
            ),
            char(quote),
        )(input)
    }    
}



pub fn binary(input: &str) -> IResult<&str, Vec<u8>> {
    delimited(
        preceded(skip_ws, tag("#\"")),
        many0(hex_byte),
        cut(tag("\"")),
    )(input)
}

pub fn hex_byte(input: &str) -> IResult<&str, u8> {
    map(
        take_while_m_n(2, 2, |c: char| c.is_ascii_hexdigit()),
        |s| u8::from_str_radix(s, 16).unwrap()
    )(input)
}


enum ConstructorArg {
    Positional(ESExpr),
    Keyword(String, ESExpr),
}

pub fn constructor(input: &str) -> IResult<&str, ESExpr> {
    map(delimited(
        preceded(skip_ws, char('(')),
        pair(
            identifier,
            many0(constructor_arg),
        ),
        preceded(skip_ws, char(')')),
    ), |(name, args)| build_constructor(name, args))(input)
}

fn build_constructor(name: String, ctor_args: Vec<ConstructorArg>) -> ESExpr {
    let mut args = Vec::new();
    let mut kwargs = HashMap::new();

    for arg in ctor_args {
        match arg {
            ConstructorArg::Positional(value) => args.push(value),
            ConstructorArg::Keyword(name, value) => {
                kwargs.insert(name, value);
            },
        }
    }

    ESExpr::Constructor {
        name,
        args,
        kwargs,
    }
}

fn constructor_arg(input: &str) -> IResult<&str, ConstructorArg> {
    alt((
        map(separated_pair(
            preceded(skip_ws, identifier),
            preceded(skip_ws, char(':')),
            expr,
        ), |(name, value)| ConstructorArg::Keyword(name.to_owned(), value)),
        map(expr, ConstructorArg::Positional),
    ))(input)
}

fn null_atom(input: &str) -> IResult<&str, ESExpr> {
    map(
        tuple((
            skip_ws,
            tag("#null"),
            digit1,
            not(alphanumeric1)
        )),
        |(_, _, n, _)| ESExpr::Null(BigUint::from_str(n).unwrap())
    )(input)
}

fn atom(expr: ESExpr, s: &'static str) -> impl Fn(&str) -> IResult<&str, ESExpr> {
    move |input| {
        value(
            expr.clone(),
            preceded(
                skip_ws,
                terminated(tag(s), not(alphanumeric1))
            ),
        )(input)
    }
}

pub fn expr(input: &str) -> IResult<&str, ESExpr> {
    alt((
        float,
        map(integer, ESExpr::Int),
        map(string, ESExpr::Str),
        map(binary, ESExpr::Binary),
        atom(ESExpr::Bool(true), "#true"),
        atom(ESExpr::Bool(false), "#false"),
        null_atom,
        atom(ESExpr::Null(BigUint::ZERO), "#null"),
        constructor,
    ))(input)
}

pub fn expr_file(input: &str) -> IResult<&str, ESExpr> {
    terminated(terminated(expr, skip_ws),  eof)(input)
}


