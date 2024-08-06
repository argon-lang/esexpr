package dev.argon.esexpr;

import java.lang.annotation.*;

/**
 * Defines a codec override for a specified type.
 */

@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
@Repeatable(ESExprCodecOverrideList.class)
public @interface ESExprOverrideCodec {
	/**
	 * The type for which the codec is mapped.
	 * @return The type.
	 */
	Class<?> value();

	/**
	 * The type of codec being specified.
	 * @return The CodecType.
	 */
	CodecType codecType() default CodecType.VALUE;

	/**
	 * If annotations are specified here, at least one must be present on the type usage for this to apply.
	 * @return An array of the annotation types.
	 */
	Class<?>[] requiredAnnotations() default {};

	/**
	 * If annotations are specified here, this will not apply if any are present.
	 * @return An array of the annotation types.
	 */
	Class<?>[] excludedAnnotations() default {};

	/**
	 * Specifies what kind of codec will be overridden.
	 */
	public enum CodecType {
		/**
		 * A value codec. (`ESExprCodec`)
		 */
		VALUE,

		/**
		 * An optional value codec. (`OptionalValueCodec`)
		 */
		OPTIONAL_VALUE,

		/**
		 * A variable argument codec (`VarargCodec`)
		 */
		VARARG,

		/**
		 * A dictionary codec (`DictCodec`)
		 */
		DICT,
	}
}
