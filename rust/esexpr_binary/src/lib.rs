
use itertools::Itertools;
use num_bigint::{BigInt, BigUint, Sign};

use derive_more::From;

use std::{borrow::Cow, collections::HashMap, io::{Read, Write}};

use esexpr::{ESExpr, ESExprCodec};

#[derive(From, Debug)]
pub enum ParseError {
    #[from(ignore)]
    InvalidTokenByte(u8),

    #[from(ignore)]
    InvalidStringTableIndex,

    #[from(ignore)]
    InvalidLength,

    #[from(ignore)]
    UnexpectedKeywordToken,

    #[from(ignore)]
    UnexpectedConstructorEnd,

    #[from(ignore)]
    UnexpectedEndOfFile,

    #[from(ignore)]
    InvalidStringPool(esexpr::DecodeError),

    IOError(std::io::Error),
    Utf8Error(std::str::Utf8Error),

}

enum VarIntTag {
    ConstructorStart,
    NonNegIntValue,
    NegIntValue,
    StringLengthValue,
    StringPoolValue,
    BytesLengthValue,
    KeywordArgument,
}

enum ExprToken {
    ConstructorStart(usize),
    ConstructorStartKnown(&'static str),
    ConstructorEnd,
    Keyword(usize),
    IntValue(BigInt),
    StringValue(String),
    StringPoolValue(usize),
    BinaryValue(Vec<u8>),
    Float32Value(f32),
    Float64Value(f64),
    BooleanValue(bool),
    NullValue,
}


const TAG_VARINT_MASK: u8 = 0xE0;
const TAG_VARINT_CONSTRUCTOR_START: u8 = 0x00;
const TAG_VARINT_NON_NEG_INT: u8 = 0x20;
const TAG_VARINT_NEG_INT: u8 = 0x40;
const TAG_VARINT_STRING_LENGTH: u8 = 0x60;
const TAG_VARINT_STRING_POOL: u8 = 0x80;
const TAG_VARINT_BYTES_LENGTH: u8 = 0xA0;
const TAG_VARINT_KEYWORD: u8 = 0xC0;

const TAG_CONSTRUCTOR_END: u8 = 0xE0;
const TAG_TRUE: u8 = 0xE1;
const TAG_FALSE: u8 = 0xE2;
const TAG_NULL: u8 = 0xE3;
const TAG_FLOAT32: u8 = 0xE4;
const TAG_FLOAT64: u8 = 0xE5;
const TAG_CONSTRUCTOR_START_STRING_TABLE: u8 = 0xE6;
const TAG_CONSTRUCTOR_START_LIST: u8 = 0xE7;



struct TokenReader<R> {
    read: R,
}

impl <R: Read> Iterator for TokenReader<R> {
    type Item = Result<ExprToken, ParseError>;

    fn next(&mut self) -> Option<Self::Item> {
        read_token_impl(self).transpose()
    }
}

fn read_token_impl<R: Read>(reader: &mut TokenReader<R>) -> Result<Option<ExprToken>, ParseError> {
    let mut b: [u8; 1] = [0];

    if reader.read.read(&mut b)? == 0 {
        return Ok(None);
    }

    let b: u8 = b[0];

    println!("Reading tag: {:02x}", b);

    Ok(Some(
        if (b & TAG_VARINT_MASK) == TAG_VARINT_MASK {
            match b {
                TAG_CONSTRUCTOR_END => ExprToken::ConstructorEnd,
                TAG_TRUE => ExprToken::BooleanValue(true),
                TAG_FALSE => ExprToken::BooleanValue(false),
                TAG_NULL => ExprToken::NullValue,
                TAG_FLOAT32 => {
                    let buffer: [u8; 4] = read_bytes(reader)?;
                    ExprToken::Float32Value(f32::from_le_bytes(buffer))
                },
                TAG_FLOAT64 => {
                    let buffer: [u8; 8] = read_bytes(reader)?;
                    ExprToken::Float64Value(f64::from_le_bytes(buffer))
                },
                TAG_CONSTRUCTOR_START_STRING_TABLE => ExprToken::ConstructorStartKnown("string-table"),
                TAG_CONSTRUCTOR_START_LIST => ExprToken::ConstructorStartKnown("list"),
                _ => {
                    return Err(ParseError::InvalidTokenByte(b));
                },
            }
        }
        else {
            let tag = match b & TAG_VARINT_MASK {
                TAG_VARINT_CONSTRUCTOR_START => VarIntTag::ConstructorStart,
                TAG_VARINT_NON_NEG_INT => VarIntTag::NonNegIntValue,
                TAG_VARINT_NEG_INT => VarIntTag::NegIntValue,
                TAG_VARINT_STRING_LENGTH => VarIntTag::StringLengthValue,
                TAG_VARINT_STRING_POOL => VarIntTag::StringPoolValue,
                TAG_VARINT_BYTES_LENGTH => VarIntTag::BytesLengthValue,
                TAG_VARINT_KEYWORD => VarIntTag::KeywordArgument,
                _ => panic!("Should not be reachable"),
            };

            let mut n = read_int(reader, b)?;

            println!("n: {}", n);

            match tag {
                VarIntTag::ConstructorStart => ExprToken::ConstructorStart(get_string_table_index(n)?),
                VarIntTag::NonNegIntValue => ExprToken::IntValue(BigInt::from_biguint(Sign::Plus, n)),
                VarIntTag::NegIntValue => {
                    n += 1u32;
                    ExprToken::IntValue(BigInt::from_biguint(Sign::Minus, n))
                },
                VarIntTag::StringLengthValue => {
                    let len = get_length(n)?;
                    let mut buff = vec![0u8; len];
                    reader.read.read_exact(&mut buff)?;
                    ExprToken::StringValue(std::str::from_utf8(&buff)?.to_owned())
                },
                VarIntTag::StringPoolValue => ExprToken::StringPoolValue(get_string_table_index(n)?),
                VarIntTag::BytesLengthValue => {
                    let len = get_length(n)?;
                    let mut buff = vec![0u8; len];
                    reader.read.read_exact(&mut buff)?;
                    ExprToken::BinaryValue(buff)
                },
                VarIntTag::KeywordArgument => ExprToken::Keyword(get_string_table_index(n)?),
            }
        }
    ))

}

fn read_int<R: Read>(reader: &mut TokenReader<R>, initial: u8) -> Result<BigUint, ParseError> {
    let mut current = initial & 0x0F;
    let mut bit_offset = 4;
    let mut has_next = (initial & 0x10) == 0x10;

    let mut buffer = Vec::new();

    while has_next {
        let b = read_byte(reader)?;

        has_next = (b & 0x80) == 0x80;

        let value = b & 0x7F;
        let low = value << bit_offset;
        let high = if bit_offset > 1 { value >> (8 - bit_offset) } else { 0 };

        eprintln!("current = {:02x}, has_next = {}, bit_offset = {}, value = {:02x}, low = {:02x}, high = {:02x}", current, has_next, bit_offset, value, low, high);


        current |= low;
        bit_offset += 7;
        if bit_offset >= 8 {
            bit_offset -= 8;
            buffer.push(current);
            current = high;
        }
    }

    eprintln!("current = {:02x}, bit_offset = {}", current, bit_offset);

    if bit_offset > 0 {
        buffer.push(current);
    }

    eprintln!("buffer = {:?}", buffer);
    
    Ok(BigUint::from_bytes_le(&buffer))
}

fn read_bytes<R: Read, const N: usize>(reader: &mut TokenReader<R>) -> Result<[u8; N], std::io::Error> {
    let mut b: [u8; N] = [0; N];
    reader.read.read_exact(&mut b)?;
    Ok(b)
}

fn read_byte<R: Read>(reader: &mut TokenReader<R>) -> Result<u8, std::io::Error> {
    Ok(read_bytes::<R, 1>(reader)?[0])
}

fn get_string_table_index(i: BigUint) -> Result<usize, ParseError> {
    i.try_into().map_err(|_| ParseError::InvalidStringTableIndex)
}

fn get_length(i: BigUint) -> Result<usize, ParseError> {
    i.try_into().map_err(|_| ParseError::InvalidLength)
}


struct ExprParser<'a, S, I> where [S]: ToOwned<Owned = Vec<S>> {
    string_pool: Cow<'a, [S]>,
    iter: I,
}

impl <'a, S: AsRef<str>, I: Iterator<Item=Result<ExprToken, ParseError>>> ExprParser<'a, S, I> where [S]: ToOwned<Owned = Vec<S>> {
    fn try_read_next_expr(&mut self) -> Result<Option<ESExpr>, ParseError> {
        let Some(token) = self.iter.next().transpose()? else {
            return Ok(None);
        };
    
        self.read_expr_with(token).map(Some)
    }

    fn read_next_expr(&mut self) -> Result<ESExpr, ParseError> {
        self.try_read_next_expr()?.ok_or(ParseError::UnexpectedEndOfFile)
    }
    
    fn read_expr_with(&mut self, token: ExprToken) -> Result<ESExpr, ParseError> {
        match token {
            ExprToken::ConstructorStart(index) => {
                let name = self.get_string(index)?;
                self.read_expr_constructor(name)
            },
            ExprToken::ConstructorStartKnown(name) => {
                self.read_expr_constructor(name.to_owned())
            }
            ExprToken::ConstructorEnd => Err(ParseError::UnexpectedConstructorEnd),
            ExprToken::Keyword(_) => Err(ParseError::UnexpectedKeywordToken),
            ExprToken::IntValue(i) => Ok(ESExpr::Int(i)),
            ExprToken::StringValue(s) => Ok(ESExpr::Str(s)),
            ExprToken::StringPoolValue(index) => Ok(ESExpr::Str(self.get_string(index)?)),
            ExprToken::BinaryValue(b) => Ok(ESExpr::Binary(b)),
            ExprToken::Float32Value(f) => Ok(ESExpr::Float32(f)),
            ExprToken::Float64Value(d) => Ok(ESExpr::Float64(d)),
            ExprToken::BooleanValue(b) => Ok(ESExpr::Bool(b)),
            ExprToken::NullValue => Ok(ESExpr::Null),
        }
    }

    fn read_expr_constructor(&mut self, name: String) -> Result<ESExpr, ParseError> {
        let mut args = Vec::new();
        let mut kwargs = HashMap::new();

        loop {
            let token = self.iter.next().transpose()?.ok_or(ParseError::UnexpectedEndOfFile)?;

            match token {
                ExprToken::ConstructorEnd => break,
                ExprToken::Keyword(index) => {
                    let kw = self.get_string(index)?;
                    let value = self.read_next_expr()?;
                    kwargs.insert(kw, value);
                },

                _ => args.push(self.read_expr_with(token)?),
            }
        }

        Ok(ESExpr::Constructor { name, args, kwargs })

    }

    fn get_string(&self, i: usize) -> Result<String, ParseError> {
        self.string_pool.get(i)
            .map(|s| s.as_ref().to_owned())
            .ok_or(ParseError::InvalidStringTableIndex)
    }
}

impl <'a, S: AsRef<str>, I: Iterator<Item=Result<ExprToken, ParseError>>> Iterator for ExprParser<'a, S, I> where [S]: ToOwned<Owned = Vec<S>> {
    type Item = Result<ESExpr, ParseError>;

    fn next(&mut self) -> Option<Self::Item> {
        self.try_read_next_expr().transpose()
    }
}

pub fn parse<'a, F: Read + 'a, S: AsRef<str>>(f: F, string_pool: &'a [S]) -> impl Iterator<Item=Result<ESExpr, ParseError>> + 'a where [S]: ToOwned<Owned = Vec<S>> {
    ExprParser {
        iter: TokenReader { read: f },
        string_pool: Cow::Borrowed(string_pool),
    }
}

pub fn parse_embedded_string_pool<'a, F: Read + 'a>(f: F) -> Result<impl Iterator<Item=Result<ESExpr, ParseError>> + 'a, ParseError> {
    let mut parser = ExprParser {
        iter: TokenReader { read: f },
        string_pool: Cow::Owned(Vec::new()),
    };

    let Some(sp) = parser.next() else { return Err(ParseError::UnexpectedEndOfFile) };
    let sp = sp?;

    println!("{:?}", sp);

    let sp = FixedStringPool::decode_esexpr(sp).map_err(ParseError::InvalidStringPool)?;

    parser.string_pool = Cow::Owned(sp.strings);

    Ok(parser)
}


#[derive(From, Debug)]
pub enum GeneratorError {
    #[from(ignore)]
    StringNotInStringPool,

    IOError(std::io::Error),
}


pub trait StringPool {
    fn lookup(&mut self, s: &str) -> Option<usize>;
}


struct ExprGenerator<'a, SP, W> {
    out: &'a mut W,
    string_pool: &'a mut SP,
}

impl <'a, SP: StringPool, W: Write> ExprGenerator<'a, SP, W> {
    fn generate_expr(&mut self, expr: &ESExpr) -> Result<(), GeneratorError> {
        eprintln!("{:?}", expr);
        match expr {
            ESExpr::Constructor { name, args, kwargs } => {
                match name.as_str() {
                    "string-table" => self.write(TAG_CONSTRUCTOR_START_STRING_TABLE)?,
                    "list" => self.write(TAG_CONSTRUCTOR_START_LIST)?,
                    _ => {
                        let index = self.get_string_pool_index(&name)?;
                        self.write_int_tag(TAG_VARINT_CONSTRUCTOR_START, &BigUint::from(index))?;
                    }
                }

                for arg in args {
                    self.generate_expr(arg)?;
                }

                for (kw, value) in kwargs {
                    let index = self.get_string_pool_index(&kw)?;
                    self.write_int_tag(TAG_VARINT_KEYWORD, &BigUint::from(index))?;
                    self.generate_expr(value)?;
                }

                self.write(TAG_CONSTRUCTOR_END)?;
            },
            ESExpr::Bool(true) => {
                self.write(TAG_TRUE)?;
            },
            ESExpr::Bool(false) => {
                self.write(TAG_FALSE)?;
            },
            ESExpr::Int(i) => {
                let (sign, mut magnitude) = i.clone().into_parts();

                match sign {
                    Sign::NoSign | Sign::Plus => {
                        self.write_int_tag(TAG_VARINT_NON_NEG_INT, &magnitude)?;
                    },

                    Sign::Minus => {
                        magnitude -= 1usize;
                        self.write_int_tag(TAG_VARINT_NEG_INT, &magnitude)?;
                    },
                }
            },
            ESExpr::Str(s) => {
                self.write_int_tag(TAG_VARINT_STRING_LENGTH, &BigUint::from(s.len()))?;
                self.out.write_all(&s.as_bytes())?;
            },
            ESExpr::Binary(b) => {
                self.write_int_tag(TAG_VARINT_BYTES_LENGTH, &BigUint::from(b.len()))?;
                self.out.write_all(&b)?;
            },
            ESExpr::Float32(f) => {
                self.write(TAG_FLOAT32)?;
                self.out.write_all(&f32::to_le_bytes(*f))?;
            },
            ESExpr::Float64(d) => {
                self.write(TAG_FLOAT64)?;
                self.out.write_all(&f64::to_le_bytes(*d))?;
            },
            ESExpr::Null => {
                self.write(TAG_NULL)?;
            },
        }

        Ok(())
    }

    fn get_string_pool_index(&mut self, s: &str) -> Result<usize, GeneratorError> {
        self.string_pool.lookup(s).ok_or(GeneratorError::StringNotInStringPool)
    }

    fn write_int_tag(&mut self, tag: u8, i: &BigUint) -> Result<(), GeneratorError> {
        let buff = i.to_bytes_le();

        eprintln!("write {} {:?}", tag, buff);

        let b0 = *buff.get(0).unwrap_or(&0);
        let mut current = tag | (b0 & 0x0F);
        if buff.len() < 2 && (b0 & 0xF0) == 0 {
            eprintln!("writing {:02x}", current);
            self.write(current)?;
            return Ok(());
        }

        current |= 0x10;
        eprintln!("writing {:02x}", current);
        self.write(current)?;

        current = b0 >> 4;
        let mut bit_index = 4;

        for (i, b) in buff.iter().copied().enumerate().skip(1) {            
            let mut bit_index2 = 0;
            while bit_index2 < 8 {
                let written_bits = std::cmp::min(7 - bit_index, 8 - bit_index2);
                eprintln!("write b = {:02x}, current = {:02x}, bit_index = {}, bit_index2 = {}, written_bits = {}", b, current, bit_index, bit_index2, written_bits);
                current |= ((b >> bit_index2) & 0x7F) << bit_index;

                
                bit_index += written_bits;
                bit_index2 += written_bits;
                if bit_index >= 7 {
                    if i < buff.len() - 1 || (bit_index2 < 8 && (b >> bit_index2) != 0) {
                        current |= 0x80;
                    }

                    eprintln!("writing {:02x}", current);

                    self.write(current)?;
                    bit_index = 0;
                    current = 0;
                }
            }
        }

        if current != 0 {
            self.write(current)?;
        }

        Ok(())
    }

    fn write(&mut self, b: u8) -> Result<(), GeneratorError> {
        Ok(self.out.write_all(std::slice::from_ref(&b))?)
    }
}

pub fn generate<SP: StringPool, W: Write>(out: &mut W, string_pool: &mut SP, expr: &ESExpr) -> Result<(), GeneratorError> {
    let mut generator = ExprGenerator {
        out,
        string_pool,
    };

    generator.generate_expr(expr)
}

pub struct StringPoolBuilder {
    strings: HashMap<String, usize>,
}

impl StringPoolBuilder {
    pub fn new() -> Self {
        Self {
            strings: HashMap::new(),
        }
    }

    pub fn add(&mut self, expr: &ESExpr) {
        let mut generator = ExprGenerator {
            out: &mut std::io::sink(),
            string_pool: &mut StringPoolBuilderAdapter(self),
        };
        generator.generate_expr(expr).unwrap();
    }

    pub fn into_fixed_string_pool(self) -> FixedStringPool {
        FixedStringPool {
            strings: self.strings
                .into_iter()
                .sorted_by_key(|(_, v)| *v)
                .map(|(k, _)| k)
                .collect(),
        }
    }
}

pub struct StringPoolBuilderAdapter<'a>(&'a mut StringPoolBuilder);

impl <'a> StringPool for StringPoolBuilderAdapter<'a> {
    fn lookup(&mut self, s: &str) -> Option<usize> {
        let count = self.0.strings.entry(s.to_owned()).or_default();
        *count += 1;
        Some(0)
    }
}

#[derive(ESExprCodec, Debug, PartialEq, Clone)]
#[constructor = "string-table"]
pub struct FixedStringPool {
    #[vararg]
    pub strings: Vec<String>,
}

impl StringPool for FixedStringPool {
    fn lookup(&mut self, s: &str) -> Option<usize> {
        self.strings.iter().position(|a| a == s)
    }
}


#[cfg(test)]
mod test {
    use std::str::FromStr;

    use num_bigint::BigUint;

    use crate::*;

    #[test]
    fn encode_int() {
        fn check(n: &str, enc: &[u8]) {
            let n = BigUint::from_str(n).unwrap();
            
            let mut buff: Vec<u8> = Vec::new();
            let mut gen = ExprGenerator {
                out: &mut buff,
                string_pool: &mut FixedStringPool { strings: vec![], },
            };

            gen.write_int_tag(TAG_VARINT_NON_NEG_INT, &n).unwrap();

            assert_eq!(enc, &buff);

            let mut reader = TokenReader {
                read: &enc[1..],
            };
            let m = read_int(&mut reader, enc[0]).unwrap();
            assert_eq!(n, m);
        }

        check("4", &[0x24]);
        check("9223372036854775807", &[0x3F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x07]);
        check("18446744073709551615", &[0x3F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F]);
        check("12345678901234567890", &[0x32, 0xAD, 0xE1, 0xC7, 0xF5, 0x8C, 0xD3, 0xD2, 0xDA, 0x0A]);
        check("98765432109876543210", &[0x3A, 0xEE, 0xCF, 0xC9, 0xF2, 0xB8, 0x9A, 0x95, 0xD5, 0x55]);
    }
}

