package com.star4droid.template.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Look of a whole .ui. One word (the preset) switches everything; single colors / numbers / images can override it.
 *
 * <pre>
 * "theme": {"preset":"candy", "primary":"#ff5c8a", "radius":28, "images":{"button":"ui/btn.9.png","panel":"ui/panel.9.png"}}
 * </pre>
 * Pure Java (colors are RGBA8888 ints, same as libGDX {@code Color.rgba8888}).
 */
public class UITheme {
    /** Names of the built-in presets. */
    public static final String[] PRESETS = {"jelly", "candy", "flat_3d", "glass_neon", "hex_arcade", "dark", "light", "minimal"};
    /** Color roles every preset defines. */
    public static final String[] ROLES = {"background", "surface", "surfaceAlt", "text", "textDim", "border", "primary", "secondary", "accent", "success", "danger"};
    /** Button styles = color roles used to paint a button. */
    public static final String[] BUTTON_STYLES = {"primary", "secondary", "accent", "success", "danger", "ghost", "flat"};

    /** Raw values of a built-in preset. */
    public static final class Look {
        public String name;
        public int background, surface, surfaceAlt, text, textDim, border, primary, secondary, accent, success, danger;
        /** Corner radius in px, border width, drop shadow offset, glossy highlight 0..1, 3D bottom edge px, neon glow 0..1, cut corners (hex) 0/1. */
        public float radius, borderWidth, shadow, gloss, bevel, glow;
        public boolean cut, gradient;
    }

    public String preset = "jelly";
    /** Overridden colors by role. */
    public final LinkedHashMap<String, String> colors = new LinkedHashMap<>();
    /** Overridden numbers: radius, borderWidth, shadow, gloss, bevel, glow, fontSize. */
    public final LinkedHashMap<String, Float> numbers = new LinkedHashMap<>();
    /** Image overrides, key = "button", "button.primary", "button.pressed", "panel", "slider.knob", ... value = project image path. */
    public final LinkedHashMap<String, String> images = new LinkedHashMap<>();
    /** Project font json (a .s2df/.json made by the font creator) or "" for the engine default. */
    public String font = "";

    // ------------------------------------------------------------------ presets

    private static Look look(String name, String bg, String surface, String surfaceAlt, String text, String textDim, String border,
                             String primary, String secondary, String accent, String success, String danger,
                             float radius, float bw, float shadow, float gloss, float bevel, float glow, boolean cut, boolean gradient) {
        Look l = new Look();
        l.name = name;
        l.background = parseColor(bg);
        l.surface = parseColor(surface);
        l.surfaceAlt = parseColor(surfaceAlt);
        l.text = parseColor(text);
        l.textDim = parseColor(textDim);
        l.border = parseColor(border);
        l.primary = parseColor(primary);
        l.secondary = parseColor(secondary);
        l.accent = parseColor(accent);
        l.success = parseColor(success);
        l.danger = parseColor(danger);
        l.radius = radius;
        l.borderWidth = bw;
        l.shadow = shadow;
        l.gloss = gloss;
        l.bevel = bevel;
        l.glow = glow;
        l.cut = cut;
        l.gradient = gradient;
        return l;
    }

    private static final Map<String, Look> LOOKS = new LinkedHashMap<>();

    static {
        //                  name         background  surface    surfaceAlt  text       textDim    border     primary    secondary  accent     success    danger     radius bw  shadow gloss bevel glow  cut    gradient
        LOOKS.put("jelly", look("jelly", "#2b1b4d", "#4a2f86", "#5d3ea3", "#ffffff", "#cdbff0", "#1d1038", "#35c46a", "#4aa8ff", "#ffb02e", "#35c46a", "#ff4d5e", 22, 3, 5, .55f, 0, 0, false, true));
        LOOKS.put("candy", look("candy", "#fff1f6", "#ffd9e8", "#ffc2dc", "#5a2142", "#a8708f", "#e86aa2", "#ff5c9a", "#8f7bff", "#ffc83d", "#4fd1a1", "#ff6b6b", 26, 3, 4, .6f, 0, 0, false, true));
        LOOKS.put("flat_3d", look("flat_3d", "#e9eef5", "#ffffff", "#e4eaf3", "#1f2a3d", "#6d7b93", "#c4cedd", "#3b82f6", "#64748b", "#f59e0b", "#22c55e", "#ef4444", 12, 0, 3, 0f, 6, 0, false, false));
        LOOKS.put("glass_neon", look("glass_neon", "#070b1a", "#12183a", "#1b2350", "#e8f0ff", "#8d9bd1", "#38e8ff", "#00e5ff", "#7c4dff", "#ff2bd6", "#39ff88", "#ff3b5c", 18, 2, 0, .25f, 0, 1f, false, true));
        LOOKS.put("hex_arcade", look("hex_arcade", "#10131c", "#1c2233", "#252d44", "#f4f6ff", "#8b94b5", "#ffd23f", "#ff9f1c", "#2ec4b6", "#ffd23f", "#6bd425", "#e63946", 14, 3, 4, .2f, 4, 0, true, false));
        LOOKS.put("dark", look("dark", "#14161b", "#1e2128", "#2a2e38", "#f1f3f7", "#8d94a5", "#3a3f4b", "#4c8dff", "#3a3f4b", "#ffb347", "#3ecf8e", "#ff5d5d", 10, 1, 0, 0f, 0, 0, false, false));
        LOOKS.put("light", look("light", "#f6f7f9", "#ffffff", "#eceef2", "#1a1d24", "#6b7280", "#d5d9e0", "#2563eb", "#6b7280", "#d97706", "#16a34a", "#dc2626", 10, 1, 0, 0f, 0, 0, false, false));
        LOOKS.put("minimal", look("minimal", "#00000000", "#00000055", "#00000088", "#ffffff", "#c8c8c8", "#ffffff66", "#ffffff22", "#ffffff22", "#ffffff33", "#ffffff22", "#ff000044", 8, 1, 0, 0f, 0, 0, false, false));
    }

    public static Look look(String name) {
        Look l = name == null ? null : LOOKS.get(name.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_"));
        return l == null ? LOOKS.get("jelly") : l;
    }

    public static boolean isPreset(String name) {
        return name != null && LOOKS.containsKey(name.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_"));
    }

    public Look look() {
        return look(preset);
    }

    // ------------------------------------------------------------------ per-node theme

    private transient java.util.HashMap<String, UITheme> variants;

    /** A theme that uses another preset but keeps this one's font (one is made per preset and reused). */
    public UITheme variant(String presetName) {
        if (presetName == null || presetName.isEmpty() || !isPreset(presetName) || presetName.equalsIgnoreCase(preset) && colors.isEmpty() && numbers.isEmpty() && images.isEmpty())
            return this;
        if (variants == null) variants = new java.util.HashMap<>();
        String key = presetName.toLowerCase(Locale.ROOT) + "|" + font;
        UITheme v = variants.get(key);
        if (v == null) {
            v = new UITheme();
            v.preset = presetName;
            v.font = font;
            variants.put(key, v);
        }
        return v;
    }

    /**
     * The theme to use for a node: the {@code theme} setting of the node (a preset name) or of its nearest parent that has one,
     * otherwise {@code base} (the whole document's theme). A node with its own theme ignores the document's color / number overrides.
     */
    public static UITheme forNode(UINode n, UITheme base) {
        if (base == null) base = new UITheme();
        for (UINode p = n; p != null; p = p.parent) {
            if (p.has("theme")) {
                String t = p.str("theme", "").trim();
                if (!t.isEmpty() && isPreset(t)) return base.variant(t);
            }
        }
        return base;
    }

    // ------------------------------------------------------------------ resolved values

    /** RGBA8888 color of a role ("primary", "text", ...) honouring overrides. Unknown roles return the text color. */
    public int color(String role) {
        if (role == null) role = "text";
        String o = colors.get(role);
        if (o != null) return parseColor(o);
        Look l = look();
        switch (role) {
            case "background": return l.background;
            case "surface": return l.surface;
            case "surfaceAlt": return l.surfaceAlt;
            case "text": return l.text;
            case "textDim": return l.textDim;
            case "border": return l.border;
            case "primary": return l.primary;
            case "secondary": return l.secondary;
            case "accent": return l.accent;
            case "success": return l.success;
            case "danger": return l.danger;
            case "ghost": return 0x00000000;
            case "flat": return l.surfaceAlt;
            default: return l.text;
        }
    }

    /** Any "#rrggbb", "#rrggbbaa" or a role name. */
    public int resolveColor(String spec, int def) {
        if (spec == null || spec.trim().isEmpty()) return def;
        String s = spec.trim();
        if (s.startsWith("#") || s.matches("[0-9a-fA-F]{6,8}")) return parseColor(s);
        for (String r : ROLES) if (r.equalsIgnoreCase(s)) return color(r);
        return def;
    }

    public float number(String key) {
        Float o = numbers.get(key);
        if (o != null) return o;
        Look l = look();
        switch (key) {
            case "radius": return l.radius;
            case "borderWidth": return l.borderWidth;
            case "shadow": return l.shadow;
            case "gloss": return l.gloss;
            case "bevel": return l.bevel;
            case "glow": return l.glow;
            case "fontSize": return 28f;
            default: return 0f;
        }
    }

    public boolean cutCorners() {
        Float o = numbers.get("cut");
        return o != null ? o > 0 : look().cut;
    }

    public boolean gradient() {
        Float o = numbers.get("gradient");
        return o != null ? o > 0 : look().gradient;
    }

    /**
     * Project image for a widget part. Tries "kind.style" then "kind"; for pressed twins "kind.style.pressed" / "kind.pressed".
     * @return path or null (draw the built-in look instead)
     */
    public String image(String kind, String style, boolean pressed) {
        String suffix = pressed ? ".pressed" : "";
        if (style != null && !style.isEmpty()) {
            String s = images.get(kind + "." + style + suffix);
            if (s != null && !s.isEmpty()) return s;
        }
        String s = images.get(kind + suffix);
        if (s != null && !s.isEmpty()) return s;
        return null;
    }

    /** Stable text of everything that changes how widgets look (used as a cache key). */
    public String signature() {
        return preset + "|" + colors + "|" + numbers + "|" + images + "|" + font;
    }

    // ------------------------------------------------------------------ JSON

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("preset", preset);
        for (Map.Entry<String, String> e : colors.entrySet()) o.addProperty(e.getKey(), e.getValue());
        for (Map.Entry<String, Float> e : numbers.entrySet()) o.addProperty(e.getKey(), e.getValue());
        if (font != null && !font.isEmpty()) o.addProperty("font", font);
        if (!images.isEmpty()) {
            JsonObject im = new JsonObject();
            for (Map.Entry<String, String> e : images.entrySet()) im.addProperty(e.getKey(), e.getValue());
            o.add("images", im);
        }
        return o;
    }

    public static UITheme fromJson(JsonObject o) {
        UITheme t = new UITheme();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = e.getKey();
            JsonElement v = e.getValue();
            if (v == null || v.isJsonNull()) continue;
            if (k.equals("preset")) {
                t.preset = v.getAsString();
                if (!isPreset(t.preset)) t.preset = "jelly";
            } else if (k.equals("font")) {
                t.font = v.getAsString();
            } else if (k.equals("images") && v.isJsonObject()) {
                for (Map.Entry<String, JsonElement> im : v.getAsJsonObject().entrySet())
                    if (im.getValue().isJsonPrimitive()) t.images.put(im.getKey(), im.getValue().getAsString());
            } else if (v.isJsonPrimitive()) {
                boolean isRole = false;
                for (String r : ROLES) if (r.equals(k)) isRole = true;
                if (isRole) t.colors.put(k, v.getAsString());
                else {
                    try {
                        if (v.getAsJsonPrimitive().isBoolean()) t.numbers.put(k, v.getAsBoolean() ? 1f : 0f);
                        else t.numbers.put(k, v.getAsFloat());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return t;
    }

    // ------------------------------------------------------------------ colors

    /** "#rgb", "#rrggbb", "#rrggbbaa" (with or without #) to RGBA8888. Bad input gives opaque white. */
    public static int parseColor(String s) {
        if (s == null) return 0xffffffff;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1), 16) * 17, g = Integer.parseInt(s.substring(1, 2), 16) * 17, b = Integer.parseInt(s.substring(2, 3), 16) * 17;
                return rgba(r, g, b, 255);
            }
            if (s.length() == 6) {
                long v = Long.parseLong(s, 16);
                return (int) ((v << 8) | 0xff);
            }
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (Exception ignored) {
        }
        return 0xffffffff;
    }

    public static int rgba(int r, int g, int b, int a) {
        return ((r & 255) << 24) | ((g & 255) << 16) | ((b & 255) << 8) | (a & 255);
    }

    public static String toHex(int rgba) {
        int a = rgba & 255;
        String h = String.format(Locale.ROOT, "#%06x", (rgba >>> 8) & 0xffffff);
        return a == 255 ? h : h + String.format(Locale.ROOT, "%02x", a);
    }

    /** Mixes two RGBA8888 colors. t=0 gives a, t=1 gives b. */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int g = (int) (((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int bl = (int) (((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int al = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return rgba(r, g, bl, al);
    }

    public static int lighten(int c, float t) {
        return mix(c, 0xffffff00 | (c & 255), t);
    }

    public static int darken(int c, float t) {
        return mix(c, 0x00000000 | (c & 255), t);
    }

    /** Readable text color on top of {@code bg}: white on dark colors, a very dark tone on light ones. */
    public static int onColor(int bg, UITheme th) {
        float lum = 0.299f * ((bg >>> 24) & 255) + 0.587f * ((bg >>> 16) & 255) + 0.114f * ((bg >>> 8) & 255);
        return lum > 170 ? 0x1c1a24ff : 0xffffffff;
    }

    public static int withAlpha(int c, float a) {
        return (c & 0xffffff00) | Math.max(0, Math.min(255, Math.round(a * 255)));
    }
}
