package com.star4droid.star2d.buttons;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Named looks. A preset is nothing but default values of a {@link ButtonSpec}, derived from one base colour:
 * pass {@code color} (hex or a name) to get the same look in another hue, {@code color2} for a custom gradient end.
 * The looks reproduce the reference UI kits: flat_3d (Duolingo style), glass_neon, hex_arcade, jelly, candy.
 */
public final class ButtonPresets {
    private ButtonPresets() {}

    public static final String DEFAULT = "jelly";

    private interface Look { void apply(ButtonSpec s, int c); }

    private static final class Def {
        final String id, desc, shape;
        final int color;
        final int w, h;
        final Look look;

        Def(String id, String desc, int color, String shape, int w, int h, Look look) {
            this.id = id;
            this.desc = desc;
            this.color = color;
            this.shape = shape;
            this.w = w;
            this.h = h;
            this.look = look;
        }
    }

    private static final LinkedHashMap<String, Def> DEFS = new LinkedHashMap<>();

    private static void add(String id, String desc, int color, String shape, int w, int h, Look look) {
        DEFS.put(id, new Def(id, desc, color, shape, w, h, look));
    }

    // ------------------------------------------------------------------------------------------ looks

    private static final Look FLAT_3D = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.36f;
        s.fill = new int[]{c};
        s.lip = 6;
        s.lipColor = ButtonColors.darken(c, 0.17f);
    };

    private static final Look GLASS = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.16f;
        s.fill = new int[]{ButtonColors.mix(0xFF4D4D4D, c, 0.10f), ButtonColors.mix(0xFF2A2A2A, c, 0.10f), ButtonColors.mix(0xFF191919, c, 0.12f)};
        s.outlineColor = 0xFF0B0B0B;
        s.outlineWidth = 2;
        s.borderColor = c;
        s.borderWidth = 2;
        s.glowColor = ButtonColors.alpha(c, 0.55f);
        s.glowBlur = 5;
        s.innerGlowColor = ButtonColors.alpha(c, 0.35f);
        s.innerGlowWidth = 5;
        s.gloss = "top_half";
        s.glossStrength = 0.20f;
        s.glossHeight = 0.48f;
        s.shadowColor = 0x66000000;
        s.shadowDy = 2;
        s.shadowBlur = 3;
    };

    private static final Look HEX = (s, c) -> {
        s.shape = "chamfer";
        s.chamferRatio = 0.46f;
        s.fill = new int[]{ButtonColors.lighten(c, 0.35f), c, ButtonColors.darken(c, 0.10f)};
        s.outlineColor = ButtonColors.lighten(ButtonColors.mix(c, 0xFFFF8FA3, 0.5f), 0.35f);
        s.outlineWidth = 2;
        s.frameColor = 0xFF111111;
        s.frameWidth = 4;
        s.gloss = "top_band";
        s.glossStrength = 0.30f;
        s.glossHeight = 0.32f;
        s.bevel = 1;
        s.shadowColor = 0x66000000;
        s.shadowDy = 3;
        s.shadowBlur = 3;
    };

    private static final Look JELLY = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.32f;
        s.fill = new int[]{ButtonColors.lighten(c, 0.30f), c, ButtonColors.darken(c, 0.10f)};
        s.outlineColor = 0xFFFFFFFF;
        s.outlineWidth = 4;
        s.innerGlowColor = ButtonColors.alpha(ButtonColors.lighten(c, 0.6f), 0.6f);
        s.innerGlowWidth = 5;
        s.gloss = "top_band";
        s.glossStrength = 0.55f;
        s.glossHeight = 0.38f;
        s.shadowColor = 0x55000000;
        s.shadowDy = 3;
        s.shadowBlur = 3;
    };

    private static final Look CANDY = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.5f;
        s.fill = new int[]{ButtonColors.lighten(c, 0.18f), c, ButtonColors.darken(c, 0.10f)};
        s.outlineColor = 0xFFFFFFFF;
        s.outlineWidth = 5;
        s.borderColor = ButtonColors.alpha(ButtonColors.lighten(c, 0.45f), 0.8f);
        s.borderWidth = 2;
        s.gloss = "top_arc";
        s.glossStrength = 0.22f;
        s.glossHeight = 0.5f;
        s.shadowColor = 0x40000000;
        s.shadowDy = 2;
        s.shadowBlur = 2;
    };

    private static final Look FLAT = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.22f;
        s.fill = new int[]{c};
        s.shadowColor = 0x40000000;
        s.shadowDy = 2;
        s.shadowBlur = 2;
    };

    private static final Look GHOST = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.22f;
        s.fill = new int[]{ButtonColors.alpha(c, 0.14f)};
        s.outlineColor = c;
        s.outlineWidth = 3;
    };

    private static final Look PANEL_DARK = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.12f;
        s.fill = new int[]{ButtonColors.lighten(c, 0.05f), ButtonColors.darken(c, 0.12f)};
        s.outlineColor = ButtonColors.lighten(c, 0.25f);
        s.outlineWidth = 2;
        s.shadowColor = 0x66000000;
        s.shadowDy = 4;
        s.shadowBlur = 6;
    };

    private static final Look PANEL_LIGHT = (s, c) -> {
        s.shape = "rounded";
        s.radiusRatio = 0.12f;
        s.fill = new int[]{ButtonColors.lighten(c, 0.6f), c};
        s.outlineColor = ButtonColors.darken(c, 0.12f);
        s.outlineWidth = 2;
        s.shadowColor = 0x33000000;
        s.shadowDy = 3;
        s.shadowBlur = 5;
    };

    static {
        add("jelly", "glossy rounded button with white outline (jelly UI kit)", 0xFF2D8CFF, "rounded", 128, 64, JELLY);
        add("candy", "pill with thick white outline and soft vertical gradient", 0xFFE8433F, "rounded", 128, 56, CANDY);
        add("flat_3d", "flat solid button with a darker 3D bottom edge (Duolingo style)", 0xFF1CB0F6, "rounded", 128, 64, FLAT_3D);
        add("glass_neon", "dark glass button, thin glowing coloured border, shiny top half", 0xFFFFA726, "rounded", 128, 56, GLASS);
        add("hex_arcade", "arcade hexagon, gradient fill, black frame and pink outline", 0xFFFF8F1F, "chamfer", 160, 64, HEX);
        add("flat", "simple solid rounded rectangle", 0xFF2D8CFF, "rounded", 128, 56, FLAT);
        add("ghost", "transparent button with a coloured outline", 0xFFFFFFFF, "rounded", 128, 56, GHOST);
        add("panel_dark", "dark panel / dialog / card background", 0xFF2B2F36, "rounded", 192, 128, PANEL_DARK);
        add("panel_light", "light panel / dialog / card background", 0xFFF1F3F6, "rounded", 192, 128, PANEL_LIGHT);
        // jelly shapes (all fixed shapes are saved as normal PNG: keep their aspect)
        add("jelly_pill", "jelly, fully rounded wide pill", 0xFF2D8CFF, "pill", 160, 56, JELLY);
        add("jelly_square", "jelly, rounded square (stretches)", 0xFF2D8CFF, "rounded", 96, 96, JELLY);
        add("jelly_circle", "jelly round button (fixed shape: use a square element)", 0xFF2D8CFF, "circle", 96, 96, JELLY);
        add("jelly_blob", "jelly rounded pentagon (fixed shape)", 0xFF3DBE4A, "blob", 96, 96, JELLY);
        add("jelly_arrow_left", "jelly rounded triangle pointing left (fixed shape)", 0xFFFF8F1F, "triangle_left", 96, 96, JELLY);
        add("jelly_arrow_right", "jelly rounded triangle pointing right / play (fixed shape)", 0xFFFF8F1F, "triangle_right", 96, 96, JELLY);
    }

    // ------------------------------------------------------------------------------------------ api

    public static List<String> ids() {
        return new ArrayList<>(DEFS.keySet());
    }

    public static boolean has(String id) {
        return DEFS.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public static String describe(String id) {
        Def d = DEFS.get(id);
        return d == null ? "" : d.desc;
    }

    public static int defaultColor(String id) {
        Def d = DEFS.get(id);
        return d == null ? 0xFF2D8CFF : d.color;
    }

    /** Preset values for {@code preset}, derived from {@code color} (null/empty = the preset's own), optional gradient end {@code color2} and optional shape override. */
    public static ButtonSpec make(String preset, String color, String color2, String shape) {
        String id = preset == null || preset.trim().isEmpty() ? DEFAULT : preset.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        Def d = DEFS.get(id);
        if (d == null) throw new IllegalArgumentException("unknown preset '" + preset + "'. Presets: " + String.join(", ", DEFS.keySet()));
        int c = color == null || color.isEmpty() ? d.color : ButtonColors.parse(color);
        if (c == 0) throw new IllegalArgumentException("color cannot be 'none'");
        ButtonSpec s = new ButtonSpec();
        s.preset = d.id;
        s.width = d.w;
        s.height = d.h;
        d.look.apply(s, c);
        s.shape = d.shape;
        applyShapeDefaults(s, d.shape, true);
        if (shape != null && !shape.isEmpty()) {
            String sh = shape.trim().toLowerCase(Locale.ROOT);
            if (!Arrays.asList(ButtonSpec.SHAPES).contains(sh)) throw new IllegalArgumentException("unknown shape '" + shape + "'. Shapes: " + String.join(", ", ButtonSpec.SHAPES));
            if (!sh.equals(s.shape)) applyShapeDefaults(s, sh, false);
        }
        if (color2 != null && !color2.isEmpty()) {
            int c2 = ButtonColors.parse(color2);
            s.fill = new int[]{c, c2};
        }
        return s;
    }

    /** Sizes/rounding that make a shape look right. {@code fromPreset}: the preset already chose size and rounding. */
    private static void applyShapeDefaults(ButtonSpec s, String shape, boolean fromPreset) {
        s.shape = shape;
        switch (shape) {
            case "rect":
                s.radius = 0;
                break;
            case "rounded":
                if (!fromPreset) {
                    s.radius = -1;
                    if (s.radiusRatio > 0.45f || s.radiusRatio < 0.05f) s.radiusRatio = 0.3f;
                }
                break;
            case "pill":
                s.radius = -1;
                s.radiusRatio = 0.5f;
                if (!fromPreset && s.width < s.height * 1.6f) s.width = Math.round(s.height * 2.4f);
                break;
            case "chamfer":
                if (!fromPreset) {
                    s.chamfer = -1;
                    s.chamferRatio = 0.46f;
                    if (s.width < s.height * 1.6f) s.width = Math.round(s.height * 2.4f);
                }
                break;
            case "circle":
            case "blob":
            case "triangle_left":
            case "triangle_right":
            case "triangle_up":
            case "triangle_down":
                if (!fromPreset || s.width != s.height) {
                    int side = fromPreset ? Math.max(s.width, s.height) : 96;
                    s.width = side;
                    s.height = side;
                }
                s.radius = -1;
                s.radiusRatio = shape.equals("circle") ? 0.5f : 0.14f;
                s.lip = 0;
                break;
            default:
                break;
        }
    }

    /** Switch an existing spec to another shape (size and rounding adapt, everything else stays). Used by the maker UI. */
    public static void changeShape(ButtonSpec s, String shape) {
        if (!Arrays.asList(ButtonSpec.SHAPES).contains(shape)) throw new IllegalArgumentException("unknown shape '" + shape + "'");
        applyShapeDefaults(s, shape, false);
    }

    /** A fresh spec of {@code preset} in the hue {@code baseColor}; used by the UI to re-colour (copy the colour fields it needs). */
    public static ButtonSpec makeWithColor(String preset, int baseColor) {
        return make(preset, ButtonColors.toString(baseColor), null, null);
    }

    /** Short catalog for the AI / UI: ids with one line each, the shapes, colour names, gloss styles. */
    public static JsonObject catalog() {
        JsonObject o = new JsonObject();
        JsonArray presets = new JsonArray();
        for (Def d : DEFS.values()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", d.id);
            p.addProperty("desc", d.desc);
            p.addProperty("shape", d.shape);
            p.addProperty("base_size", d.w + "x" + d.h);
            p.addProperty("default_color", ButtonColors.toString(d.color));
            presets.add(p);
        }
        o.add("presets", presets);
        JsonArray shapes = new JsonArray();
        for (String s : ButtonSpec.SHAPES) shapes.add(s);
        o.add("shapes", shapes);
        JsonArray colors = new JsonArray();
        for (String n : ButtonColors.NAMED.keySet()) colors.add(n);
        o.add("color_names", colors);
        JsonArray gloss = new JsonArray();
        for (String s : ButtonSpec.GLOSS) gloss.add(s);
        o.add("gloss_styles", gloss);
        return o;
    }
}
