package dev.argon.esexpr;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify encoding information for ESExpr (Expression) values.
 * This can be applied to methods, types, or fields to indicate how objects
 * are serialized or deserialized in relation to their expressions.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
public @interface ESExprCodecTags {
	/**
	 * {@return Specifies an array of scalar tags that define primitive data types.}
	 */
	ESExprTag.Scalar[] scalar() default {};

	/**
	 * {@return Defines an array of string names for permitted constructors.}
	 */
	String[] constructors() default {};

	/**
	 * {@return A boolean flag indicating whether this represents the set of all tags.}
	 */
	boolean all() default false;

	/**
	 * {@return Indicate this set includes the tags of the specified type parameters.}
	 */
	String[] unionWithTypeParameters() default {};
}
