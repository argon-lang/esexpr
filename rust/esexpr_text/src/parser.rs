use std::collections::HashMap;

use esexpr::ESExpr;
use hexfloat2::{HexFloat32, HexFloat64};
use nom::{
    IResult,
    branch::alt,
    character::complete::{alphanumeric1, char, digit1, hex_digit1, multispace0, none_of, one_of},
    bytes::complete::{take_while, take_while1, tag, tag_no_case, escaped_transform},
    combinator::{cut, eof, map, map_res, not, opt, peek, recognize, value},
    multi::many0,
    sequence::{delimited, pair, preceded, separated_pair, terminated, tuple},
};
use num_bigint::{BigInt, Sign};




#[derive(Debug, Clone, PartialEq)]
pub enum LexErrorType {
    UnexpectedToken,
    UnterminatedString,
    UnterminatedIdentifierString,

    InvalidUnicodeCodePoint(u32),
}


fn is_alpha(c: char) -> bool {
    c.is_ascii_lowercase()
}

fn is_alphanum(c: char) -> bool {
    c.is_ascii_lowercase() || c.is_ascii_digit()
}


pub fn simple_identifier(input: &str) -> IResult<&str, &str> {
    preceded(
        multispace0,
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
        preceded(multispace0, string_impl('\'', "'\\")),
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
    preceded(multispace0, alt((float_decimal, float_hex)))(input)
}


pub fn integer(input: &str) -> IResult<&str, BigInt> {
    preceded(multispace0, alt((
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
        multispace0,
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


enum ConstructorArg {
    Positional(ESExpr),
    Keyword(String, ESExpr),
}

pub fn constructor(input: &str) -> IResult<&str, ESExpr> {
    map(delimited(
        preceded(multispace0, char('(')),
        pair(
            identifier,
            many0(constructor_arg),
        ),
        preceded(multispace0, char(')')),
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
            preceded(multispace0, identifier),
            preceded(multispace0, char(':')),
            expr,
        ), |(name, value)| ConstructorArg::Keyword(name.to_owned(), value)),
        map(expr, ConstructorArg::Positional),
    ))(input)
}

pub fn expr(input: &str) -> IResult<&str, ESExpr> {
    alt((
        float,
        map(integer, ESExpr::Int),
        map(string, ESExpr::Str),
        value(ESExpr::Bool(true), preceded(multispace0, tag("#true"))),
        value(ESExpr::Bool(false), preceded(multispace0, tag("#false"))),
        value(ESExpr::Null, preceded(multispace0, tag("#null"))),
        constructor,
    ))(input)
}

pub fn expr_file(input: &str) -> IResult<&str, ESExpr> {
    terminated(terminated(expr, multispace0),  eof)(input)
}


