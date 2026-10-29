package com.star4droid.template.behaviors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Optional extra information for a public field: description under the field, range for the number pad, unit label. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Param {
	String desc() default "";
	/** Display label (default: the field name split into words). */
	String label() default "";
	double min() default -1000000000d;
	double max() default 1000000000d;
	double step() default 0d;
	/** "px", "px/s", "s", "deg"... shown after the label. */
	String unit() default "";
}
