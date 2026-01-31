package dev.argon.esexpr;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.eclipse.collections.api.list.primitive.ImmutableByteList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.ImmutableShortList;

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
	ESExprTag tag();

	/**
	 * A constructor value.
	 * @param constructor The constructor name.
	 * @param args Positional arguments.
	 * @param kwargs Keyword arguments.
	 */
    public static record Constructor(String constructor, ImmutableList<ESExpr> args, ImmutableMap<String, ESExpr> kwargs) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return new ESExprTag.Constructor(constructor);
		}
	}

	/**
	 * A boolean value.
	 * @param b The boolean value.
	 */
    public static record Bool(boolean b) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.BOOL;
		}
	}

	/**
	 * An integer value.
	 * @param n The integer value.
	 */
    public static record Int(BigInteger n) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.INT;
		}
	}

	/**
	 * A string value.
	 * @param s The string value.
	 */
    public static record Str(String s) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.STR;
		}
	}

	/**
	 * A 32-bit floating point value.
	 * @param f The float value.
	 */
	public static record Float16(short f) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.FLOAT16;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Float16(var other) &&
				f == other;
		}

		@Override
		public int hashCode() {
			return f;
		}

		@Override
		public String toString() {
			return "Float16[f=" + Float.float16ToFloat(f) + "]";
		}
	}

	/**
	 * A 32-bit floating point value.
	 * @param f The float value.
	 */
    public static record Float32(float f) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.FLOAT32;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Float32(var other) &&
				Float.floatToRawIntBits(f) == Float.floatToRawIntBits(other);
		}

		@Override
		public int hashCode() {
			return Float.floatToRawIntBits(f);
		}
	}

	/**
	 * A 64-bit floating point value.
	 * @param d The double value.
	 */
    public static record Float64(double d) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.FLOAT64;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Float64(var other) &&
				Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(other);
		}

		@Override
		public int hashCode() {
			return Long.hashCode(Double.doubleToRawLongBits(d));
		}
	}

	/**
	 * An array of 8-bit values.
	 * @param b The values.
	 */
	public static record Array8(ImmutableByteList b) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.ARRAY8;
		}
	}

	/**
	 * An array of 16-bit values.
	 *
	 * @param b The values.
	 */
	public static record Array16(ImmutableShortList b) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.ARRAY16;
		}
	}

	/**
	 * An array of 32-bit values.
	 *
	 * @param b The values.
	 */
	public static record Array32(ImmutableIntList b) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.ARRAY32;
		}
	}

	/**
	 * An array of 64-bit values.
	 *
	 * @param b The values.
	 */
	public static record Array64(ImmutableLongList b) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.ARRAY64;
		}
	}

	/**
	 * An array of 128-bit values.
	 *
	 * @param b The complex number values as pairs of longs.
	 */
	public static record Array128(ImmutableLongList b) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.ARRAY128;
		}
	}

	/**
	 * A null value.
	 * @param level The level where the null lives. Used to disambiguate nesting of Optional types.
	 */
    public static record Null(BigInteger level) implements ESExpr {
		@Override
		public ESExprTag tag() {
			return ESExprTag.NULL;
		}
	}



	/**
	 * Codec for arbitrary ESExpr values.
	 * @return The codec.
	 */
	public static ESExprCodec<ESExpr> codec() {
		return CODEC;
	}

	/**
	 * Codec for arbitrary ESExpr values.
	 */
	static final ESExprCodec<ESExpr> CODEC = new ESExprCodec<>() {
		@Override
		public ESExprTagSet tags() {
			return new ESExprTagSet.All();
		}

		@Override
		public boolean isEncodedEqual(ESExpr x, ESExpr y) {
			return x.equals(y);
		}

		@Override
		public ESExpr encode(ESExpr value) {
			return value;
		}

		@Override
		public ESExpr decode(ESExpr expr, FailurePath path) throws DecodeException {
			return expr;
		}
	};
}
