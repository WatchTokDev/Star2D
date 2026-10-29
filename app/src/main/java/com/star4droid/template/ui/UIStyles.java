package com.star4droid.template.ui;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Per-widget look presets selected with the node property {@code style}. A preset is a set of drawing parameters (heights, radii,
 * color roles, glow, gloss...); every parameter can be overridden on one node by a property with the same name
 * (for example {@code trackColor}, {@code fillColor}, {@code knobSize}, {@code trackHeight}).
 * Color values are a theme role ("primary", "surfaceAlt"...) or "#rrggbb[aa]". Pure Java.
 */
public final class UIStyles {
    private UIStyles() {}

    /** Style parameters of one node = node properties (win) over the preset values over the caller's default. */
    public static final class Spec {
        private final Map<String, Object> preset;
        private final UINode node;
        public final UITheme theme;
        public final String name;

        Spec(Map<String, Object> preset, UINode node, UITheme theme, String name) {
            this.preset = preset;
            this.node = node;
            this.theme = theme;
            this.name = name;
        }

        public boolean has(String k) {
            return (node != null && node.has(k)) || preset.containsKey(k);
        }

        public float f(String k, float def) {
            if (node != null && node.has(k)) return node.num(k, def);
            Object o = preset.get(k);
            if (o instanceof Number) return ((Number) o).floatValue();
            return def;
        }

        public boolean b(String k, boolean def) {
            return f(k, def ? 1f : 0f) > 0f || (node != null && node.has(k) && node.bool(k, def));
        }

        public String s(String k, String def) {
            if (node != null && node.has(k)) return node.str(k, def);
            Object o = preset.get(k);
            return o == null ? def : o.toString();
        }

        /** RGBA8888 of a color parameter (role or hex). */
        public int color(String k, String defRole) {
            return theme.resolveColor(s(k, defRole), theme.color(defRole));
        }
    }

    private static Map<String, Object> s(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static final Map<String, Map<String, Map<String, Object>>> TABLE = new LinkedHashMap<>();

    private static void put(String type, String style, Map<String, Object> m) {
        Map<String, Map<String, Object>> t = TABLE.get(type);
        if (t == null) {
            t = new LinkedHashMap<>();
            TABLE.put(type, t);
        }
        t.put(style, m);
    }

    static {
        // ---- Slider
        put("Slider", "classic", s("trackHeight", 8f, "trackRadius", -1f, "trackColor", "surfaceAlt", "fillColor", "primary", "knobShape", "circle", "knobSize", 32f, "knobColor", "text", "knobBorder", 2f, "knobBorderColor", "border"));
        put("Slider", "jelly", s("trackHeight", 24f, "trackRadius", -1f, "trackColor", "background", "trackBorder", 3f, "borderColor", "border", "fillColor", "primary", "gloss", .55f, "knobShape", "circle", "knobSize", 44f, "knobColor", "accent", "knobBorder", 3f, "knobBorderColor", "border", "knobGloss", .6f));
        put("Slider", "neon", s("trackHeight", 6f, "trackRadius", -1f, "trackColor", "surface", "trackBorder", 1f, "borderColor", "border", "fillColor", "primary", "glow", 1f, "knobShape", "circle", "knobSize", 26f, "knobColor", "background", "knobBorder", 3f, "knobBorderColor", "primary", "knobGlow", 1f));
        put("Slider", "minimal_line", s("trackHeight", 2f, "trackRadius", -1f, "trackColor", "textDim", "fillColor", "text", "knobShape", "circle", "knobSize", 16f, "knobColor", "text"));
        put("Slider", "thick_candy", s("trackHeight", 34f, "trackRadius", -1f, "trackColor", "surfaceAlt", "trackBorder", 3f, "borderColor", "border", "fillColor", "accent", "gloss", .6f, "knobShape", "circle", "knobSize", 48f, "knobColor", "text", "knobBorder", 3f, "knobBorderColor", "border", "knobGloss", .5f));
        put("Slider", "game_bar", s("trackHeight", 26f, "trackRadius", 4f, "trackColor", "background", "trackBorder", 3f, "borderColor", "border", "fillColor", "primary", "segments", 10f, "gloss", .2f, "knobShape", "rect", "knobW", 14f, "knobH", 40f, "knobColor", "accent", "knobBorder", 2f, "knobBorderColor", "border"));
        put("Slider", "round_knob", s("trackHeight", 10f, "trackRadius", -1f, "trackColor", "surfaceAlt", "fillColor", "primary", "knobShape", "circle", "knobSize", 56f, "knobColor", "text", "knobBorder", 4f, "knobBorderColor", "primary", "knobGloss", .4f));
        // ---- Progress (same keys, no knob)
        put("Progress", "classic", s("trackHeight", 16f, "trackRadius", -1f, "trackColor", "surfaceAlt", "fillColor", "primary"));
        put("Progress", "jelly", s("trackHeight", 26f, "trackRadius", -1f, "trackColor", "background", "trackBorder", 3f, "borderColor", "border", "fillColor", "success", "gloss", .55f));
        put("Progress", "neon", s("trackHeight", 10f, "trackRadius", -1f, "trackColor", "surface", "trackBorder", 1f, "borderColor", "border", "fillColor", "primary", "glow", 1f));
        put("Progress", "minimal_line", s("trackHeight", 3f, "trackRadius", -1f, "trackColor", "textDim", "fillColor", "text"));
        put("Progress", "thick_candy", s("trackHeight", 36f, "trackRadius", -1f, "trackColor", "surfaceAlt", "trackBorder", 3f, "borderColor", "border", "fillColor", "accent", "gloss", .6f, "stripes", 1f));
        put("Progress", "game_bar", s("trackHeight", 28f, "trackRadius", 4f, "trackColor", "background", "trackBorder", 3f, "borderColor", "border", "fillColor", "primary", "segments", 10f, "gloss", .2f));
        put("Progress", "health", s("trackHeight", 28f, "trackRadius", 6f, "trackColor", "background", "trackBorder", 3f, "borderColor", "border", "fillColor", "danger", "gloss", .35f));
        put("Progress", "xp", s("trackHeight", 18f, "trackRadius", -1f, "trackColor", "background", "trackBorder", 2f, "borderColor", "border", "fillColor", "accent", "gloss", .3f, "stripes", 1f));
        // ---- Switch
        put("Switch", "classic", s("trackOnColor", "success", "trackOffColor", "surfaceAlt", "knobColor", "text", "knobScale", .8f, "trackRadius", -1f));
        put("Switch", "jelly", s("trackOnColor", "success", "trackOffColor", "background", "knobColor", "text", "knobScale", .85f, "trackRadius", -1f, "border", 3f, "borderColor", "border", "gloss", .5f));
        put("Switch", "neon", s("trackOnColor", "primary", "trackOffColor", "surface", "knobColor", "text", "knobScale", .7f, "trackRadius", -1f, "border", 1f, "borderColor", "border", "glow", 1f));
        put("Switch", "minimal", s("trackOnColor", "text", "trackOffColor", "textDim", "knobColor", "background", "knobScale", .6f, "trackRadius", -1f));
        put("Switch", "square", s("trackOnColor", "primary", "trackOffColor", "surfaceAlt", "knobColor", "text", "knobScale", .8f, "trackRadius", 4f, "knobShape", "rect", "border", 2f, "borderColor", "border"));
        // ---- Checkbox / Radio
        for (String t : new String[]{"Checkbox", "Radio"}) {
            String shape = t.equals("Radio") ? "dot" : "tick";
            put(t, "classic", s("boxColor", "surface", "checkColor", "primary", "border", 2f, "borderColor", "border", "boxRadius", 6f, "checkShape", shape));
            put(t, "jelly", s("boxColor", "background", "checkColor", "success", "border", 3f, "borderColor", "border", "boxRadius", 10f, "gloss", .5f, "checkShape", shape));
            put(t, "neon", s("boxColor", "surface", "checkColor", "primary", "border", 2f, "borderColor", "primary", "boxRadius", 6f, "glow", 1f, "checkShape", shape));
            put(t, "minimal", s("boxColor", "#00000000", "checkColor", "text", "border", 2f, "borderColor", "text", "boxRadius", 4f, "checkShape", shape));
            put(t, "square", s("boxColor", "surface", "checkColor", "accent", "border", 3f, "borderColor", "border", "boxRadius", 0f, "checkShape", t.equals("Radio") ? "dot" : "cross"));
        }
        // ---- TextField
        put("TextField", "classic", s("bgColor", "surface", "border", 2f, "borderColor", "border", "radius", 10f, "focusColor", "primary"));
        put("TextField", "jelly", s("bgColor", "background", "border", 3f, "borderColor", "border", "radius", 22f, "gloss", .25f, "focusColor", "accent"));
        put("TextField", "neon", s("bgColor", "background", "border", 2f, "borderColor", "border", "radius", 14f, "glow", 1f, "focusColor", "primary"));
        put("TextField", "minimal_line", s("bgColor", "#00000000", "border", 2f, "borderColor", "textDim", "radius", 0f, "underline", 1f, "focusColor", "text"));
        put("TextField", "dark", s("bgColor", "background", "border", 1f, "borderColor", "border", "radius", 8f, "focusColor", "primary"));
        put("TextField", "flat", s("bgColor", "surfaceAlt", "border", 0f, "radius", 6f, "focusColor", "primary"));
        // ---- Button (color variants + looks)
        for (String c : new String[]{"primary", "secondary", "accent", "success", "danger"}) put("Button", c, s("buttonColor", c));
        put("Button", "ghost", s("buttonColor", "ghost", "border", 0f, "shadow", 0f, "gloss", 0f, "bevel", 0f));
        put("Button", "flat", s("buttonColor", "surfaceAlt", "shadow", 0f, "gloss", 0f, "bevel", 0f, "border", 0f));
        put("Button", "outline", s("buttonColor", "primary", "outline", 1f, "shadow", 0f, "gloss", 0f, "bevel", 0f, "border", 3f));
        put("Button", "neon", s("buttonColor", "primary", "outline", 1f, "glow", 1f, "shadow", 0f, "gloss", 0f, "bevel", 0f, "border", 2f));
        put("Button", "glass", s("buttonColor", "text", "fillAlpha", .18f, "border", 2f, "gloss", .3f, "shadow", 0f, "bevel", 0f));
        put("Button", "pill", s("buttonColor", "primary", "shape", "pill"));
        put("Button", "jelly", s("buttonColor", "primary", "gloss", .6f, "border", 3f, "shape", "round", "shadow", 5f));
        put("Button", "soft", s("buttonColor", "surfaceAlt", "gloss", 0f, "border", 0f, "shadow", 0f, "bevel", 0f, "shape", "round"));
        put("Button", "square", s("buttonColor", "primary", "shape", "square"));
        put("Button", "hex", s("buttonColor", "accent", "shape", "cut", "border", 3f, "bevel", 4f));
        // ---- Panel
        put("Panel", "card", s("panelColor", "surface"));
        put("Panel", "solid", s("panelColor", "surfaceAlt", "shadow", 0f));
        put("Panel", "glass", s("panelColor", "text", "fillAlpha", .12f, "border", 2f, "gloss", .15f, "shadow", 0f));
        put("Panel", "outline", s("panelColor", "#00000000", "border", 3f, "shadow", 0f, "gloss", 0f));
        put("Panel", "flat", s("panelColor", "surface", "shadow", 0f, "border", 0f, "gloss", 0f, "bevel", 0f));
        put("Panel", "dark", s("panelColor", "background", "shadow", 0f));
        put("Panel", "transparent", s("panelColor", "#00000000", "border", 0f, "shadow", 0f, "gloss", 0f));
    }

    /** Preset names of a widget type, first one is the default. Empty for types without presets. */
    public static String[] names(String type) {
        Map<String, Map<String, Object>> t = TABLE.get(type);
        return t == null ? new String[0] : t.keySet().toArray(new String[0]);
    }

    public static String defaultStyle(String type) {
        switch (type) {
            case UINode.BUTTON: return "primary";
            case UINode.PANEL: return "card";
            default: {
                String[] n = names(type);
                return n.length == 0 ? "" : n[0];
            }
        }
    }

    /** Parameters of a node: its {@code style} (or the type default) plus node-level overrides. */
    public static Spec spec(UINode node, UITheme theme) {
        String style = node.str("style", "");
        if (style.isEmpty()) style = defaultStyle(node.type);
        Map<String, Map<String, Object>> t = TABLE.get(node.type);
        Map<String, Object> m = null;
        if (t != null) {
            String key = style.toLowerCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
            m = t.get(key);
            if (m == null) m = t.get(defaultStyle(node.type));
        }
        if (m == null) m = new LinkedHashMap<>();
        return new Spec(m, node, theme == null ? new UITheme() : theme, style);
    }
}
