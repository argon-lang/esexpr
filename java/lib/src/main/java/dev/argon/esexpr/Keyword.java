package dev.argon.esexpr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.RECORD_COMPONENT)
public @interface Keyword {
	String name() default "";
	boolean required() default true;
	String defaultValueMethod() default "";
}
