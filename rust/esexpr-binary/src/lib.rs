mod append_only_string_list;

use num_bigint::{BigInt, BigUint, Sign};

use derive_more::From;

use std::{collections::HashMap, io::{Read, Write}};
use std::borrow::Cow;
use esexpr::{ESExpr, ESExprCodec};
use crate::append_only_string_list::AppendOnlyStringList;

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
    NullValue(BigUint),
    AppendStringTable,
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
const TAG_NULL0: u8 = 0xE3;
const TAG_FLOAT32: u8 = 0xE4;
const TAG_FLOAT64: u8 = 0xE5;
const TAG_CONSTRUCTOR_START_STRING_TABLE: u8 = 0xE6;
const TAG_CONSTRUCTOR_START_LIST: u8 = 0xE7;
const TAG_NULL1: u8 = 0xE8;
const TAG_NULL2: u8 = 0xE9;
const TAG_NULLN: u8 = 0xEA;
const TAG_APPEND_STRING_TABLE: u8 = 0xEB;


enum ExprPlus<'a> {
    Expr(ESExpr<'a>),
    Keyword(usize),
    ConstructorEnd,
    AppendedToStringTable,
    EndOfFile,
}


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

    Ok(Some(
        if (b & TAG_VARINT_MASK) == TAG_VARINT_MASK {
            match b {
                TAG_CONSTRUCTOR_END => ExprToken::ConstructorEnd,
                TAG_TRUE => ExprToken::BooleanValue(true),
                TAG_FALSE => ExprToken::BooleanValue(false),
                TAG_NULL0 => ExprToken::NullValue(BigUint::ZERO),
                TAG_NULL1 => ExprToken::NullValue(BigUint::from(1u32)),
                TAG_NULL2 => ExprToken::NullValue(BigUint::from(2u32)),
                TAG_NULLN => {
                    let n = read_int_full(reader)?;
                    ExprToken::NullValue(n + 3u32)
                },
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
                TAG_APPEND_STRING_TABLE => ExprToken::AppendStringTable,
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
    let current = initial & 0x0F;
    let bit_offset = 4;
    let has_next = (initial & 0x10) == 0x10;

    read_int_rest(reader, current, bit_offset, has_next)
}

fn read_int_full<R: Read>(reader: &mut TokenReader<R>) -> Result<BigUint, ParseError> {
    let current = 0;
    let bit_offset = 0;
    let has_next = true;

    read_int_rest(reader, current, bit_offset, has_next)
}

fn read_int_rest<R: Read>(reader: &mut TokenReader<R>, mut current: u8, mut bit_offset: i32, mut has_next: bool) -> Result<BigUint, ParseError> {
    let mut buffer = Vec::new();

    while has_next {
        let b = read_byte(reader)?;

        has_next = (b & 0x80) == 0x80;

        let value = b & 0x7F;
        let low = value << bit_offset;
        let high = if bit_offset > 1 { value >> (8 - bit_offset) } else { 0 };
        


        current |= low;
        bit_offset += 7;
        if bit_offset >= 8 {
            bit_offset -= 8;
            buffer.push(current);
            current = high;
        }
    }

    if bit_offset > 0 {
        buffer.push(current);
    }
    
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

pub trait ExprParser {
    fn try_read_next_expr<'a>(&'a mut self) -> Result<Option<ESExpr<'a>>, ParseError>;
    fn read_next_expr<'a>(&'a mut self) -> Result<ESExpr<'a>, ParseError>;
    
    fn iter_static(&mut self) -> impl Iterator<Item=Result<ESExpr<'static>, ParseError>> {
        std::iter::from_fn(move || self.try_read_next_expr().map(|res| res.map(ESExpr::into_owned)).transpose())
    }
}



struct ExprParserImpl<I> {
    string_pool: AppendOnlyStringList,
    iter: I,
}

impl <I: Iterator<Item=Result<ExprToken, ParseError>>> ExprParser for ExprParserImpl<I> {
    fn try_read_next_expr<'a>(&'a mut self) -> Result<Option<ESExpr<'a>>, ParseError> {
        try_read_next_expr_impl(&mut self.iter, &self.string_pool)
    }

    fn read_next_expr<'a>(&'a mut self) -> Result<ESExpr<'a>, ParseError> {
        read_next_expr_impl(&mut self.iter, &self.string_pool)
    }
}

fn try_read_next_expr_impl<'a>(iter: &mut impl Iterator<Item=Result<ExprToken, ParseError>>, string_pool: &'a AppendOnlyStringList) -> Result<Option<ESExpr<'a>>, ParseError> {
    loop {
        return match read_expr_plus(iter, string_pool)? {
            ExprPlus::Expr(expr) => Ok(Some(expr)),
            ExprPlus::Keyword(_) => Err(ParseError::UnexpectedKeywordToken),
            ExprPlus::ConstructorEnd => Err(ParseError::UnexpectedConstructorEnd),
            ExprPlus::AppendedToStringTable => continue,
            ExprPlus::EndOfFile => Ok(None),
        }
    }
}

fn read_next_expr_impl<'a>(iter: &mut impl Iterator<Item=Result<ExprToken, ParseError>>, string_pool: &'a AppendOnlyStringList) -> Result<ESExpr<'a>, ParseError> {
    try_read_next_expr_impl(iter, string_pool)?.ok_or(ParseError::UnexpectedEndOfFile)
}

fn read_expr_plus<'a>(iter: &mut impl Iterator<Item=Result<ExprToken, ParseError>>, string_pool: &'a AppendOnlyStringList) -> Result<ExprPlus<'a>, ParseError> {
    let Some(token) = iter.next().transpose()? else {
        return Ok(ExprPlus::EndOfFile)
    };

    Ok(ExprPlus::Expr(match token {
        ExprToken::ConstructorStart(index) => {
            let name = get_string(string_pool, index)?;
            read_expr_constructor(iter, string_pool, name)?
        },
        ExprToken::ConstructorStartKnown(name) => {
            read_expr_constructor(iter, string_pool, name)?
        }
        ExprToken::ConstructorEnd => return Ok(ExprPlus::ConstructorEnd),
        ExprToken::Keyword(index) => return Ok(ExprPlus::Keyword(index)),
        ExprToken::IntValue(i) => ESExpr::Int(Cow::Owned(i)),
        ExprToken::StringValue(s) => ESExpr::Str(Cow::Owned(s)),
        ExprToken::StringPoolValue(index) => ESExpr::Str(Cow::Borrowed(get_string(string_pool, index)?)),
        ExprToken::BinaryValue(b) => ESExpr::Binary(Cow::Owned(b)),
        ExprToken::Float32Value(f) => ESExpr::Float32(f),
        ExprToken::Float64Value(d) => ESExpr::Float64(d),
        ExprToken::BooleanValue(b) => ESExpr::Bool(b),
        ExprToken::NullValue(level) => ESExpr::Null(Cow::Owned(level)),
        ExprToken::AppendStringTable => {
            let new_string_table = read_next_expr_impl(iter, string_pool)?;
            let new_string_table = AppendedStringPool::decode_esexpr(new_string_table)
                .map_err(ParseError::InvalidStringPool)?;

            match new_string_table {
                AppendedStringPool::Fixed(mut fixed_string_pool) =>
                    string_pool.append(&mut fixed_string_pool.strings),

                AppendedStringPool::Single(s) =>
                    string_pool.push(s),
            }

            return Ok(ExprPlus::AppendedToStringTable)
        }
    }))
}
fn read_expr_constructor<'a>(iter: &mut impl Iterator<Item=Result<ExprToken, ParseError>>, string_pool: &'a AppendOnlyStringList, name: &'a str) -> Result<ESExpr<'a>, ParseError> {
    let mut args = Vec::new();
    let mut kwargs = HashMap::new();

    loop {
        match read_expr_plus(iter, string_pool)? {
            ExprPlus::Expr(expr) => args.push(expr),
            ExprPlus::Keyword(index) => {
                let kw = get_string(string_pool, index)?;
                let value = read_next_expr_impl(iter, string_pool)?;
                kwargs.insert(Cow::Borrowed(kw), value);
            },
            ExprPlus::ConstructorEnd => break,
            ExprPlus::AppendedToStringTable => continue,
            ExprPlus::EndOfFile => return Err(ParseError::UnexpectedEndOfFile),
        }
    }

    Ok(ESExpr::Constructor {
        name: Cow::Borrowed(name),
        args: Cow::Owned(args),
        kwargs: Cow::Owned(kwargs),
    })
}

fn get_string<'a>(string_pool: &'a AppendOnlyStringList, i: usize) -> Result<&'a str, ParseError> {
    string_pool.get(i)
        .ok_or(ParseError::InvalidStringTableIndex)
}

pub fn parse_existing_string_pool<'a, F: Read + 'a>(f: F, string_pool: Vec<String>) -> impl ExprParser + 'a {
    ExprParserImpl {
        iter: TokenReader { read: f },
        string_pool: AppendOnlyStringList::from(string_pool),
    }
}

pub fn parse<'a, F: Read + 'a>(f: F) -> impl ExprParser + 'a {
    parse_existing_string_pool(f, Vec::new())
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


pub struct ExprGenerator<'a, W> {
    out: &'a mut W,
    string_pool: Vec<String>,
}

impl <'a, W: Write> ExprGenerator<'a, W> {
    pub fn new(out: &'a mut W) -> Self {
        ExprGenerator {
            out,
            string_pool: Vec::new(),
        }
    }

    pub fn new_with_string_pool(out: &'a mut W, string_pool: Vec<String>) -> Self {
        ExprGenerator {
            out,
            string_pool,
        }
    }

    pub fn generate(&mut self, expr: &ESExpr) -> Result<(), GeneratorError> {
        let old_string_pool_end = self.string_pool.len();

        let mut generator = ExprGenerator {
            out: &mut std::io::sink(),
            string_pool: Vec::new(),
        };

        std::mem::swap(&mut self.string_pool, &mut generator.string_pool);
        // Dummy generator to catch new strings
        generator.generate_expr(expr)?;

        std::mem::swap(&mut self.string_pool, &mut generator.string_pool);

        match &self.string_pool[old_string_pool_end..] {
            [] => {},
            [ s ] => {
                Self::write_out(self.out, TAG_APPEND_STRING_TABLE)?;
                Self::write_string_expr(self.out, s.as_str())?;
            },
            new_strings => {
                Self::write_out(self.out, TAG_APPEND_STRING_TABLE)?;
                Self::write_out(self.out, TAG_CONSTRUCTOR_START_STRING_TABLE)?;
                for s in new_strings {
                    Self::write_string_expr(self.out, s)?;
                }
                Self::write_out(self.out, TAG_CONSTRUCTOR_END)?;
            }
        }

        self.generate_expr(expr)?;
        Ok(())
    }

    fn generate_expr(&mut self, expr: &ESExpr) -> Result<(), GeneratorError> {
        match expr {
            ESExpr::Constructor { name, args, kwargs } => {
                match &**name {
                    "string-table" => self.write(TAG_CONSTRUCTOR_START_STRING_TABLE)?,
                    "list" => self.write(TAG_CONSTRUCTOR_START_LIST)?,
                    _ => {
                        let index = self.get_string_pool_index(&name)?;
                        self.write_int_tag(TAG_VARINT_CONSTRUCTOR_START, &BigUint::from(index))?;
                    }
                }

                for arg in args.iter() {
                    self.generate_expr(arg)?;
                }

                for (kw, value) in kwargs.iter() {
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
                let (sign, mut magnitude) = i.as_ref().clone().into_parts();

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
            ESExpr::Null(level) => {
                if **level == BigUint::ZERO {
                    self.write(TAG_NULL0)?;
                }
                else if **level == BigUint::from(1u32) {
                    self.write(TAG_NULL1)?;
                }
                else if **level == BigUint::from(2u32) {
                    self.write(TAG_NULL2)?;
                }
                else {
                    self.write(TAG_NULLN)?;
                    Self::write_int_full(self.out, &(&**level - 3u32))?;
                }
            },
        }

        Ok(())
    }

    fn get_string_pool_index(&mut self, s: &str) -> Result<usize, GeneratorError> {
        if let Some(index) = self.string_pool.iter().position(|s2| s2 == s) {
            return Ok(index);
        }

        let index = self.string_pool.len();
        self.string_pool.push(s.to_owned());

        self.write(TAG_APPEND_STRING_TABLE)?;
        Self::write_string_expr(self.out, s)?;

        Ok(index)
    }

    fn write_int_tag(&mut self, tag: u8, i: &BigUint) -> Result<(), GeneratorError> {
        Self::write_int_tag_out(self.out, tag, i)
    }

    fn write_int_tag_out(out: &mut W, tag: u8, i: &BigUint) -> Result<(), GeneratorError> {
        let buff = i.to_bytes_le();

        let b0 = *buff.get(0).unwrap_or(&0);
        let mut current = tag | (b0 & 0x0F);
        if buff.len() < 2 && (b0 & 0xF0) == 0 {
            Self::write_out(out, current)?;
            return Ok(());
        }

        current |= 0x10;
        Self::write_out(out, current)?;

        current = b0 >> 4;
        let bit_index = 4;

        Self::write_int_rest(out, &buff[1..], current, bit_index)
    }

    fn write_int_full(out: &mut W, i: &BigUint) -> Result<(), GeneratorError> {
        if *i == BigUint::ZERO {
            Self::write_out(out, 0)?;
            return Ok(());
        }

        let buff = i.to_bytes_le();
        let current = 0;
        let bit_index = 0;

        Self::write_int_rest(out, &buff, current, bit_index)
    }

    fn write_int_rest(out: &mut W, buff: &[u8], mut current: u8, mut bit_index: i32) -> Result<(), GeneratorError> {
        for (i, b) in buff.iter().copied().enumerate() {            
            let mut bit_index2 = 0;
            while bit_index2 < 8 {
                let written_bits = std::cmp::min(7 - bit_index, 8 - bit_index2);
                current |= ((b >> bit_index2) & 0x7F) << bit_index;

                
                bit_index += written_bits;
                bit_index2 += written_bits;
                if bit_index >= 7 {
                    if i < buff.len() - 1 || (bit_index2 < 8 && (b >> bit_index2) != 0) {
                        current |= 0x80;
                    }

                    Self::write_out(out, current)?;
                    bit_index = 0;
                    current = 0;
                }
            }
        }

        if current != 0 {
            Self::write_out(out, current)?;
        }

        Ok(())
    }

    fn write_string_expr(out: &mut W, s: &str) -> Result<(), GeneratorError> {
        Self::write_int_tag_out(out, TAG_VARINT_STRING_LENGTH, &BigUint::from(s.len()))?;
        out.write_all(&s.as_bytes())?;
        Ok(())
    }

    fn write(&mut self, b: u8) -> Result<(), GeneratorError> {
        Self::write_out(self.out, b)
    }

    fn write_out(out: &mut W, b: u8) -> Result<(), GeneratorError> {
        Ok(out.write_all(std::slice::from_ref(&b))?)
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

#[derive(ESExprCodec, Debug, PartialEq, Clone)]
pub enum AppendedStringPool {
    #[inline_value]
    Fixed(FixedStringPool),

    #[inline_value]
    Single(String),
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
            let mut eg = ExprGenerator {
                out: &mut buff,
                string_pool: vec![],
            };

            eg.write_int_tag(TAG_VARINT_NON_NEG_INT, &n).unwrap();

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

