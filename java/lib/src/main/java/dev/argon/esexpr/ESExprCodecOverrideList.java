package dev.argon.esexpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies codec overrides.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
public @interface ESExprCodecOverrideList {
	/**
	 * Get the overrides.
	 * @return The overrides.
	 */
	ESExprOverrideCodec[] value();
}
