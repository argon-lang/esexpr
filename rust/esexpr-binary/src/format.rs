use alloc::string::String;
use alloc::vec::Vec;

use half::f16;
use num_bigint::{BigInt, BigUint};

pub enum VarIntTag {
	ConstructorStart,
	NonNegIntValue,
	NegIntValue,
	StringLengthValue,
	StringPoolValue,
	Array8LengthValue,
	KeywordArgument,
}

pub enum ExprToken {
	ConstructorStart(usize),
	ConstructorStartKnown(&'static str),
	ConstructorEnd,
	Keyword(usize),
	IntValue(BigInt),
	StringValue(String),
	StringPoolValue(usize),
	Array8Value(Vec<u8>),
	Array16Value(Vec<u16>),
	Array32Value(Vec<u32>),
	Array64Value(Vec<u64>),
	Array128Value(Vec<u128>),
	Float16Value(f16),
	Float32Value(f32),
	Float64Value(f64),
	BooleanValue(bool),
	NullValue(BigUint),
	AppendStringTable,
}

pub const TAG_VARINT_MASK: u8 = 0xE0;
pub const TAG_VARINT_CONSTRUCTOR_START: u8 = 0x00;
pub const TAG_VARINT_NON_NEG_INT: u8 = 0x20;
pub const TAG_VARINT_NEG_INT: u8 = 0x40;
pub const TAG_VARINT_STRING_LENGTH: u8 = 0x60;
pub const TAG_VARINT_STRING_POOL: u8 = 0x80;
pub const TAG_VARINT_ARRAY8_LENGTH: u8 = 0xA0;
pub const TAG_VARINT_KEYWORD: u8 = 0xC0;

pub const TAG_CONSTRUCTOR_END: u8 = 0xE0;
pub const TAG_TRUE: u8 = 0xE1;
pub const TAG_FALSE: u8 = 0xE2;
pub const TAG_NULL0: u8 = 0xE3;
pub const TAG_NULL1: u8 = 0xE8;
pub const TAG_NULL2: u8 = 0xE9;
pub const TAG_NULLN: u8 = 0xEA;
pub const TAG_FLOAT16: u8 = 0xEC;
pub const TAG_FLOAT32: u8 = 0xE4;
pub const TAG_FLOAT64: u8 = 0xE5;
pub const TAG_CONSTRUCTOR_START_STRING_TABLE: u8 = 0xE6;
pub const TAG_CONSTRUCTOR_START_LIST: u8 = 0xE7;
pub const TAG_CONSTRUCTOR_START_MAP: u8 = 0xF1;
pub const TAG_CONSTRUCTOR_START_SET: u8 = 0xF2;
pub const TAG_APPEND_STRING_TABLE: u8 = 0xEB;

pub const TAG_ARRAY16: u8 = 0xED;
pub const TAG_ARRAY32: u8 = 0xEE;
pub const TAG_ARRAY64: u8 = 0xEF;
pub const TAG_ARRAY128: u8 = 0xF0;
