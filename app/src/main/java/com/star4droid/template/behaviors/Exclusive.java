package com.star4droid.template.behaviors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Behaviors that drive the same thing (movement, steering, weapon...) share a group name: {@code @Exclusive("movement")}.
 * The editor asks before adding a second one to an element and at runtime enabling one disables the others of its group
 * (see PlayerItem.setBehaviorEnabled). Behaviors that only read (health, lifetime...) have no group and never conflict.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Exclusive {
	String value();
}
