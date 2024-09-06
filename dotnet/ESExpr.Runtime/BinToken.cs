using System.Numerics;

namespace ESExpr.Runtime;

internal record struct BinToken(BinToken.TokenType BinTokenType, BigInteger? IntValue) {
	public enum TokenType {
		Constructor,
		Int,
		NegInt,
		String,
		StringPoolIndex,
		Binary,
		Keyword,
		Null,
		ConstructorEnd,
		True,
		False,
		Float32,
		Float64,
		ConstructorStartStringTable,
		ConstructorStartList,
	}
}
