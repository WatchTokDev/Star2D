package com.star4droid.star2d.ui.behaviors;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.icons.IconCatalog;
import com.star4droid.star2d.icons.IconRenderer;
import com.star4droid.star2d.ui.Ui;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Behavior icons: ids of the icon library (assets/icons/catalog.json) drawn as vectors.
 * Bitmaps are cached by (icon, color) and drawn on the behavior UI's background thread: a list shows its rows at once and the
 * icons pop in as soon as they are ready (normally they already are, see {@link #prewarm}).
 */
final class BehaviorIcons {
	private BehaviorIcons() {}

	private static final ConcurrentHashMap<String, Bitmap> CACHE = new ConcurrentHashMap<>();
	private static volatile boolean warmDone;

	/** True once every built-in icon is drawn in the two colors the UI uses. */
	static boolean warm() {
		return warmDone;
	}

	/** Draws the icons of the built-in behaviors (background thread). */
	static void prewarm(Context c, List<BehaviorMeta> metas) {
		for (BehaviorMeta m : metas) {
			bitmap(c, m.icon, Ui.ACCENT);
			bitmap(c, m.icon, Ui.TEXT_3);
		}
		warmDone = true;
	}

	/** An icon view of the given size; the picture is set at once when cached, else a moment later. */
	static ImageView view(Context c, String iconId, int sizeDp, int color) {
		ImageView iv = new ImageView(c);
		int px = Ui.dp(c, sizeDp);
		iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
		set(iv, iconId, color);
		return iv;
	}

	/** Puts the icon in the view (recycled views included): immediately from the cache, otherwise drawn in the background. */
	static void set(final ImageView iv, final String iconId, final int color) {
		final String key = key(iconId, color);
		iv.setTag(key);
		Bitmap b = CACHE.get(key);
		if (b != null) {
			iv.setImageBitmap(b);
			return;
		}
		iv.setImageDrawable(null);
		final Context app = iv.getContext().getApplicationContext() != null ? iv.getContext().getApplicationContext() : iv.getContext();
		BehaviorText.background(new Runnable() {
			@Override
			public void run() {
				final Bitmap r = bitmap(app, iconId, color);
				Ui.ui(new Runnable() {
					@Override
					public void run() {
						if (key.equals(iv.getTag())) iv.setImageBitmap(r);
					}
				});
			}
		});
	}

	private static String key(String iconId, int color) {
		return iconId + "|" + color;
	}

	/** Any thread. */
	static Bitmap bitmap(Context c, String iconId, int color) {
		String key = key(iconId, color);
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
		Bitmap old = CACHE.putIfAbsent(key, b);
		return old != null ? old : b;
	}
}
