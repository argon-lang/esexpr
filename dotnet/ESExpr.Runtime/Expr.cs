using System.Collections.Generic;
using System.Collections.Immutable;
using System.Numerics;

namespace ESExpr.Runtime;

public abstract record Expr {
	private Expr() { }
	
	public abstract ESExprTag Tag { get; }

	public sealed record Constructor(
		string constructor,
		VList<Expr> args,
		VDict<Expr> kwargs
	) : Expr {
		public Constructor(string constructor, IReadOnlyList<Expr> args, IReadOnlyDictionary<string, Expr> kwargs)
			: this(constructor, args.ToImmutableList(), kwargs.ToImmutableDictionary())
		{}

		public override ESExprTag Tag => new ESExprTag.Constructor(constructor);
	}

	public sealed record Bool(bool value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Bool();
	}
	public sealed record Int(BigInteger value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Int();
	}

	public sealed record Str(string value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Str();
	}

	public sealed record Binary(byte[] value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Binary();

		public override int GetHashCode() => new Binary(value).GetHashCode();
		public bool Equals(Binary? other) => other is not null && new Binary(value) == new Binary(other.value);
	}

	public sealed record Float32(float value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Float32();
	}

	public sealed record Float64(double value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Float64();
	}

	public sealed record Null() : Expr {
		public override ESExprTag Tag => new ESExprTag.Null();
	}


	public sealed class Codec : IESExprCodec<Expr> {
		public ISet<ESExprTag> Tags => (HashSet<ESExprTag>)[];
		public Expr Encode(Expr value) => value;

		public Expr Decode(Expr expr, DecodeFailurePath path) => expr;
	}
}
