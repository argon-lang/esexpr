package dev.argon.esexpr;

import org.jetbrains.annotations.NotNull;

/**
 * A tag that indicates the type of ESExpr.
 */
public sealed interface ESExprTag {
	/**
	 * A tag for a constructor value.
	 * @param constructor The name of the constructor.
	 */
    public static record Constructor(
		@NotNull String constructor) implements ESExprTag {}

	/**
	 * A tag for a boolean value.
	 */
    public static record Bool() implements ESExprTag {}

	/**
	 * A tag for an integer value.
	 */
    public static record Int() implements ESExprTag {}

	/**
	 * A tag for a string value.
	 */
    public static record Str() implements ESExprTag {}

	/**
	 * A tag for a binary value.
	 */
    public static record Binary() implements ESExprTag {}

	/**
	 * A tag for a 32-bit floating point value.
	 */
    public static record Float32() implements ESExprTag {}

	/**
	 * A tag for a 64-bit floating point value.
	 */
    public static record Float64() implements ESExprTag {}

	/**
	 * A tag for a null value.
	 */
    public static record Null() implements ESExprTag {}

}
