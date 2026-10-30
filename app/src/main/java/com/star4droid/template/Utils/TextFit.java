package com.star4droid.template.Utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Align;

/**
 * "Auto Size" of TEXT elements: the biggest font scale (never above the one chosen by the user) at which the text,
 * wrapped to the width of the element, fits inside the element. Works for any script (Arabic / English...) because it
 * measures the real glyphs.
 */
public final class TextFit {
	private TextFit() {}

	private static final GlyphLayout LAYOUT = new GlyphLayout();

	private static boolean fits(BitmapFont font, String text, float w, float h, float scale) {
		BitmapFont.BitmapFontData d = font.getData();
		float ox = d.scaleX, oy = d.scaleY;
		d.setScale(scale);
		try {
			LAYOUT.setText(font, text, Color.WHITE, w, Align.left, true);
			return LAYOUT.height <= h + 0.5f && LAYOUT.width <= w + 0.5f;
		} finally {
			d.setScale(ox, oy);
		}
	}

	/** @param base the font scale the user asked for (the result is never bigger) */
	public static synchronized float scale(BitmapFont font, String text, float w, float h, float base) {
		if (font == null || text == null || text.isEmpty() || w < 2 || h < 2 || base <= 0) return base;
		if (fits(font, text, w, h, base)) return base;
		float lo = base * 0.12f, hi = base;
		if (fits(font, text, w, h, lo) == false) return lo;
		for (int i = 0; i < 10; i++) {
			float mid = (lo + hi) * 0.5f;
			if (fits(font, text, w, h, mid)) lo = mid; else hi = mid;
		}
		return lo;
	}
}
