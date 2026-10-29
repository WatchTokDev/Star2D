package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Android-style 9-patch images ("name.9.png") for the game runtime and the editor. No android.* here: this class is part of the
 * template runtime sources (template/runtime-sources.txt).
 *
 * The file is read as a Pixmap, the 1px marker border is parsed ({@link NinePatchParser}) and CROPPED into its own texture
 * (so the markers never bleed in through linear filtering), then wrapped in a libgdx NinePatch. A NinePatchDrawable draws at the
 * size of the element it is set on, so the picture stretches with width/height at any time.
 *
 * Thread: creating textures needs the GL thread (same rule as {@code new Texture(...)}).
 * Cache: one entry per file (path + modified time + length); it is dropped without disposing when the GL context object changes.
 * Replaced files keep their old texture alive until {@link #clear()} so items that still use it keep drawing.
 */
public final class NinePatchLoader {
    private NinePatchLoader() {}

    public static boolean is9(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".9.png");
    }

    public static boolean is9(FileHandle file) {
        return file != null && is9(file.name());
    }

    /** One loaded 9-patch (shared; do not dispose, use {@link #drawable} for a per-item copy). */
    public static final class Loaded {
        /** The cropped picture (no border). Handy as a thumbnail. */
        public final Texture texture;
        public final NinePatch patch;
        public final NinePatchParser.Parsed info;
        final Pixmap content;
        final String stamp;

        Loaded(Texture texture, NinePatch patch, NinePatchParser.Parsed info, Pixmap content, String stamp) {
            this.texture = texture;
            this.patch = patch;
            this.info = info;
            this.content = content;
            this.stamp = stamp;
        }

        void dispose() {
            try { texture.dispose(); } catch (Throwable ignored) {}
            try { content.dispose(); } catch (Throwable ignored) {}
        }
    }

    private static final HashMap<String, Loaded> CACHE = new HashMap<>();
    private static final ArrayList<Loaded> RETIRED = new ArrayList<>();
    private static Object owner;

    private static Pixmap.Format keepFormat(Pixmap src) {
        return Pixmap.Format.RGBA8888;
    }

    private static NinePatchParser.Parsed parse(final Pixmap pm) {
        return NinePatchParser.parse(pm.getWidth(), pm.getHeight(), new NinePatchParser.Pixels() {
            @Override
            public int rgba(int x, int y) {
                return pm.getPixel(x, y);
            }
        });
    }

    /** The picture without the 1px marker border, as a new Pixmap the caller must dispose. Null when it is not a valid 9-patch. */
    private static Pixmap crop(Pixmap src, NinePatchParser.Parsed info) {
        Pixmap out = new Pixmap(info.contentW, info.contentH, keepFormat(src));
        out.setBlending(Pixmap.Blending.None);
        out.drawPixmap(src, 0, 0, 1, 1, info.contentW, info.contentH);
        return out;
    }

    /**
     * Loads (cached) the 9-patch at {@code file}. Throws when the file cannot be read or is too small to hold markers.
     * GL thread.
     */
    public static synchronized Loaded load(FileHandle file) {
        Object g = Gdx.graphics;
        if (owner != g) { // new GL context / new game: the old textures are gone, just forget them
            CACHE.clear();
            RETIRED.clear();
            owner = g;
        }
        String key = file.path();
        String stamp = file.lastModified() + ":" + file.length();
        Loaded old = CACHE.get(key);
        if (old != null && old.stamp.equals(stamp)) return old;

        Pixmap src = new Pixmap(file);
        Pixmap content = null;
        try {
            NinePatchParser.Parsed info = parse(src);
            if (!info.valid) throw new IllegalArgumentException("9-patch too small: " + file.name());
            content = crop(src, info);
            // managed = true: the texture is rebuilt from the kept pixmap after a GL context loss
            Texture tex = new Texture(new PixmapTextureData(content, null, false, false, true));
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            NinePatch patch = new NinePatch(new TextureRegion(tex), info.left, info.right, info.top, info.bottom);
            patch.setPadding(info.padLeft, info.padRight, info.padTop, info.padBottom);
            Loaded l = new Loaded(tex, patch, info, content, stamp);
            content = null; // owned by Loaded now
            if (old != null) RETIRED.add(old);
            CACHE.put(key, l);
            return l;
        } finally {
            src.dispose();
            if (content != null) content.dispose();
        }
    }

    /** A drawable for one element (own NinePatch copy sharing the texture): stretches to whatever size the element has. */
    public static NinePatchDrawable drawable(FileHandle file) {
        Loaded l = load(file);
        NinePatch copy = new NinePatch(l.patch);
        copy.setPadding(l.info.padLeft, l.info.padRight, l.info.padTop, l.info.padBottom);
        return new NinePatchDrawable(copy);
    }

    /** Disposes every cached 9-patch (call when the game / project is closed for good). */
    public static synchronized void clear() {
        for (Loaded l : CACHE.values()) l.dispose();
        for (Loaded l : RETIRED) l.dispose();
        CACHE.clear();
        RETIRED.clear();
    }
}
