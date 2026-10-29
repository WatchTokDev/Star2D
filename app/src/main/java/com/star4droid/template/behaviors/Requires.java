package com.star4droid.template.behaviors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What kind of element the behavior needs; the inspector shows a warning when the element does not fit.
 * {@code @Requires(body = true, type = "DYNAMIC")} = a physical element whose body type is DYNAMIC;
 * {@code ui = true} = a screen-space (UI) element.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Requires {
	/** "DYNAMIC", "KINEMATIC", "STATIC" or "" (any). Only for physical elements. */
	String type() default "";
	/** Needs a physics body. */
	boolean body() default false;
	/** Needs a UI element. */
	boolean ui() default false;
	/** Extra explanation shown with the warning. */
	String note() default "";
}
