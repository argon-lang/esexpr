package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents an ESExpr value.
 */
public sealed interface ESExpr {

	/**
	 * Gets the tag of this expression.
	 * @return The tag.
	 */
	@NotNull ESExprTag tag();

	/**
	 * A constructor value.
	 * @param constructor The constructor name.
	 * @param args Positional arguments.
	 * @param kwargs Keyword arguments.
	 */
    public static record Constructor(@NotNull String constructor, @NotNull List<@NotNull ESExpr> args, @NotNull Map<@NotNull String, @NotNull ESExpr> kwargs) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Constructor(constructor);
		}
	}

	/**
	 * A boolean value.
	 * @param b The boolean value.
	 */
    public static record Bool(boolean b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.BOOL;
		}
	}

	/**
	 * An integer value.
	 * @param n The integer value.
	 */
    public static record Int(@NotNull BigInteger n) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.INT;
		}
	}

	/**
	 * A string value.
	 * @param s The string value.
	 */
    public static record Str(@NotNull String s) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.STR;
		}
	}

	/**
	 * A 32-bit floating point value.
	 * @param f The float value.
	 */
	public static record Float16(short f) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.FLOAT16;
		}
	}

	/**
	 * A 32-bit floating point value.
	 * @param f The float value.
	 */
    public static record Float32(float f) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.FLOAT32;
		}
	}

	/**
	 * A 64-bit floating point value.
	 * @param d The double value.
	 */
    public static record Float64(double d) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.FLOAT64;
		}
	}

	/**
	 * An array of 8-bit values.
	 * @param b The values.
	 */
	public static record Array8(byte @NotNull[] b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.ARRAY8;
		}

		@Override
		public final @NotNull String toString() {
			return "Array8" + Arrays.toString(b);
		}

		@Override
		public final int hashCode() {
			return Arrays.hashCode(b);
		}

		@Override
		public final boolean equals(Object obj) {
			if(!(obj instanceof Array8 other)) {
				return false;
			}

			return Arrays.equals(b, other.b());
		}
	}

	/**
	 * An array of 16-bit values.
	 *
	 * @param b The values.
	 */
	public static record Array16(short @NotNull [] b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.ARRAY16;
		}

		@Override
		public final @NotNull String toString() {
			return "Array16" + Arrays.toString(b);
		}

		@Override
		public final int hashCode() {
			return Arrays.hashCode(b);
		}

		@Override
		public final boolean equals(Object obj) {
			if (!(obj instanceof Array16 other)) {
				return false;
			}

			return Arrays.equals(b, other.b());
		}
	}

	/**
	 * An array of 32-bit values.
	 *
	 * @param b The values.
	 */
	public static record Array32(int @NotNull [] b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.ARRAY32;
		}

		@Override
		public final @NotNull String toString() {
			return "Array32" + Arrays.toString(b);
		}

		@Override
		public final int hashCode() {
			return Arrays.hashCode(b);
		}

		@Override
		public final boolean equals(Object obj) {
			if (!(obj instanceof Array32 other)) {
				return false;
			}

			return Arrays.equals(b, other.b());
		}
	}

	/**
	 * An array of 64-bit values.
	 *
	 * @param b The values.
	 */
	public static record Array64(long @NotNull [] b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.ARRAY64;
		}

		@Override
		public final @NotNull String toString() {
			return "Array64" + Arrays.toString(b);
		}

		@Override
		public final int hashCode() {
			return Arrays.hashCode(b);
		}

		@Override
		public final boolean equals(Object obj) {
			if (!(obj instanceof Array64 other)) {
				return false;
			}

			return Arrays.equals(b, other.b());
		}
	}

	/**
	 * An array of 128-bit values.
	 *
	 * @param b The complex number values as pairs of longs.
	 */
	public static record Array128(long @NotNull [] b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.ARRAY128;
		}

		@Override
		public final @NotNull String toString() {
			return "Array128" + Arrays.toString(b);
		}

		@Override
		public final int hashCode() {
			return Arrays.hashCode(b);
		}

		@Override
		public final boolean equals(Object obj) {
			if (!(obj instanceof Array128 other)) {
				return false;
			}

			return Arrays.equals(b, other.b());
		}
	}

	/**
	 * A null value.
	 * @param level The level where the null lives. Used to disambiguate nesting of Optional types.
	 */
    public static record Null(BigInteger level) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return ESExprTag.NULL;
		}
	}



	/**
	 * Codec for arbitrary ESExpr values.
	 * @return The codec.
	 */
	public static @NotNull ESExprCodec<@NotNull ESExpr> codec() {
		return CODEC;
	}

	/**
	 * Codec for arbitrary ESExpr values.
	 */
	static final @NotNull ESExprCodec<@NotNull ESExpr> CODEC = new ESExprCodec<ESExpr>() {
		@Override
		public @NotNull ESExprTagSet tags() {
			return new ESExprTagSet.All();
		}

		@Override
		public @NotNull ESExpr encode(@NotNull ESExpr value) {
			return value;
		}

		@Override
		public @NotNull ESExpr decode(@NotNull ESExpr expr, @NotNull FailurePath path) throws DecodeException {
			return expr;
		}
	};
}
