using System;
using System.Collections.Generic;
using System.Numerics;

namespace ESExpr.Runtime;

public struct Nat {
	public Nat(BigInteger value) {
		if(value.Sign < 0) {
			throw new ArgumentException("Nat can not be negative", nameof(value));;
		}
		
		this.BigIntegerValue = value;
	}

	public BigInteger BigIntegerValue { get; }

	public class Codec : IESExprCodec<Nat> {
		public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[ new ESExprTag.Int() ];

		public Expr Encode(Nat value) {
			return new Expr.Int(value.BigIntegerValue);
		}

		public Nat Decode(Expr expr, DecodeFailurePath path) {
			if(expr is Expr.Int(var i)) {
				if(i.Sign < 0) {
					throw new DecodeException("Integer value out of range", path);
				}

				return new Nat(i);
			}
			else {
				throw new DecodeException("Expected an integer value", path);
			}
		}
	}
}
