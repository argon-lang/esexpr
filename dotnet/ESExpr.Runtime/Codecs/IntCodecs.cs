using System;
using System.Collections.Generic;
using System.Numerics;

namespace ESExpr.Runtime.Codecs;


public abstract class IntCodecBase<I> : IESExprCodec<I>
	where I : IBinaryInteger<I>, IMinMaxValue<I> {
	internal IntCodecBase() {

	}
	
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Int()]);

	public bool IsEncodedEqual(I a, I b) {
		return a == b;
	}

	public Expr Encode(I value) {
		return new Expr.Int(BigInteger.CreateChecked(value));
	}

	public I Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Int(var i)) {
			try {
				return I.CreateChecked(i);
			}
			catch(OverflowException) {
				throw new DecodeException("Integer value out of range", path);
			}
		}
		else {
			throw new DecodeException("Expected an integer value", path);
		}
	}
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class SByteCodec : IntCodecBase<sbyte> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class ByteCodec : IntCodecBase<byte> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class ShortCodec : IntCodecBase<short> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class UShortCodec : IntCodecBase<ushort> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class IntCodec : IntCodecBase<int> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class UIntCodec : IntCodecBase<uint> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class LongCodec : IntCodecBase<long> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class ULongCodec : IntCodecBase<ulong> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class Int128Codec : IntCodecBase<Int128> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class UInt128Codec : IntCodecBase<UInt128> {
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Int ])]
public sealed class BigIntegerCodec : IESExprCodec<BigInteger> {
	public ESExprTagSet Tags => ESExprTagSet.Create([new ESExprTag.Int()]);
	
	public bool IsEncodedEqual(BigInteger a, BigInteger b) => a == b;

	public Expr Encode(BigInteger value) {
		return new Expr.Int(value);
	}

	public BigInteger Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Int(var i)) {
			return i;
		}
		else {
			throw new DecodeException("Expected an integer value", path);
		}
	}
}
