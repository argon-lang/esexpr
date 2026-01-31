package dev.argon.esexpr;

import java.math.BigInteger;

/*
	Tokens with an integer (last 5 bits are the beginning of the varint)
	000XXXXX constructor
	001XXXXX Int
	010XXXXX NegInt
	011XXXXX String
	100XXXXX String Pool Index
	101XXXXX Array8
	110XXXXX Keyword
	111XXXXX Fixed


	11100000 Constructor End
	11100001 True
	11100010 False
	11100011 Null0
	11101000 Null1
	11101001 Null2
	11101010 NullN
	11101100 Float16
	11100100 Float32
	11100101 Float64
	11100110 constructor start "string-table"
	11100111 constructor start "list"
	11110001 constructor start "map"
	11110010 constructor start "set"
	11101011 append string table
	11101101 Array16
	11101110 Array32
	11101111 Array64
	11110000 Array128
 */
sealed interface BinToken {
	static record WithInteger(WithIntegerType type, BigInteger value) implements BinToken {
	}

	static enum WithIntegerType {
		CONSTRUCTOR,
		INT,
		NEG_INT,
		STRING,
		STRING_POOL_INDEX,
		ARRAY8,
		KEYWORD,
	}

	static enum Fixed implements BinToken {
		CONSTRUCTOR_END,
		TRUE,
		FALSE,
		NULL0,
		NULL1,
		NULL2,
		NULLN,
		FLOAT16,
		FLOAT32,
		FLOAT64,
		CONSTRUCTOR_START_STRING_TABLE,
		CONSTRUCTOR_START_LIST,
		CONSTRUCTOR_START_MAP,
		CONSTRUCTOR_START_SET,
		APPEND_STRING_TABLE,
		ARRAY16,
		ARRAY32,
		ARRAY64,
		ARRAY128,
	}

	static final String StringTableName = "string-table";
	static final String ListName = "list";
	static final String MapName = "map";
	static final String SetName = "set";
}
