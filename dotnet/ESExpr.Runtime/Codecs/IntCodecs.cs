using System;
using System.Collections.Generic;
using System.Numerics;

namespace ESExpr.Runtime.Codecs;


public abstract class IntCodecBase<I> : IESExprCodec<I>
	where I: IBinaryInteger<I>, IMinMaxValue<I>
{
	internal IntCodecBase() {
		
	}
	
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Int() ];

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
public sealed class SByteCodec : IntCodecBase<sbyte> {
}

[ESExprOverrideCodec]
public sealed class ByteCodec : IntCodecBase<byte> {
}

[ESExprOverrideCodec]
public sealed class ShortCodec : IntCodecBase<short> {
}

[ESExprOverrideCodec]
public sealed class UShortCodec : IntCodecBase<ushort> {
}

[ESExprOverrideCodec]
public sealed class IntCodec : IntCodecBase<int> {
}

[ESExprOverrideCodec]
public sealed class UIntCodec : IntCodecBase<uint> {
}

[ESExprOverrideCodec]
public sealed class LongCodec : IntCodecBase<long> {
}

[ESExprOverrideCodec]
public sealed class ULongCodec : IntCodecBase<ulong> {
}

[ESExprOverrideCodec]
public sealed class Int128Codec : IntCodecBase<Int128> {
}

[ESExprOverrideCodec]
public sealed class UInt128Codec : IntCodecBase<UInt128> {
}

[ESExprOverrideCodec]
public sealed class BigIntegerCodec : IESExprCodec<BigInteger> {
	public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Int() ];
	
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
