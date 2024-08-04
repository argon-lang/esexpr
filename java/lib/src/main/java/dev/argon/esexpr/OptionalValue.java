package dev.argon.esexpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an argument is optional.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.RECORD_COMPONENT)
public @interface OptionalValue {
	/**
	 * The OptionalValueCodec to use for this field.
	 * @return The OptionalValueCodec.
	 */
	Class<?> value();
}
