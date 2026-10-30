package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.Project;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Images merged into tileset pages at export time ("Merge images into tilesets"). The export writes
 * files/_atlas.json: { "pages": ["_atlas_0.png", ...], "images": { "folder/name.png": [page, x, y, w, h] } }
 * and removes the merged originals. Everything that used to load images/folder/name.png asks this index first.
 * Without the file (always the case in the editor) this class does nothing.
 */
public class AtlasIndex {
	private static final Map<String, int[]> images = new HashMap<>();
	private static String[] pages = new String[0];
	private static String loadedFor;

	/** Reads the index of the project (once per project). */
	public static void init(Project project) {
		String key = project.getImagesPath();
		if (key.equals(loadedFor)) return;
		loadedFor = key;
		images.clear();
		pages = new String[0];
		try {
			File f = new File(project.get("files") + "/_atlas.json");
			if (!f.isFile()) return;
			Map<String, Object> m = new Gson().fromJson(Gdx.files.absolute(f.getAbsolutePath()).readString(), new TypeToken<Map<String, Object>>() {}.getType());
			java.util.List<?> p = (java.util.List<?>) m.get("pages");
			pages = new String[p.size()];
			for (int i = 0; i < pages.length; i++) pages[i] = String.valueOf(p.get(i));
			Map<?, ?> im = (Map<?, ?>) m.get("images");
			for (Map.Entry<?, ?> e : im.entrySet()) {
				java.util.List<?> r = (java.util.List<?>) e.getValue();
				int[] a = new int[5];
				for (int i = 0; i < 5; i++) a[i] = ((Number) r.get(i)).intValue();
				images.put(String.valueOf(e.getKey()), a);
			}
		} catch (Throwable t) {
			images.clear();
		}
	}

	public static boolean isEmpty() { return images.isEmpty(); }

	/** [page, x, y, w, h] of an image path (absolute path inside the project, or relative to images/), or null. */
	public static int[] find(String path) {
		if (images.isEmpty() || path == null) return null;
		String p = path.replace('\\', '/');
		int i = p.indexOf("images/");
		while (i >= 0) {
			int[] r = images.get(p.substring(i + 7));
			if (r != null) return r;
			i = p.indexOf("images/", i + 1);
		}
		return images.get(p);
	}

	/** The picture of a merged image (optionally only a [x,y,w,h] part of it), or null when the image is not merged. */
	public static Drawable drawable(com.star4droid.template.Utils.ProjectAssetLoader loader, Project project, String path, int[] part) {
		int[] r = find(path);
		if (r == null) return null;
		String page = project.getImagesPath() + pages[r[0]];
		if (!loader.isLoaded(page)) return null;
		Texture tex = loader.get(page, Texture.class);
		if (part != null) return new TextureRegionDrawable(new TextureRegion(tex, r[1] + part[0], r[2] + part[1], part[2], part[3]));
		return new TextureRegionDrawable(new TextureRegion(tex, r[1], r[2], r[3], r[4]));
	}
}
