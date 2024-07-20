
use hexfloat2::{HexFloat32, HexFloat64};
use logos::Logos;
use num_bigint::{BigInt, Sign};

use std::ops::Range;

#[derive(Debug, Clone, PartialEq)]
pub enum LexErrorType {
    UnexpectedToken,
    UnterminatedString,
    UnterminatedIdentifierString,

    InvalidUnicodeCodePoint(u32),
}

impl Default for LexErrorType {
    fn default() -> Self {
        LexErrorType::UnexpectedToken
    }
}

#[derive(Debug, Clone)]
pub struct LexError {
    pub error_type: LexErrorType,
    pub position: Range<usize>,
}



#[derive(Debug, Clone, Logos)]
#[logos(error = LexErrorType)]
#[logos(skip r"\s+")]
pub enum SimpleToken<'input> {
    #[regex(r"[a-z]([a-z0-9\\-]*[a-z0-9])?")]
    Identifier(&'input str),

    #[regex(r"[+\-]?0", |_| BigInt::ZERO)]
    #[regex(r"[+\-]?[1-9][0-9]*", |x| x.slice().parse::<BigInt>().unwrap())]
    #[regex(r"[+\-]?0[xX][0-9a-fA-F]+", |x| parse_int_base(x.slice(), 16))]
    Integer(BigInt),

    #[regex(r"[+\-]?[0-9]+\.[0-9]+([eE][+\-]?[0-9]+)?[fF]", |x| *x.slice().trim_end_matches("f").trim_end_matches("F").parse::<HexFloat32>().unwrap())]
    #[regex(r"[+\-]?0[xX][a-fA-F0-9]+\.[a-fA-F0-9]+[pP][+\-]?[0-9]+[fF]", |x| *x.slice().trim_end_matches("f").trim_end_matches("F").parse::<HexFloat32>().unwrap())]
    Float32(f32),

    #[regex(r"[+\-]?[0-9]+\.[0-9]+([eE][+\-]?[0-9]+)?[dD]?", |x| *x.slice().trim_end_matches("d").trim_end_matches("D").parse::<HexFloat64>().unwrap())]
    #[regex(r"[+\-]?0[xX][a-fA-F0-9]+\.[a-fA-F0-9]+[pP][+\-]?[0-9]+[dD]?", |x| *x.slice().trim_end_matches("d").trim_end_matches("D").parse::<HexFloat64>().unwrap())]
    Float64(f64),

    #[token("#true")]
    TrueAtom,

    #[token("#false")]
    FalseAtom,

    #[token("#null")]
    NullAtom,

    #[token(":")]
    Colon,

    #[token("(")]
    OpenParen,

    #[token(")")]
    CloseParen,

    #[token("\"")]
    StringStart,

    #[token("'")]
    IdentifierStringStart,
}


#[derive(Debug, Clone, Logos)]
#[logos(error = LexErrorType)]
pub enum StringPartToken<'input> {
    #[regex("[^\"\\\\]+")]
    Text(&'input str),

    #[token("\\f", |_| '\x0C')]
    #[token("\\n", |_| '\n')]
    #[token("\\r", |_| '\r')]
    #[token("\\t", |_| '\t')]
    #[token("\\\\", |_| '\\')]
    #[token("\\'", |_| '\'')]
    #[token("\\\"", |_| '"')]
    #[regex(r"\\u\{[a-fA-F0-9]+\}", |x| {
        let s = x.slice();
        let s = &s[3..x.slice().len() - 1];
        let codepoint = u32::from_str_radix(s, 16).unwrap();
        char::from_u32(codepoint).ok_or_else(|| LexErrorType::InvalidUnicodeCodePoint(codepoint))
    })]
    Escape(char),

    #[token("\"")]
    StringEnd,
}

#[derive(Debug, Clone, Logos)]
#[logos(error = LexErrorType)]
pub enum IdentifierStringPartToken<'input> {
    #[regex("[^'\\\\]+")]
    Text(&'input str),

    #[token("\\f", |_| '\x0C')]
    #[token("\\n", |_| '\n')]
    #[token("\\r", |_| '\r')]
    #[token("\\t", |_| '\t')]
    #[token("\\\\", |_| '\\')]
    #[token("\\'", |_| '\'')]
    #[token("\\\"", |_| '"')]
    #[regex(r"\\u\{[a-fA-F0-9]+\}", |x| {
        let s = x.slice();
        let s = &s[3..x.slice().len() - 1];
        let codepoint = s.parse::<u32>().unwrap();
        char::from_u32(codepoint).ok_or_else(|| LexErrorType::InvalidUnicodeCodePoint(codepoint))
    })]
    Escape(char),

    #[token("'")]
    StringEnd,
}

#[derive(Debug, Clone)]
pub enum Token<'input> {
    Simple(SimpleToken<'input>),
    StringPart(StringPartToken<'input>),
    IdentifierStringPart(IdentifierStringPartToken<'input>),
}

enum LexerImpl<'input> {
	Normal(logos::Lexer<'input, SimpleToken<'input>>),
	InString(logos::Lexer<'input, StringPartToken<'input>>),
    InIdentifierString(logos::Lexer<'input, IdentifierStringPartToken<'input>>),
}


pub(super) struct Lexer<'input> {
	inner: LexerImpl<'input>,
}

impl<'input> Lexer<'input> {
	pub fn new(s: &'input str) -> Self {
		Self {
			inner: LexerImpl::Normal(logos::Lexer::new(s)),
		}
	}

	fn as_normal_mode(&mut self) {
		let mut lexer_impl: LexerImpl<'input> = LexerImpl::Normal(SimpleToken::lexer(""));
		std::mem::swap(&mut lexer_impl, &mut self.inner);

		lexer_impl = match lexer_impl {
			LexerImpl::Normal(normal) => LexerImpl::Normal(normal),
			LexerImpl::InString(in_string) => LexerImpl::Normal(in_string.morph()),
            LexerImpl::InIdentifierString(in_id_string) => LexerImpl::Normal(in_id_string.morph()),
		};

		std::mem::swap(&mut lexer_impl, &mut self.inner);
	}

	fn as_in_string_mode(&mut self) {
		let mut lexer_impl: LexerImpl<'input> = LexerImpl::Normal(SimpleToken::lexer(""));
		std::mem::swap(&mut lexer_impl, &mut self.inner);

		lexer_impl = match lexer_impl {
			LexerImpl::Normal(normal) => LexerImpl::InString(normal.morph()),
			LexerImpl::InString(in_string) => LexerImpl::InString(in_string),
            LexerImpl::InIdentifierString(in_id_string) => LexerImpl::InString(in_id_string.morph()),
		};

		std::mem::swap(&mut lexer_impl, &mut self.inner);
	}

	fn as_in_id_string_mode(&mut self) {
		let mut lexer_impl: LexerImpl<'input> = LexerImpl::Normal(SimpleToken::lexer(""));
		std::mem::swap(&mut lexer_impl, &mut self.inner);

		lexer_impl = match lexer_impl {
			LexerImpl::Normal(normal) => LexerImpl::InIdentifierString(normal.morph()),
			LexerImpl::InString(in_string) => LexerImpl::InIdentifierString(in_string.morph()),
            LexerImpl::InIdentifierString(in_id_string) => LexerImpl::InIdentifierString(in_id_string),
		};

		std::mem::swap(&mut lexer_impl, &mut self.inner);
	}
}

impl<'input> Iterator for Lexer<'input> {
	type Item = Result<(usize, Token<'input>, usize), LexError>;

	fn next(&mut self) -> Option<Self::Item> {
		match &mut self.inner {
			LexerImpl::Normal(normal) => {
				match normal.next() {
					Some(Ok(token)) => {
                        let position = normal.span();

                        match token {
                            SimpleToken::StringStart => self.as_in_string_mode(),
                            SimpleToken::IdentifierStringStart => self.as_in_id_string_mode(),
                            _ => {},
                        }

						Some(Ok((position.start, Token::Simple(token), position.end)))
					},
					Some(Err(e)) => Some(Err(LexError { error_type: e, position: normal.span() })),
					None => None,
				}
			},

			LexerImpl::InString(instr) => {
				match instr.next() {
					Some(Ok(token)) => {
                        let position = instr.span();

                        if let StringPartToken::StringEnd = token {
                            self.as_normal_mode();
                        }

						Some(Ok((position.start, Token::StringPart(token), position.end)))
					}
					Some(Err(e)) => Some(Err(LexError { error_type: e, position: instr.span() })),
					None => Some(Err(LexError { error_type: LexErrorType::UnterminatedString, position: instr.span() })),
				}
			}

            LexerImpl::InIdentifierString(in_id_str) => {
                match in_id_str.next() {
                    Some(Ok(token)) => {
                        let position = in_id_str.span();

                        if let IdentifierStringPartToken::StringEnd = token {
                            self.as_normal_mode();
                        }

						Some(Ok((position.start, Token::IdentifierStringPart(token), position.end)))
                    },
                    Some(Err(e)) => Some(Err(LexError { error_type: e, position: in_id_str.span() })),
                    None => Some(Err(LexError { error_type: LexErrorType::UnterminatedIdentifierString, position: in_id_str.span() })),
                }
            },
		}
	}
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



