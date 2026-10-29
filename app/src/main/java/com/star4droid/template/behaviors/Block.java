package com.star4droid.template.behaviors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a PUBLIC METHOD of a behavior as a visual block. The block is shown in the block editor (and offered to the AI)
 * only while the project's scene has an element that uses this behavior.
 *
 * <pre>
 * &#64;Block(label = "set run speed", desc = "Changes the running speed (px/s).", labelAr = "اضبط سرعة الجري", args = {"speed"})
 * public void setSpeed(float value) { speed = value; }
 *
 * &#64;Block(label = "is on the ground")      // a method that returns a value becomes a value (round) block
 * public boolean isGrounded() { ... }
 * </pre>
 *
 * Parameter types: int / long / float / double (number slot), boolean (condition slot), String (text slot),
 * PlayerItem (element picker), com.badlogic.gdx.graphics.Color (hex text). Methods with other parameter types are ignored.
 * void -> action block; boolean / number / String / PlayerItem -> value block. The first slot of every block is the
 * element that owns the behavior ("current" by default); if that element does not have the behavior the block does nothing
 * (value blocks give false / 0 / "" / null).
 *
 * Only label is required. Everything is plain text so the editor can also read it from the source of a project's own
 * behaviors (java/com/star4droid/Game/Behaviors/*.java) without compiling them.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Block {
	/** Text on the block (English). */
	String label();
	/** Explanation shown by the (i) button (English). */
	String desc() default "";
	/** Group in the palette; default = the behavior's name. */
	String category() default "";
	/** Arabic text on the block / explanation (optional; the editor falls back to English). */
	String labelAr() default "";
	String descAr() default "";
	/** Names of the parameter slots, in order ("speed", "enemy"...); default: derived from the type. */
	String[] args() default {};
}
