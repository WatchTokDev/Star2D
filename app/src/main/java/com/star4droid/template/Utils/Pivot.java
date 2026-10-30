package com.star4droid.template.Utils;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.ElementDefs.ItemDef;

/**
 * Pivot (origin) of an element: the point it rotates and scales around.
 * Names: center, left, right, top, bottom, topleft, topright, bottomleft, bottomright, custom (custom = Pivot X / Pivot Y as 0..1 of the size).
 */
public final class Pivot {
	private Pivot() {}

	public static final String[] NAMES = {"center", "left", "right", "top", "bottom", "topleft", "topright", "bottomleft", "bottomright", "custom"};

	/** Fraction {fx, fy} (0..1 of width / height, y up) of a pivot name. */
	public static float[] fraction(String pivot, float customX, float customY) {
		String p = pivot == null ? "center" : pivot.trim().toLowerCase();
		switch (p) {
			case "left": return new float[]{0f, 0.5f};
			case "right": return new float[]{1f, 0.5f};
			case "top": return new float[]{0.5f, 1f};
			case "bottom": return new float[]{0.5f, 0f};
			case "topleft": return new float[]{0f, 1f};
			case "topright": return new float[]{1f, 1f};
			case "bottomleft": return new float[]{0f, 0f};
			case "bottomright": return new float[]{1f, 0f};
			case "custom": return new float[]{customX, customY};
			default: return new float[]{0.5f, 0.5f};
		}
	}

	/** Sets the origin of {@code a} from its current size. */
	public static void apply(Actor a, String pivot, float customX, float customY) {
		float[] f = fraction(pivot, customX, customY);
		a.setOrigin(a.getWidth() * f[0], a.getHeight() * f[1]);
	}

	public static void apply(Actor a, ItemDef def) {
		if (def == null) apply(a, "center", 0.5f, 0.5f);
		else apply(a, def.Pivot, def.Pivot_X, def.Pivot_Y);
	}
}
