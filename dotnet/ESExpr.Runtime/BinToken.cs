using System.Numerics;

namespace ESExpr.Runtime;

internal record struct BinToken(BinToken.TokenType BinTokenType, BigInteger? IntValue) {
	public enum TokenType {
		Constructor,
		Int,
		NegInt,
		String,
		StringPoolIndex,
		Keyword,
		Null0,
		Null1,
		Null2,
		NullN,
		ConstructorEnd,
		True,
		False,
		Float16,
		Float32,
		Float64,
		Array8,
		Array16,
		Array32,
		Array64,
		Array128,
		ConstructorStartStringTable,
		ConstructorStartList,
		AppendStringTable,
	}
}
