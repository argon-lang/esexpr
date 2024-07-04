package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

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
    public static record Constructor(@NotNull String constructor, @NotNull List<ESExpr> args, @NotNull Map<String, ESExpr> kwargs) implements ESExpr {
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
			return new ESExprTag.Bool();
		}
	}

	/**
	 * An integer value.
	 * @param n The integer value.
	 */
    public static record Int(@NotNull BigInteger n) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Int();
		}
	}

	/**
	 * A string value.
	 * @param s The string value.
	 */
    public static record Str(@NotNull String s) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Str();
		}
	}

	/**
	 * A binary value.
	 * @param b The binary data.
	 */
    public static record Binary(byte @NotNull[] b) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Binary();
		}
	}

	/**
	 * A 32-bit floating point value.
	 * @param f The float value.
	 */
    public static record Float32(float f) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Float32();
		}
	}

	/**
	 * A 64-bit floating point value.
	 * @param d The double value.
	 */
    public static record Float64(double d) implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Float64();
		}
	}

	/**
	 * A null value.
	 */
    public static record Null() implements ESExpr {
		@Override
		public @NotNull ESExprTag tag() {
			return new ESExprTag.Null();
		}
	}

}
