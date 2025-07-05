package dev.argon.esexpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the value is encoded as a keyword argument.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.RECORD_COMPONENT)
public @interface Keyword {
	/**
	 * Gets the name of the keyword argument.
	 * @return The name.
	 */
	String value() default "";
}
