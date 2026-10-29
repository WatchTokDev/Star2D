package com.star4droid.star2d.icons;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Draws an {@link IconCatalog.Icon} into a bitmap / PNG. Safe on any thread (every call works on its own copy of the path).
 * Default look: white glyph on a transparent background, so the element's Tint property (or a colour chosen here) recolours it.
 */
public final class IconRenderer {
    private IconRenderer() {}

    public static final int MIN_SIZE = 16, MAX_SIZE = 512, DEFAULT_SIZE = 64;

    /** Rendering options. All fields have sensible defaults. */
    public static final class Options {
        /** Glyph colour, ARGB. White by default. */
        public int color = Color.WHITE;
        /** Edge length in pixels (the bitmap is square). */
        public int size = DEFAULT_SIZE;
        /** Empty border around the glyph as a fraction of the size (0..0.4). The 24x24 glyphs already carry ~2px of their own. */
        public float padding = 0f;
        /** Background shape: 0 none, 1 circle, 2 rounded square. */
        public int background = 0;
        public int backgroundColor = 0xFF2196F3;
        /** Soft drop shadow under the glyph. */
        public boolean shadow = false;

        public Options color(int c) { this.color = c; return this; }
        public Options size(int s) { this.size = s; return this; }
        public Options padding(float p) { this.padding = p; return this; }
        public Options background(int shape, int color) { this.background = shape; this.backgroundColor = color; return this; }
        public Options shadow(boolean s) { this.shadow = s; return this; }
    }

    public static int clampSize(int s) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, s));
    }

    public static Bitmap render(IconCatalog.Icon icon, Options o) {
        int size = clampSize(o.size);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        float pad = Math.max(0f, Math.min(0.4f, o.padding)) * size;
        if (o.background != 0) {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(o.backgroundColor);
            if (o.background == 1) cv.drawCircle(size / 2f, size / 2f, size / 2f, bg);
            else cv.drawRoundRect(new RectF(0, 0, size, size), size * 0.22f, size * 0.22f, bg);
            // inside a background the glyph needs room: at least 18% on every side
            pad = Math.max(pad, size * 0.18f);
        }
        Path p = icon.path();
        if (p == null) return bmp;
        Path q = new Path(p);
        float scale = (size - 2 * pad) / (float) icon.viewBox;
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(pad, pad);
        q.transform(m);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        if (o.shadow) {
            float r = Math.max(1f, size * 0.04f);
            paint.setShadowLayer(r, 0, r * 0.6f, 0x99000000);
        }
        paint.setColor(o.color);
        cv.drawPath(q, paint);
        return bmp;
    }

    public static byte[] png(IconCatalog.Icon icon, Options o) {
        Bitmap b = render(icon, o);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bo);
        b.recycle();
        return bo.toByteArray();
    }

    /** Writes the PNG to {@code target} (parent folders are created). Returns the number of bytes. */
    public static int writePng(IconCatalog.Icon icon, Options o, File target) throws Exception {
        byte[] data = png(icon, o);
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(data);
        }
        return data.length;
    }

    /** Names the user may type: letters, digits, underscore; no extension. Falls back to the icon's default name. */
    public static String cleanName(String name, IconCatalog.Icon icon) {
        String n = name == null ? "" : name.trim();
        if (n.toLowerCase().endsWith(".png")) n = n.substring(0, n.length() - 4);
        n = n.replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return n.isEmpty() ? icon.defaultName() : n;
    }

    /** Parses "#RRGGBB", "RRGGBB", "#AARRGGBB", "red"... Returns white for empty input; throws IllegalArgumentException when unknown. */
    public static int parseColor(String s) {
        if (s == null || s.trim().isEmpty()) return Color.WHITE;
        String t = s.trim();
        switch (t.toLowerCase()) {
            case "gold": return 0xFFFFD700;
            case "orange": return 0xFFFFA500;
            case "pink": return 0xFFFF69B4;
            case "brown": return 0xFF8D6E63;
            case "transparent": return Color.TRANSPARENT;
            default: break;
        }
        if (t.matches("[0-9a-fA-F]{6}") || t.matches("[0-9a-fA-F]{8}")) t = "#" + t;
        try {
            return Color.parseColor(t);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad colour '" + s + "' (use #RRGGBB or a name like white, red, gold)");
        }
    }
}
