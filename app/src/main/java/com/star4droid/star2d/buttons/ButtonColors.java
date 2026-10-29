package com.star4droid.star2d.buttons;

import android.graphics.Color;

import java.util.LinkedHashMap;
import java.util.Locale;

/** Colour helpers shared by presets, renderer and UI. Colours are ARGB ints; 0 (fully transparent) means "not used". */
public final class ButtonColors {
    private ButtonColors() {}

    /** Named colours the AI can pass as {@code color}. The values are the "base" hue of a preset (presets derive light/dark shades from it). */
    public static final LinkedHashMap<String, Integer> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("blue", 0xFF2D8CFF);
        NAMED.put("green", 0xFF3DBE4A);
        NAMED.put("orange", 0xFFFF8F1F);
        NAMED.put("red", 0xFFE8433F);
        NAMED.put("purple", 0xFF9B4DFF);
        NAMED.put("cyan", 0xFF14C8D8);
        NAMED.put("pink", 0xFFFF4F9A);
        NAMED.put("yellow", 0xFFFFC107);
        NAMED.put("gray", 0xFF8E9AA6);
        NAMED.put("navy", 0xFF22307A);
        NAMED.put("teal", 0xFF16B39A);
        NAMED.put("lime", 0xFF8BD21F);
        NAMED.put("white", 0xFFFFFFFF);
        NAMED.put("black", 0xFF222222);
    }

    /** Mix with white by {@code f} (0..1), keeps alpha. */
    public static int lighten(int c, float f) {
        return mix(c, 0xFFFFFFFF, f);
    }

    /** Mix with black by {@code f} (0..1), keeps alpha. */
    public static int darken(int c, float f) {
        return mix(c, 0xFF000000, f);
    }

    /** Linear mix of RGB, alpha of {@code a}. */
    public static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.argb(Color.alpha(a), r, g, bl);
    }

    /** Move the colour toward its own grey by {@code f} (0..1): less saturated, same brightness, alpha kept. */
    public static int desaturate(int c, float f) {
        int lum = Math.round(0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c));
        return mix(c, Color.argb(Color.alpha(c), lum, lum, lum), f);
    }

    public static int alpha(int c, float a) {
        return Color.argb(Math.max(0, Math.min(255, Math.round(a * 255f))), Color.red(c), Color.green(c), Color.blue(c));
    }

    public static boolean used(int c) {
        return Color.alpha(c) > 0;
    }

    /** "#RRGGBB" when opaque, else "#AARRGGBB"; "none" for 0. */
    public static String toString(int c) {
        if (c == 0) return "none";
        if (Color.alpha(c) == 255) return String.format(Locale.US, "#%06X", c & 0xFFFFFF);
        return String.format(Locale.US, "#%08X", c);
    }

    /** Accepts #RRGGBB, #AARRGGBB, RRGGBB, a colour name from {@link #NAMED} or an Android name (red...), "none"/"transparent". Throws IllegalArgumentException. */
    public static int parse(String s) {
        if (s == null) throw new IllegalArgumentException("empty colour");
        String t = s.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("transparent")) return 0;
        Integer named = NAMED.get(t.toLowerCase(Locale.ROOT));
        if (named != null) return named;
        try {
            if (t.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) t = "#" + t;
            return Color.parseColor(t);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad colour '" + s + "' (use #RRGGBB, #AARRGGBB, a name like blue/green/orange/red/purple/cyan/pink/yellow/gray/navy/teal/lime/white/black, or none)");
        }
    }
}
