using System;
using System.Collections.Immutable;
using System.Linq;

namespace ESExpr.Runtime.Codecs;

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array8 ])]
public class ImmutableArrayByteCodec : IESExprCodec<ImmutableArray<byte>> {
	public ESExprTagSet Tags => ESExprTagSet.Create([ new ESExprTag.Array8() ]);

	public bool IsEncodedEqual(ImmutableArray<byte> a, ImmutableArray<byte> b) =>
		a.SequenceEqual(b);

	public Expr Encode(ImmutableArray<byte> value) {
		return new Expr.Array8(value);
	}

	public ImmutableArray<byte> Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Array8(var a)) {
			return a;
		}
		else {
			throw new DecodeException("Expected an array8", path);
		}
	}
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array16 ])]
public class ImmutableArrayUInt16Codec : IESExprCodec<ImmutableArray<ushort>> {
	public ESExprTagSet Tags => ESExprTagSet.Create([ new ESExprTag.Array16() ]);

	public bool IsEncodedEqual(ImmutableArray<ushort> a, ImmutableArray<ushort> b) =>
		a.SequenceEqual(b);

	public Expr Encode(ImmutableArray<ushort> value) {
		return new Expr.Array16(value);
	}

	public ImmutableArray<ushort> Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Array16(var a)) {
			return a;
		}
		else {
			throw new DecodeException("Expected an array16", path);
		}
	}
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array32 ])]
public class ImmutableArrayUInt32Codec : IESExprCodec<ImmutableArray<uint>> {
	public ESExprTagSet Tags => ESExprTagSet.Create([ new ESExprTag.Array32() ]);

	public bool IsEncodedEqual(ImmutableArray<uint> a, ImmutableArray<uint> b) =>
		a.SequenceEqual(b);

	public Expr Encode(ImmutableArray<uint> value) {
		return new Expr.Array32(value);
	}

	public ImmutableArray<uint> Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Array32(var a)) {
			return a;
		}
		else {
			throw new DecodeException("Expected an array32", path);
		}
	}
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array64 ])]
public class ImmutableArrayUInt64Codec : IESExprCodec<ImmutableArray<ulong>> {
	public ESExprTagSet Tags => ESExprTagSet.Create([ new ESExprTag.Array64() ]);

	public bool IsEncodedEqual(ImmutableArray<ulong> a, ImmutableArray<ulong> b) =>
		a.SequenceEqual(b);

	public Expr Encode(ImmutableArray<ulong> value) {
		return new Expr.Array64(value);
	}

	public ImmutableArray<ulong> Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Array64(var a)) {
			return a;
		}
		else {
			throw new DecodeException("Expected an array64", path);
		}
	}
}

[ESExprOverrideCodec]
[ESExprTags(Scalar = [ ESExprTag.ScalarType.Array128 ])]
public class ImmutableArrayUInt128Codec : IESExprCodec<ImmutableArray<UInt128>> {
	public ESExprTagSet Tags => ESExprTagSet.Create([ new ESExprTag.Array128() ]);

	public bool IsEncodedEqual(ImmutableArray<UInt128> a, ImmutableArray<UInt128> b) =>
		a.SequenceEqual(b);

	public Expr Encode(ImmutableArray<UInt128> value) {
		return new Expr.Array128(value);
	}

	public ImmutableArray<UInt128> Decode(Expr expr, DecodeFailurePath path) {
		if(expr is Expr.Array128(var a)) {
			return a;
		}
		else {
			throw new DecodeException("Expected an array128", path);
		}
	}
}



