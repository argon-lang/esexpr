package dev.argon.esexpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the constructor name when encoded as an ESExpr.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface Constructor {
	/**
	 * Gets the constructor name.
	 * @return The constructor name.
	 */
	String value();
}
