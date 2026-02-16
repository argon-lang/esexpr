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

internal sealed class SByteCodec : IntCodecBase<sbyte> {
}

internal sealed class ByteCodec : IntCodecBase<byte> {
}

internal sealed class ShortCodec : IntCodecBase<short> {
}

internal sealed class UShortCodec : IntCodecBase<ushort> {
}

internal sealed class IntCodec : IntCodecBase<int> {
}

internal sealed class UIntCodec : IntCodecBase<uint> {
}

internal sealed class LongCodec : IntCodecBase<long> {
}

internal sealed class ULongCodec : IntCodecBase<ulong> {
}

internal sealed class Int128Codec : IntCodecBase<Int128> {
}

internal sealed class UInt128Codec : IntCodecBase<UInt128> {
}

internal sealed class BigIntegerCodec : IESExprCodec<BigInteger> {
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
