package dev.argon.esexpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.math.BigInteger;

/**
 * Specifies a bitmask for a flag field.
 */
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface FlagMask {

	/**
	 * {@return The bitmask value.}
	 */
	long value() default 0;

	/**
	 * {@return An alternate bitmask value for when the value does not fit in a long.}
	 */
	String bits() default "";
}
