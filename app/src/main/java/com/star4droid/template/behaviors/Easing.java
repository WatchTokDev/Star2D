package com.star4droid.template.behaviors;

/**
 * Easing curves shared by the UI-effect behaviors (SlideIn, FadeIn, FadeOut...). No allocation, plain math.
 *
 * Every behavior that offers an easing declares its OWN nested enum (the editor reads enums from the same source file) with
 * exactly the constants of {@link #NAMES}, in this order, and calls {@code Easing.apply(ease.ordinal(), t)}.
 */
public final class Easing {
	public static final int LINEAR = 0, EASE_IN = 1, EASE_OUT = 2, EASE_IN_OUT = 3, BACK_OUT = 4, BOUNCE_OUT = 5, ELASTIC_OUT = 6;
	/** The constant names every nested {@code Ease} enum must have, in order. */
	public static final String[] NAMES = {"LINEAR", "EASE_IN", "EASE_OUT", "EASE_IN_OUT", "BACK_OUT", "BOUNCE_OUT", "ELASTIC_OUT"};

	private Easing() {
	}

	/** Curve value for t in 0..1 (0 at the start, 1 at the end; BACK/ELASTIC overshoot above 1 on the way). */
	public static float apply(int type, float t) {
		if (t <= 0) return 0;
		if (t >= 1) return 1;
		switch (type) {
			case EASE_IN:
				return t * t * t;
			case EASE_OUT: {
				float u = 1 - t;
				return 1 - u * u * u;
			}
			case EASE_IN_OUT:
				if (t < 0.5f) return 4 * t * t * t;
				float v = -2 * t + 2;
				return 1 - v * v * v / 2;
			case BACK_OUT: {
				float c1 = 1.70158f, c3 = c1 + 1, u = t - 1;
				return 1 + c3 * u * u * u + c1 * u * u;
			}
			case BOUNCE_OUT:
				return bounceOut(t);
			case ELASTIC_OUT: {
				float c4 = (float) (2 * Math.PI / 3);
				return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75f) * c4) + 1);
			}
			default:
				return t;
		}
	}

	private static float bounceOut(float t) {
		float n1 = 7.5625f, d1 = 2.75f;
		if (t < 1 / d1) return n1 * t * t;
		if (t < 2 / d1) {
			t -= 1.5f / d1;
			return n1 * t * t + 0.75f;
		}
		if (t < 2.5f / d1) {
			t -= 2.25f / d1;
			return n1 * t * t + 0.9375f;
		}
		t -= 2.625f / d1;
		return n1 * t * t + 0.984375f;
	}
}
