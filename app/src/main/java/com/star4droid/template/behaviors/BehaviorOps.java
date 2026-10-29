package com.star4droid.template.behaviors;

/** Small conversions used by the behavior blocks ("setting ___ of behavior ___"); no android / libgdx imports. */
public final class BehaviorOps {
	private BehaviorOps() {}

	/** A behavior setting read with getBehaviorParam as a number (0 when missing or not a number). */
	public static float num(Object v) {
		if (v instanceof Number) return ((Number) v).floatValue();
		if (v instanceof Boolean) return ((Boolean) v).booleanValue() ? 1f : 0f;
		if (v instanceof CharSequence) {
			try {
				return Float.parseFloat(v.toString().trim());
			} catch (NumberFormatException ignored) {
			}
		}
		return 0f;
	}

	/** A behavior setting as yes/no (false when missing). */
	public static boolean bool(Object v) {
		if (v instanceof Boolean) return ((Boolean) v).booleanValue();
		if (v instanceof Number) return ((Number) v).floatValue() != 0f;
		if (v instanceof CharSequence) return v.toString().trim().equalsIgnoreCase("true");
		return false;
	}

	/** A behavior setting as text ("" when missing). */
	public static String text(Object v) {
		return v == null ? "" : String.valueOf(v);
	}
}
