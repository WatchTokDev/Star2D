package com.star4droid.star2d.ui.behaviors;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.star4droid.star2d.icons.IconCatalog;
import com.star4droid.star2d.icons.IconRenderer;
import com.star4droid.star2d.ui.Ui;

import java.util.HashMap;

/** Behavior icons: ids of the icon library (assets/icons/catalog.json) drawn as vectors. */
final class BehaviorIcons {
	private BehaviorIcons() {}

	private static final HashMap<String, Bitmap> CACHE = new HashMap<>();

	static ImageView view(Context c, String iconId, int sizeDp, int color) {
		ImageView iv = new ImageView(c);
		int px = Ui.dp(c, sizeDp);
		iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
		iv.setImageBitmap(bitmap(c, iconId, color));
		return iv;
	}

	static Bitmap bitmap(Context c, String iconId, int color) {
		String key = iconId + "|" + color;
		Bitmap b = CACHE.get(key);
		if (b != null) return b;
		IconCatalog.Icon icon = null;
		try {
			icon = IconCatalog.get(c).byId(iconId);
			if (icon == null) icon = IconCatalog.get(c).byId("puzzle");
		} catch (RuntimeException ignored) {
		}
		if (icon == null) {
			b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
		} else {
			b = IconRenderer.render(icon, new IconRenderer.Options().color(color).size(96));
		}
		CACHE.put(key, b);
		return b;
	}
}
