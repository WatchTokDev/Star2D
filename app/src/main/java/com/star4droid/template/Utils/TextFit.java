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

	/**
	 * Auto Size: the font scale at which the text fills the box as much as possible and never leaves it (it grows or
	 * shrinks with the box; the "Font Scale" property is ignored). {@code base} is only used when the text is empty.
	 */
	public static synchronized float scale(BitmapFont font, String text, float w, float h, float base) {
		if (font == null || text == null || text.isEmpty() || w < 2 || h < 2) return base;
		BitmapFont.BitmapFontData d = font.getData();
		float lh = d.scaleY == 0 ? d.lineHeight : d.lineHeight / d.scaleY; // line height at scale 1
		if (lh <= 0) return base;
		float hi = h / lh;               // one line must fit the height
		if (fits(font, text, w, h, hi)) return hi;
		float lo = hi * 0.01f;
		for (int i = 0; i < 16; i++) {
			float mid = (lo + hi) * 0.5f;
			if (fits(font, text, w, h, mid)) lo = mid; else hi = mid;
		}
		return lo;
	}
}
