package com.star4droid.star2d.buttons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Everything that describes one button image. All sizes are pixels of the BASE bitmap (the image that is saved);
 * the saved 9-patch then stretches to any element size. Colours are ARGB ints, 0 = unused.
 *
 * Build one from the AI/UI parameters with {@link #fromJson}: a preset provides the look (all fields), then any
 * explicitly given field overrides it.
 */
public final class ButtonSpec {
    public static final String[] SHAPES = {"rect", "rounded", "pill", "chamfer", "circle", "blob", "triangle_left", "triangle_right", "triangle_up", "triangle_down"};
    public static final String[] GLOSS = {"none", "top_half", "top_band", "top_arc", "top_left_ellipse", "streak", "bottom_glow"};

    public String preset = "jelly";
    public String shape = "rounded";

    /** Size of the saved bitmap (shadow margin and lip included). */
    public int width = 128, height = 64;

    /** Corner radius in px; negative = {@link #radiusRatio} * the shorter side of the body. */
    public float radius = -1f, radiusRatio = 0.3f;
    /** Chamfer (cut corner) size in px; negative = {@link #chamferRatio} * body height. */
    public float chamfer = -1f, chamferRatio = 0.45f;

    /** Fill gradient, 1..3 colours along {@link #angle} (degrees: 0 = left to right, 90 = top to bottom). */
    public int[] fill = {0xFF2D8CFF};
    public float angle = 90f;

    /** Rings from the outside inwards: outline, then frame, then border, then the fill. Width 0 or colour 0 = skipped. */
    public int outlineColor = 0, frameColor = 0, borderColor = 0;
    public float outlineWidth = 0, frameWidth = 0, borderWidth = 0;

    /** Soft drop shadow (drawn from the whole silhouette, lip included). */
    public int shadowColor = 0;
    public float shadowDx = 0, shadowDy = 0, shadowBlur = 0;

    /** 3D bottom edge: a darker copy of the button shifted down by {@code lip} px. lipColor 0 = darker shade of the fill. */
    public float lip = 0;
    public int lipColor = 0;

    /** Outer glow (neon). */
    public int glowColor = 0;
    public float glowBlur = 0;

    /** Shine: style, strength 0..1, height (fraction of the fill), vertical offset (fraction), side inset px, colour. */
    public String gloss = "none";
    public float glossStrength = 0.5f, glossHeight = 0.45f, glossY = 0f, glossInset = -1f;
    public int glossColor = 0xFFFFFFFF;

    /** Soft light along the inside of the edge, and a 1-2 px bevel (light top, dark bottom). */
    public int innerGlowColor = 0;
    public float innerGlowWidth = 0;
    public float bevel = 0;

    /** Pressed look: body moved down by {@code drop} px (set by {@link #pressedVariant()}). */
    public float drop = 0;

    // ------------------------------------------------------------------------------------------------ queries

    public boolean isStretchable() {
        return shape.equals("rect") || shape.equals("rounded") || shape.equals("pill") || shape.equals("chamfer");
    }

    public ButtonSpec copy() {
        ButtonSpec s = new ButtonSpec();
        s.preset = preset;
        s.shape = shape;
        s.width = width;
        s.height = height;
        s.radius = radius;
        s.radiusRatio = radiusRatio;
        s.chamfer = chamfer;
        s.chamferRatio = chamferRatio;
        s.fill = Arrays.copyOf(fill, fill.length);
        s.angle = angle;
        s.outlineColor = outlineColor;
        s.frameColor = frameColor;
        s.borderColor = borderColor;
        s.outlineWidth = outlineWidth;
        s.frameWidth = frameWidth;
        s.borderWidth = borderWidth;
        s.shadowColor = shadowColor;
        s.shadowDx = shadowDx;
        s.shadowDy = shadowDy;
        s.shadowBlur = shadowBlur;
        s.lip = lip;
        s.lipColor = lipColor;
        s.glowColor = glowColor;
        s.glowBlur = glowBlur;
        s.gloss = gloss;
        s.glossStrength = glossStrength;
        s.glossHeight = glossHeight;
        s.glossY = glossY;
        s.glossInset = glossInset;
        s.glossColor = glossColor;
        s.innerGlowColor = innerGlowColor;
        s.innerGlowWidth = innerGlowWidth;
        s.bevel = bevel;
        s.drop = drop;
        return s;
    }

    /**
     * The same button "pressed in": same shape and size, fill darker and a little less saturated, shine and shadow reduced,
     * the 3D lip nearly gone and the whole body moved down by the lip that disappeared (so it looks pushed in, the label
     * moves with it by {@code lip - remaining lip} px). Used for the automatic name_pressed image.
     */
    public ButtonSpec pressedVariant() {
        ButtonSpec s = copy();
        for (int i = 0; i < s.fill.length; i++) s.fill[i] = ButtonColors.desaturate(ButtonColors.darken(s.fill[i], 0.15f), 0.12f);
        if (ButtonColors.used(s.lipColor)) s.lipColor = ButtonColors.darken(s.lipColor, 0.10f);
        if (ButtonColors.used(s.borderColor)) s.borderColor = ButtonColors.darken(s.borderColor, 0.10f);
        float remaining = s.lip * 0.15f;
        s.drop = s.drop + (s.lip - remaining);
        s.lip = remaining;
        // no lip at all (jelly, glass, panels): the press is a small push down instead
        if (lip < 0.5f) s.drop = s.drop + Math.max(1f, Math.min(3f, height * 0.04f));
        s.shadowDy = s.shadowDy * 0.45f;
        s.shadowBlur = s.shadowBlur * 0.7f;
        s.glossStrength = s.glossStrength * 0.55f;
        if (ButtonColors.used(s.glowColor)) s.glowColor = ButtonColors.alpha(s.glowColor, 0.5f);
        if (ButtonColors.used(s.innerGlowColor)) s.innerGlowColor = ButtonColors.darken(s.innerGlowColor, 0.1f);
        return s;
    }

    // ------------------------------------------------------------------------------------------------ json

    /** All the keys the AI may pass (documented in the tool/docs). */
    public static final String[] KEYS = {"preset", "shape", "color", "color2", "colors", "angle", "width", "height", "radius", "chamfer",
            "outline", "outline_w", "frame", "frame_w", "border", "border_w", "shadow", "shadow_dx", "shadow_dy", "shadow_blur", "lip", "lip_color",
            "glow", "glow_blur", "gloss", "gloss_strength", "gloss_height", "gloss_y", "gloss_inset", "gloss_color", "inner_glow", "inner_glow_w", "bevel"};

    /** Resolved values (no preset needed to restore it): stored inside the saved PNG so the button can be edited again. */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("preset", preset);
        o.addProperty("shape", shape);
        o.addProperty("width", width);
        o.addProperty("height", height);
        o.addProperty("radius", radius);
        o.addProperty("radius_ratio", radiusRatio);
        o.addProperty("chamfer", chamfer);
        o.addProperty("chamfer_ratio", chamferRatio);
        JsonArray f = new JsonArray();
        for (int c : fill) f.add(ButtonColors.toString(c));
        o.add("colors", f);
        o.addProperty("angle", angle);
        ring(o, "outline", outlineColor, outlineWidth);
        ring(o, "frame", frameColor, frameWidth);
        ring(o, "border", borderColor, borderWidth);
        o.addProperty("shadow", ButtonColors.toString(shadowColor));
        o.addProperty("shadow_dx", shadowDx);
        o.addProperty("shadow_dy", shadowDy);
        o.addProperty("shadow_blur", shadowBlur);
        o.addProperty("lip", lip);
        o.addProperty("lip_color", ButtonColors.toString(lipColor));
        o.addProperty("glow", ButtonColors.toString(glowColor));
        o.addProperty("glow_blur", glowBlur);
        o.addProperty("gloss", gloss);
        o.addProperty("gloss_strength", glossStrength);
        o.addProperty("gloss_height", glossHeight);
        o.addProperty("gloss_y", glossY);
        o.addProperty("gloss_inset", glossInset);
        o.addProperty("gloss_color", ButtonColors.toString(glossColor));
        o.addProperty("inner_glow", ButtonColors.toString(innerGlowColor));
        o.addProperty("inner_glow_w", innerGlowWidth);
        o.addProperty("bevel", bevel);
        o.addProperty("drop", drop);
        return o;
    }

    private static void ring(JsonObject o, String name, int color, float w) {
        o.addProperty(name, ButtonColors.toString(color));
        o.addProperty(name + "_w", w);
    }

    /**
     * Preset first (all defaults, derived from {@code color}), then every explicitly given key overrides.
     * Problems that do not stop the build (unknown key, clamped value) are added to {@code warnings}.
     * Throws IllegalArgumentException for values that cannot be used (unknown preset/shape, bad colour).
     */
    public static ButtonSpec fromJson(JsonObject o, List<String> warnings) {
        if (warnings == null) warnings = new ArrayList<>();
        String preset = str(o, "preset");
        ButtonSpec s = ButtonPresets.make(preset.isEmpty() ? ButtonPresets.DEFAULT : preset, str(o, "color"), str(o, "color2"), str(o, "shape"));

        // keys written by toJson but not listed for the AI
        List<String> known = new ArrayList<>(Arrays.asList(KEYS));
        known.addAll(Arrays.asList("radius_ratio", "chamfer_ratio", "drop", "state", "pressed"));
        for (String k : o.keySet()) {
            if (!known.contains(k.toLowerCase(Locale.ROOT))) warnings.add("unknown parameter '" + k + "' ignored (known: " + String.join(", ", KEYS) + ")");
        }

        if (o.has("colors") && o.get("colors").isJsonArray() && o.getAsJsonArray("colors").size() > 0) {
            JsonArray a = o.getAsJsonArray("colors");
            int n = Math.min(3, a.size());
            if (a.size() > 3) warnings.add("colors: only the first 3 are used");
            int[] f = new int[n];
            for (int i = 0; i < n; i++) f[i] = ButtonColors.parse(a.get(i).getAsString());
            s.fill = f;
        }
        s.angle = num(o, "angle", s.angle);
        s.width = clampInt(num(o, "width", s.width), 24, 512, "width", warnings);
        s.height = clampInt(num(o, "height", s.height), 24, 512, "height", warnings);
        s.radius = num(o, "radius", s.radius);
        s.radiusRatio = num(o, "radius_ratio", s.radiusRatio);
        s.chamfer = num(o, "chamfer", s.chamfer);
        s.chamferRatio = num(o, "chamfer_ratio", s.chamferRatio);
        s.outlineColor = col(o, "outline", s.outlineColor);
        s.outlineWidth = num(o, "outline_w", s.outlineWidth);
        s.frameColor = col(o, "frame", s.frameColor);
        s.frameWidth = num(o, "frame_w", s.frameWidth);
        s.borderColor = col(o, "border", s.borderColor);
        s.borderWidth = num(o, "border_w", s.borderWidth);
        s.shadowColor = col(o, "shadow", s.shadowColor);
        s.shadowDx = num(o, "shadow_dx", s.shadowDx);
        s.shadowDy = num(o, "shadow_dy", s.shadowDy);
        s.shadowBlur = num(o, "shadow_blur", s.shadowBlur);
        s.lip = num(o, "lip", s.lip);
        s.lipColor = col(o, "lip_color", s.lipColor);
        s.glowColor = col(o, "glow", s.glowColor);
        s.glowBlur = num(o, "glow_blur", s.glowBlur);
        if (o.has("gloss")) {
            String g = o.get("gloss").getAsString().toLowerCase(Locale.ROOT);
            if (!Arrays.asList(GLOSS).contains(g)) throw new IllegalArgumentException("gloss must be one of " + Arrays.toString(GLOSS));
            s.gloss = g;
        }
        s.glossStrength = num(o, "gloss_strength", s.glossStrength);
        s.glossHeight = num(o, "gloss_height", s.glossHeight);
        s.glossY = num(o, "gloss_y", s.glossY);
        s.glossInset = num(o, "gloss_inset", s.glossInset);
        s.glossColor = col(o, "gloss_color", s.glossColor);
        s.innerGlowColor = col(o, "inner_glow", s.innerGlowColor);
        s.innerGlowWidth = num(o, "inner_glow_w", s.innerGlowWidth);
        s.bevel = num(o, "bevel", s.bevel);
        s.drop = num(o, "drop", s.drop);
        s.sanitize(warnings);
        return s;
    }

    /** Keeps every number in a range the renderer can draw. */
    public void sanitize(List<String> warnings) {
        outlineWidth = clamp(outlineWidth, 0, 24);
        frameWidth = clamp(frameWidth, 0, 24);
        borderWidth = clamp(borderWidth, 0, 24);
        shadowBlur = clamp(shadowBlur, 0, 48);
        shadowDy = clamp(shadowDy, -24, 24);
        shadowDx = clamp(shadowDx, -24, 24);
        glowBlur = clamp(glowBlur, 0, 48);
        lip = clamp(lip, 0, Math.max(0, height / 3f));
        drop = clamp(drop, 0, 32);
        innerGlowWidth = clamp(innerGlowWidth, 0, 32);
        bevel = clamp(bevel, 0, 8);
        glossStrength = clamp(glossStrength, 0, 1);
        glossHeight = clamp(glossHeight, 0.05f, 1);
        glossY = clamp(glossY, 0, 0.9f);
        angle = clamp(angle, -360, 360);
        radiusRatio = clamp(radiusRatio, 0, 0.5f);
        chamferRatio = clamp(chamferRatio, 0, 0.5f);
        if (fill == null || fill.length == 0) fill = new int[]{0xFF2D8CFF};
        if (!Arrays.asList(SHAPES).contains(shape)) throw new IllegalArgumentException("shape must be one of " + Arrays.toString(SHAPES));
        if (!Arrays.asList(GLOSS).contains(gloss)) gloss = "none";
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(float v, int lo, int hi, String name, List<String> warnings) {
        int r = Math.round(v);
        if (r < lo || r > hi) {
            warnings.add(name + " " + r + " clamped to " + lo + ".." + hi);
            r = Math.max(lo, Math.min(hi, r));
        }
        return r;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString().trim();
    }

    private static float num(JsonObject o, String k, float def) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return def;
        try {
            return e.getAsFloat();
        } catch (Exception ex) {
            throw new IllegalArgumentException("'" + k + "' must be a number");
        }
    }

    private static int col(JsonObject o, String k, int def) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return def;
        return ButtonColors.parse(e.getAsString());
    }
}
