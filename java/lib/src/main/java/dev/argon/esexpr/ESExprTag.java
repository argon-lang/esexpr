package dev.argon.esexpr;


/**
 * A tag that indicates the type of ESExpr.
 */
public sealed interface ESExprTag {
	/**
	 * A tag for a constructor value.
	 * @param constructor The name of the constructor.
	 */
    public static record Constructor(
		String constructor) implements ESExprTag {}


	/**
	 * A tag for scalar (primitive) values.
	 */
	public static enum Scalar implements ESExprTag {
		/**
		 * A tag for boolean values.
		 */
		BOOL,
		/**
		 * A tag for integer values.
		 */
		INT,
		/**
		 * A tag for string values.
		 */
		STR,
		/**
		 * A tag for 16-bit floating point values.
		 */
		FLOAT16,
		/**
		 * A tag for 32-bit floating point values.
		 */
		FLOAT32,
		/**
		 * A tag for 64-bit floating point values.
		 */
		FLOAT64,
		/**
		 * A tag for 8-bit array values.
		 */
		ARRAY8,
		/**
		 * A tag for 16-bit array values.
		 */
		ARRAY16,
		/**
		 * A tag for 32-bit array values.
		 */
		ARRAY32,
		/**
		 * A tag for 64-bit array values.
		 */
		ARRAY64,
		/**
		 * A tag for 128-bit array values.
		 */
		ARRAY128,
		/**
		 * A tag for null values.
		 */
		NULL,
	}

	/**
	 * A tag for boolean values.
	 */
	public static final ESExprTag BOOL = Scalar.BOOL;
	/**
	 * A tag for integer values.
	 */
	public static final ESExprTag INT = Scalar.INT;
	/**
	 * A tag for string values.
	 */
	public static final ESExprTag STR = Scalar.STR;
	/**
	 * A tag for 16-bit float values.
	 */
	public static final ESExprTag FLOAT16 = Scalar.FLOAT16;
	/**
	 * A tag for 32-bit float values.
	 */
	public static final ESExprTag FLOAT32 = Scalar.FLOAT32;
	/**
	 * A tag for 64-bit float values.
	 */
	public static final ESExprTag FLOAT64 = Scalar.FLOAT64;
	/**
	 * A tag for 8-bit array values.
	 */
	public static final ESExprTag ARRAY8 = Scalar.ARRAY8;
	/**
	 * A tag for 16-bit array values.
	 */
	public static final ESExprTag ARRAY16 = Scalar.ARRAY16;
	/**
	 * A tag for 32-bit array values.
	 */
	public static final ESExprTag ARRAY32 = Scalar.ARRAY32;
	/**
	 * A tag for 64-bit array values.
	 */
	public static final ESExprTag ARRAY64 = Scalar.ARRAY64;
	/**
	 * A tag for 128-bit array values.
	 */
	public static final ESExprTag ARRAY128 = Scalar.ARRAY128;
	/**
	 * A tag for null values.
	 */
	public static final ESExprTag NULL = Scalar.NULL;
	

}
