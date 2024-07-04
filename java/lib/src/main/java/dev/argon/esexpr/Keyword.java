package dev.argon.esexpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the value is encoded as a keyword argument.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.RECORD_COMPONENT)
public @interface Keyword {
	/**
	 * Gets the name of the keyword argument.
	 * @return The name.
	 */
	String name() default "";

	/**
	 * Gets whether the keyword is required.
	 * @return True when the keyword is required.
	 */
	boolean required() default true;

	/**
	 * Gets the name of a method that returns the default value of this keyword.
	 * @return The name of the method.
	 */
	String defaultValueMethod() default "";
}
