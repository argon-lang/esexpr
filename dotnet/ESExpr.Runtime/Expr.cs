using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Numerics;
using System.Linq;

namespace ESExpr.Runtime;

public abstract record Expr {
	private Expr() { }

	public abstract ESExprTag Tag { get; }

	public sealed record Constructor(
		string constructor,
		ImmutableList<Expr> args,
		ImmutableDictionary<string, Expr> kwargs
	) : Expr {
		public Constructor(string constructor, IReadOnlyList<Expr> args, IReadOnlyDictionary<string, Expr> kwargs)
			: this(constructor, args.ToImmutableList(), kwargs.ToImmutableDictionary()) { }

		public override ESExprTag Tag => new ESExprTag.Constructor(constructor);

		public override int GetHashCode() {
			var hash = new HashCode();
			hash.Add(constructor);
			foreach(var arg in args) {
				hash.Add(arg);
			}
			foreach(var kw in kwargs) {
				hash.Add(kw.Key);
				hash.Add(kw.Value);
			}
			return hash.ToHashCode();
		}

		public bool Equals(Constructor? other) {
            if (other is null) return false;
            
            if (constructor != other.constructor) return false;
            if (args.Count != other.args.Count) return false;
            if (kwargs.Count != other.kwargs.Count) return false;
            
            for(int i = 0; i < args.Count; i++) {
                if (!args[i].Equals(other.args[i])) return false;
            }
            
            foreach(var kvp in kwargs) {
                if (!other.kwargs.TryGetValue(kvp.Key, out var otherValue) || 
                    !kvp.Value.Equals(otherValue)) return false;
            }
            
            return true;
		}

		public override string ToString() {
            var argsStr = string.Join(", ",
	            args.Select(arg => arg.ToString())
		            .Concat(kwargs.Select(kv => $"{kv.Key}: {kv.Value}"))
	        );
            
	        return $"{constructor}({argsStr})";
        }
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

	public sealed record Float16(Half value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Float16();

		public override int GetHashCode() {
			return value.GetHashCode();
		}

		public bool Equals(Float16? other) {
			return other is not null &&
				BitConverter.HalfToUInt16Bits(value) == BitConverter.HalfToUInt16Bits(other.value);
		}
	}

	public sealed record Float32(float value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Float32();


		public override int GetHashCode() {
			return value.GetHashCode();
		}

		public bool Equals(Float32? other) {
			return other is not null &&
				BitConverter.SingleToUInt32Bits(value) == BitConverter.SingleToUInt32Bits(other.value);
		}
	}

	public sealed record Float64(double value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Float64();

		public override int GetHashCode() {
			return value.GetHashCode();
		}

		public bool Equals(Float64? other) {
			return other is not null &&
				BitConverter.DoubleToUInt64Bits(value) == BitConverter.DoubleToUInt64Bits(other.value);
		}
	}

	public sealed record Array8(ImmutableArray<byte> value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Array8();

		public override int GetHashCode() {
			var hash = new HashCode();
			foreach(var b in value) {
				hash.Add(b);
			}
			return hash.ToHashCode();
		}

		public bool Equals(Array8? other) {
			return other is not null &&
				value.AsSpan().SequenceEqual(other.value.AsSpan());
		}

		public override string ToString() {
			return $"Array8[{string.Join(", ", value)}]";
		}
	}

	public sealed record Array16(ImmutableArray<ushort> value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Array16();

		public override int GetHashCode() {
			var hash = new HashCode();
			foreach(var b in value) {
				hash.Add(b);
			}
			return hash.ToHashCode();
		}

		public bool Equals(Array16? other) {
			return other is not null &&
				value.AsSpan().SequenceEqual(other.value.AsSpan());
		}

		public override string ToString() {
			return $"Array16[{string.Join(", ", value)}]";
		}
	}

	public sealed record Array32(ImmutableArray<uint> value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Array32();

		public override int GetHashCode() {
			var hash = new HashCode();
			foreach(var b in value) {
				hash.Add(b);
			}
			return hash.ToHashCode();
		}

		public bool Equals(Array32? other) {
			return other is not null &&
				value.AsSpan().SequenceEqual(other.value.AsSpan());
		}

		public override string ToString() {
			return $"Array32[{string.Join(", ", value)}]";
		}
	}

	public sealed record Array64(ImmutableArray<ulong> value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Array64();

		public override int GetHashCode() {
			var hash = new HashCode();
			foreach(var b in value) {
				hash.Add(b);
			}
			return hash.ToHashCode();
		}

		public bool Equals(Array64? other) {
			return other is not null &&
				value.AsSpan().SequenceEqual(other.value.AsSpan());
		}

		public override string ToString() {
			return $"Array64[{string.Join(", ", value)}]";
		}
	}

	public sealed record Array128(ImmutableArray<UInt128> value) : Expr {
		public override ESExprTag Tag => new ESExprTag.Array128();

		public override int GetHashCode() {
			var hash = new HashCode();
			foreach(var b in value) {
				hash.Add(b);
			}
			return hash.ToHashCode();
		}

		public bool Equals(Array128? other) {
			return other is not null &&
				value.AsSpan().SequenceEqual(other.value.AsSpan());
		}

		public override string ToString() {
			return $"Array128[{string.Join(", ", value)}]";
		}
	}

	public sealed record Null(BigInteger level) : Expr {
		public override ESExprTag Tag => new ESExprTag.Null();
	}


	public sealed class Codec : IESExprCodec<Expr> {
		public ESExprTagSet Tags => ESExprTagSet.All;
		
		public bool IsEncodedEqual(Expr a, Expr b) => a == b;
		
		public Expr Encode(Expr value) => value;

		public Expr Decode(Expr expr, DecodeFailurePath path) => expr;
	}
}
