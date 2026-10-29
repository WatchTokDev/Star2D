package com.star4droid.template.Utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Shows a {@code private} / {@code protected} field of a Body Script or Scene Script in the Properties panel (like Unity's
 * [SerializeField]); the value set there is written into the field when the scene starts:
 * <pre>
 * &#64;Expose private float speed = 5;
 * </pre>
 * Public and package-private fields are shown without it; {@code final} and {@code transient} ones never.
 * Only the editor reads it (from the source text); it has no effect at run time.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Expose {}
