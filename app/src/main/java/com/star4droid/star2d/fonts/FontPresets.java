package com.star4droid.star2d.fonts;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Font presets and the parameter model of the Star2D bitmap font (.s2df).
 *
 * A .s2df file is a flat JSON object (a PropertySet): {"name":..., "rtl":"true|false", "font":"/Some.ttf" (TTF/OTF relative to the .s2df's folder),
 * and any of the FreeTypeFontParameter fields listed in {@link #specs()} as STRINGS}. The game generates the bitmap font from the TTF with
 * FreeType when the text element loads it (com.star4droid.template.Utils.Utils.generateFontFrom), so there are no generated font files.
 * FreeType has no gradient / glow: looks are made from fill colour + one outline (border) + one drop shadow.
 *
 * Editor side code (not part of the game runtime). Safe to call from any thread, except {@link #generate} (GL thread).
 */
public final class FontPresets {
    private FontPresets() {}

    /** TTF/OTF files that ship inside the editor (assets/files) and may be copied into a project's fonts folder. */
    public static final String[] BUNDLED_TTF = {"DroidSans.ttf", "Parvin-Regular.ttf", "VisOpenSans.ttf"};

    public static final String DEFAULT_TTF = "DroidSans.ttf";
    /** Arabic-capable bundled font. */
    public static final String ARABIC_TTF = "Parvin-Regular.ttf";

    // ----------------------------------------------------------------- parameter model

    public static final class Spec {
        public final String key, kind, def, desc;
        public final float min, max;
        public final String[] options;

        Spec(String key, String kind, String def, float min, float max, String[] options, String desc) {
            this.key = key;
            this.kind = kind;
            this.def = def;
            this.min = min;
            this.max = max;
            this.options = options;
            this.desc = desc;
        }

        /** One line for docs / the AI: "size int 8..160 (default 16)". */
        public String describe() {
            StringBuilder sb = new StringBuilder(key).append(' ').append(kind);
            if (kind.equals("int") || kind.equals("float")) sb.append(' ').append(fmt(min)).append("..").append(fmt(max));
            if (options != null) sb.append(' ').append(String.join("|", options));
            sb.append(" (default ").append(def).append(") ").append(desc);
            return sb.toString();
        }
    }

    private static final LinkedHashMap<String, Spec> SPECS = new LinkedHashMap<>();
    private static final String[] HINTING = {"None", "Slight", "Medium", "Full", "AutoSlight", "AutoMedium", "AutoFull"};
    private static final String[] FILTER = {"Nearest", "Linear", "MipMap", "MipMapNearestNearest", "MipMapLinearNearest", "MipMapNearestLinear", "MipMapLinearLinear"};

    private static void spec(String key, String kind, String def, float min, float max, String[] options, String desc) {
        SPECS.put(key, new Spec(key, kind, def, min, max, options, desc));
    }

    static {
        spec("size", "int", "16", 8, 160, null, "glyph height in px at Font Scale 1 (bigger = sharper but a bigger texture)");
        spec("color", "color", "FFFFFFFF", 0, 0, null, "fill colour RRGGBBAA (the text element's Text Color multiplies it: keep white to tint freely)");
        spec("borderWidth", "float", "0.0", 0, 20, null, "outline thickness in px (0 = none)");
        spec("borderColor", "color", "000000FF", 0, 0, null, "outline colour RRGGBBAA");
        spec("shadowOffsetX", "int", "0", -20, 20, null, "drop shadow x in px (+ = right); both offsets 0 = no shadow");
        spec("shadowOffsetY", "int", "0", -20, 20, null, "drop shadow y in px (+ = down)");
        spec("shadowColor", "color", "000000BF", 0, 0, null, "drop shadow colour RRGGBBAA");
        spec("spaceX", "int", "0", -10, 30, null, "extra horizontal spacing between letters in px");
        spec("spaceY", "int", "0", -10, 30, null, "extra line spacing in px");
        spec("hinting", "enum", "AutoMedium", 0, 0, HINTING, "None/Slight give smoother big text, Full/Auto* sharper small text");
        spec("mono", "bool", "false", 0, 0, null, "true = no anti-aliasing (pixel look)");
        spec("kerning", "bool", "true", 0, 0, null, "pair kerning");
        spec("gamma", "float", "1.8", 0.5f, 4, null, "fill gamma");
        spec("borderGamma", "float", "1.8", 0.5f, 4, null, "outline gamma");
        spec("borderStraight", "bool", "false", 0, 0, null, "true = sharp mitered outline corners");
        spec("renderCount", "int", "2", 1, 6, null, "glyph render passes (smoothness of border/gamma)");
        spec("padTop", "int", "0", 0, 30, null, "atlas padding around glyphs, px");
        spec("padLeft", "int", "0", 0, 30, null, "atlas padding around glyphs, px");
        spec("padBottom", "int", "0", 0, 30, null, "atlas padding around glyphs, px");
        spec("padRight", "int", "0", 0, 30, null, "atlas padding around glyphs, px");
        spec("minFilter", "enum", "Nearest", 0, 0, FILTER, "texture filter when shrinking (Nearest = hard pixels, Linear = smooth)");
        spec("magFilter", "enum", "Nearest", 0, 0, FILTER, "texture filter when enlarging");
    }

    public static Collection<Spec> specs() {
        return SPECS.values();
    }

    public static Spec spec(String key) {
        return SPECS.get(key);
    }

    // ----------------------------------------------------------------- presets

    public static final class Preset {
        public final String id, name, desc, ttf;
        public final boolean rtl;
        /** Already normalized (strings exactly as stored in the .s2df). */
        public final LinkedHashMap<String, String> params;

        Preset(String id, String name, String desc, String ttf, boolean rtl, LinkedHashMap<String, String> params) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.ttf = ttf;
            this.rtl = rtl;
            this.params = params;
        }
    }

    private static List<Preset> cache;

    /** All presets (reads assets/fonts/presets.json once; needs Gdx.files, so not before the app started). */
    public static synchronized List<Preset> all() {
        if (cache == null) cache = parse(Gdx.files.internal("fonts/presets.json").readString("UTF-8"));
        return cache;
    }

    public static Preset byId(String id) {
        if (id == null) return null;
        String k = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Preset p : all()) if (p.id.equals(k)) return p;
        return null;
    }

    /** Parses a presets.json text (also used by the offline checker). Throws IllegalArgumentException on any bad value. */
    public static List<Preset> parse(String json) {
        ArrayList<Preset> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement e : root.getAsJsonArray("presets")) {
            JsonObject o = e.getAsJsonObject();
            String id = o.get("id").getAsString();
            LinkedHashMap<String, String> p = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("params").entrySet()) {
                Spec s = SPECS.get(en.getKey());
                if (s == null) throw new IllegalArgumentException("preset " + id + ": unknown parameter '" + en.getKey() + "'");
                p.put(s.key, normalizeValue(s, en.getValue().getAsString()));
            }
            out.add(new Preset(id, o.get("name").getAsString(), o.get("desc").getAsString(), o.get("ttf").getAsString(),
                    o.has("rtl") && o.get("rtl").getAsBoolean(), p));
        }
        return out;
    }

    // ----------------------------------------------------------------- values

    private static String fmt(float f) {
        return f == Math.rint(f) ? String.valueOf((int) f) : String.valueOf(f);
    }

    private static final String[][] COLOR_NAMES = {
            {"white", "FFFFFFFF"}, {"black", "000000FF"}, {"red", "FF2B2BFF"}, {"green", "39D353FF"}, {"blue", "2F6BFFFF"},
            {"yellow", "FFE14DFF"}, {"orange", "FF9A1FFF"}, {"purple", "9B5CFFFF"}, {"pink", "FF6FAEFF"}, {"cyan", "19D8FFFF"},
            {"magenta", "FF2DAFFF"}, {"gray", "9AA0A6FF"}, {"grey", "9AA0A6FF"}, {"brown", "7A4B00FF"}, {"gold", "FFD54AFF"},
            {"silver", "E3E8EEFF"}, {"navy", "1B2A6BFF"}, {"teal", "159E94FF"}, {"lime", "A6E22EFF"}, {"transparent", "00000000"}};

    /** "#RRGGBB", "#RRGGBBAA", "RRGGBBAA" or a colour name -> "RRGGBBAA" (upper case, how the editor stores colours). */
    public static String color(String v) {
        String t = v.trim();
        String low = t.toLowerCase(Locale.ROOT);
        for (String[] n : COLOR_NAMES) if (n[0].equals(low)) return n[1];
        if (t.startsWith("#")) t = t.substring(1);
        if (t.matches("[0-9a-fA-F]{6}")) return t.toUpperCase(Locale.ROOT) + "FF";
        if (t.matches("[0-9a-fA-F]{8}")) return t.toUpperCase(Locale.ROOT);
        throw new IllegalArgumentException("bad colour '" + v + "' (use #RRGGBB, #RRGGBBAA or a name like white, black, gold)");
    }

    /** Validates + clamps one value; returns the string stored in the .s2df. Throws IllegalArgumentException with a readable message. */
    public static String normalizeValue(Spec s, String raw) {
        String v = raw == null ? "" : raw.trim();
        switch (s.kind) {
            case "color":
                return color(v);
            case "bool": {
                String l = v.toLowerCase(Locale.ROOT);
                if (l.equals("true") || l.equals("1") || l.equals("yes")) return "true";
                if (l.equals("false") || l.equals("0") || l.equals("no")) return "false";
                throw new IllegalArgumentException(s.key + " must be true or false");
            }
            case "enum": {
                for (String o : s.options) if (o.equalsIgnoreCase(v)) return o;
                throw new IllegalArgumentException(s.key + " must be one of " + String.join("|", s.options));
            }
            default: {
                float f;
                try {
                    f = Float.parseFloat(v);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(s.key + " must be a number (" + fmt(s.min) + ".." + fmt(s.max) + ")");
                }
                f = Math.max(s.min, Math.min(s.max, f));
                if (s.kind.equals("int")) return String.valueOf(Math.round(f));
                return String.valueOf(Math.round(f * 100f) / 100f); // "4.0", "1.8"
            }
        }
    }

    /**
     * Preset params overridden by the caller's JSON (only keys that are real parameters; others are listed in {@code warnings}).
     * {@code ignore} = keys that belong to the tool itself (name, preset, ...), skipped silently.
     */
    public static LinkedHashMap<String, String> merge(Preset base, JsonObject given, List<String> warnings, String... ignore) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (base != null) out.putAll(base.params);
        if (given == null) return out;
        outer:
        for (Map.Entry<String, JsonElement> e : given.entrySet()) {
            for (String i : ignore) if (i.equals(e.getKey())) continue outer;
            Spec s = SPECS.get(e.getKey());
            if (s == null) {
                // be forgiving about case
                for (Spec x : SPECS.values()) if (x.key.equalsIgnoreCase(e.getKey())) s = x;
            }
            if (s == null) {
                if (warnings != null) warnings.add("ignored unknown parameter '" + e.getKey() + "' (fonts {action:'list'} lists the real ones)");
                continue;
            }
            JsonElement val = e.getValue();
            String raw = val.isJsonPrimitive() ? val.getAsString() : val.toString();
            out.put(s.key, normalizeValue(s, raw));
        }
        return out;
    }

    // ----------------------------------------------------------------- .s2df text

    /** The text of a .s2df file. {@code ttfRel} is relative to the .s2df's folder, e.g. "/DroidSans.ttf". */
    public static String s2df(String name, String ttfRel, boolean rtl, Map<String, String> params) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("rtl", rtl ? "true" : "false");
        m.put("font", ttfRel);
        m.putAll(params);
        return new Gson().toJson(m);
    }

    /** Parses an existing .s2df into name/rtl/font/params (strings), or null when it is not valid. */
    public static LinkedHashMap<String, String> readS2df(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            LinkedHashMap<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : o.entrySet())
                out.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString());
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ----------------------------------------------------------------- FreeType

    /** Whole number from "56" or "56.0" (the editor's number inputs may store either). */
    private static int i(String v) {
        return Math.round(Float.parseFloat(v.trim()));
    }

    /** FreeType parameter from stored strings. {@code scale} shrinks size/outline/shadow/spacing together (thumbnails, previews). */
    public static FreeTypeFontGenerator.FreeTypeFontParameter parameter(Map<String, String> p, float scale) {
        FreeTypeFontGenerator.FreeTypeFontParameter r = new FreeTypeFontGenerator.FreeTypeFontParameter();
        if (p.containsKey("size")) r.size = Math.max(4, Math.round(i(p.get("size")) * scale));
        if (p.containsKey("mono")) r.mono = Boolean.parseBoolean(p.get("mono"));
        if (p.containsKey("hinting")) r.hinting = FreeTypeFontGenerator.Hinting.valueOf(p.get("hinting"));
        if (p.containsKey("color")) r.color = Color.valueOf(p.get("color"));
        if (p.containsKey("gamma")) r.gamma = Float.parseFloat(p.get("gamma"));
        if (p.containsKey("renderCount")) r.renderCount = i(p.get("renderCount"));
        if (p.containsKey("borderWidth")) r.borderWidth = Float.parseFloat(p.get("borderWidth")) * scale;
        if (p.containsKey("borderColor")) r.borderColor = Color.valueOf(p.get("borderColor"));
        if (p.containsKey("borderStraight")) r.borderStraight = Boolean.parseBoolean(p.get("borderStraight"));
        if (p.containsKey("borderGamma")) r.borderGamma = Float.parseFloat(p.get("borderGamma"));
        if (p.containsKey("shadowOffsetX")) r.shadowOffsetX = Math.round(i(p.get("shadowOffsetX")) * scale);
        if (p.containsKey("shadowOffsetY")) r.shadowOffsetY = Math.round(i(p.get("shadowOffsetY")) * scale);
        if (p.containsKey("shadowColor")) r.shadowColor = Color.valueOf(p.get("shadowColor"));
        if (p.containsKey("spaceX")) r.spaceX = Math.round(i(p.get("spaceX")) * scale);
        if (p.containsKey("spaceY")) r.spaceY = Math.round(i(p.get("spaceY")) * scale);
        if (p.containsKey("padTop")) r.padTop = i(p.get("padTop"));
        if (p.containsKey("padLeft")) r.padLeft = i(p.get("padLeft"));
        if (p.containsKey("padBottom")) r.padBottom = i(p.get("padBottom"));
        if (p.containsKey("padRight")) r.padRight = i(p.get("padRight"));
        if (p.containsKey("kerning")) r.kerning = Boolean.parseBoolean(p.get("kerning"));
        if (p.containsKey("minFilter")) r.minFilter = Texture.TextureFilter.valueOf(p.get("minFilter"));
        if (p.containsKey("magFilter")) r.magFilter = Texture.TextureFilter.valueOf(p.get("magFilter"));
        return r;
    }

    /** Generates a small BitmapFont for thumbnails / the live preview. GL THREAD ONLY. The caller disposes it. */
    public static BitmapFont generate(FileHandle ttf, Map<String, String> params, float scale, String characters) {
        FreeTypeFontGenerator g = new FreeTypeFontGenerator(ttf);
        try {
            return generate(g, params, scale, characters);
        } finally {
            g.dispose();
        }
    }

    /** Same with a generator the caller keeps (several fonts from one TTF). GL THREAD ONLY. */
    public static BitmapFont generate(FreeTypeFontGenerator g, Map<String, String> params, float scale, String characters) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = parameter(params, scale);
        p.characters = characters;
        return g.generateFont(p);
    }

    /** Copies a bundled TTF into {@code dir} when it is not there yet; returns the file. */
    public static FileHandle ensureBundledTtf(FileHandle dir, String ttfName) {
        FileHandle f = dir.child(ttfName);
        if (f.exists()) return f;
        for (String b : BUNDLED_TTF) {
            if (b.equalsIgnoreCase(ttfName)) {
                dir.mkdirs();
                Gdx.files.internal("files/" + b).copyTo(f);
                return f;
            }
        }
        return f;
    }
}
