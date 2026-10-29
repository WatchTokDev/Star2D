package com.star4droid.template.behaviors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Physics settings the behavior applies to the element when it STARTS (runtime only, the saved element is never rewritten):
 * {@code @Sets({"fixedRotation=true", "gravityScale=1"})}.
 * Keys: gravityScale (number), fixedRotation (boolean), type (DYNAMIC | KINEMATIC | STATIC), bullet (boolean),
 * sensor (boolean), linearDamping (number), angularDamping (number).
 * Applied in the order of the behaviors list, so a later behavior wins when two set the same key (the inspector warns).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Sets {
	String[] value();
}
