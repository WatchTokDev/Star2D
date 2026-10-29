package com.star4droid.star2d.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Building blocks of the game templates: element / block / image builders, the layer (z) policy, exact text metrics and the
 * {@link Plan} that collects everything one template wants to create.
 *
 * <p>PURE JAVA on purpose (Gson only, no Android classes): the offline harness in tools/verify_templates.py compiles this file
 * (and the template generators) with plain javac, builds every template for portrait and landscape and checks the result against
 * the real block catalog, the element tables and the engine's generated Java. Keep it that way.
 */
final class TemplateKit {
    private TemplateKit() {}

    // ------------------------------------------------------------------ layer policy
    // Draw order is the GLOBAL z of ALL elements (UI layer and world together, equal z: world first). z must be >= 0 (negative z crashes).
    // A background must therefore be the lowest z, never above the things it is behind.
    static final int Z_BG = 0, Z_GROUND = 10, Z_ITEM = 20, Z_ACTOR = 30, Z_FX = 40, Z_TAP = 50, Z_CTRL = 100, Z_HUD = 110, Z_JOY = 120, Z_OVERLAY = 200;

    /** px per world unit of the engine (StageImp.WORLD_SCALE). */
    static final double WORLD_SCALE = 0.25;

    // ------------------------------------------------------------------ numbers / json

    static JsonElement n(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e9) return new JsonPrimitive((long) Math.rint(v));
        return new JsonPrimitive(Math.round(v * 1000.0) / 1000.0);
    }

    /** Anything a block field / property may hold -> JSON. */
    static JsonElement val(Object o) {
        if (o == null) return new JsonPrimitive("");
        if (o instanceof JsonElement) return (JsonElement) o;
        if (o instanceof Boolean) return new JsonPrimitive((Boolean) o);
        if (o instanceof Integer || o instanceof Long) return new JsonPrimitive(((Number) o).longValue());
        if (o instanceof Number) return n(((Number) o).doubleValue());
        return new JsonPrimitive(String.valueOf(o));
    }

    static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) o.add(String.valueOf(kv[i]), val(kv[i + 1]));
        return o;
    }

    static JsonArray arr(Object... items) {
        JsonArray a = new JsonArray();
        for (Object o : items) a.add(val(o));
        return a;
    }

    static String hex2(int v) {
        return String.format(Locale.US, "%02X", Math.max(0, Math.min(255, v)));
    }

    /** "#RRGGBB" + alpha 0..1 -> element Tint format "#RRGGBBAA" (alpha LAST). */
    static String tint(String rgb, double alpha) {
        String c = rgb.startsWith("#") ? rgb.substring(1) : rgb;
        if (c.length() > 6) c = c.substring(0, 6);
        return "#" + c.toUpperCase(Locale.ROOT) + (alpha >= 0.999 ? "" : hex2((int) Math.round(alpha * 255)));
    }

    /** "#RRGGBB" + alpha 0..1 -> create_image color "#AARRGGBB" (alpha FIRST). */
    static String argb(String rgb, double alpha) {
        String c = rgb.startsWith("#") ? rgb.substring(1) : rgb;
        if (c.length() > 6) c = c.substring(c.length() - 6);
        return "#" + (alpha >= 0.999 ? "" : hex2((int) Math.round(alpha * 255))) + c.toUpperCase(Locale.ROOT);
    }

    static boolean isHexColor(String s) {
        return s != null && s.matches("#[0-9a-fA-F]{6}");
    }

    /** Mix two "#RRGGBB" colors (t = 0 -> a, 1 -> b). */
    static String mix(String a, String b, double t) {
        int ca = Integer.parseInt(a.substring(1), 16), cb = Integer.parseInt(b.substring(1), 16);
        int r = (int) Math.round(((ca >> 16) & 255) * (1 - t) + ((cb >> 16) & 255) * t);
        int g = (int) Math.round(((ca >> 8) & 255) * (1 - t) + ((cb >> 8) & 255) * t);
        int bl = (int) Math.round((ca & 255) * (1 - t) + (cb & 255) * t);
        return "#" + hex2(r) + hex2(g) + hex2(bl);
    }

    static String lighter(String c, double t) {
        return mix(c, "#FFFFFF", t);
    }

    static String darker(String c, double t) {
        return mix(c, "#000000", t);
    }

    /** Java string literal (with the quotes), for raw-Java block fields. */
    static String jstr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') sb.append('\\');
            sb.append(ch == '\n' ? "\\n" : String.valueOf(ch));
        }
        return sb.append('"').toString();
    }

    /** Keep only what the default font can draw (ASCII), trimmed and shortened. */
    static String asciiText(String s, String def, int max) {
        if (s == null) return def;
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) if (ch >= 32 && ch < 127) sb.append(ch);
        String t = sb.toString().trim();
        if (t.isEmpty()) return def;
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ------------------------------------------------------------------ text metrics (default.fnt, exact advances for ASCII 32..126)

    private static final int[] ADV = {4, 4, 6, 10, 9, 14, 11, 3, 5, 5, 9, 9, 4, 5, 4, 6, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 4, 4, 9, 9, 9, 7, 14, 10, 10, 10, 11, 8, 8, 11,
            11, 5, 4, 9, 8, 14, 12, 12, 9, 12, 10, 8, 8, 11, 9, 15, 9, 8, 9, 5, 6, 5, 9, 6, 9, 9, 9, 7, 9, 9, 5, 8, 10, 4, 4, 8, 4, 15, 10, 9, 9, 9, 6, 7, 5, 10, 8, 12,
            8, 8, 7, 6, 9, 6, 9};

    /** Width in px of one line at {@code scale} (Font Scale), plus a small safety margin. */
    static double textWidth(String s, double scale) {
        double w = 0;
        for (char ch : s.toCharArray()) w += (ch >= 32 && ch < 127) ? ADV[ch - 32] : 10;
        return Math.ceil(w * scale) + 4;
    }

    /** Height in px of one text line at {@code scale} (font line height 20). */
    static double textHeight(double scale) {
        return Math.ceil(20 * scale) + 6;
    }

    /** Largest Font Scale (steps of 0.5, at most {@code max}) whose text fits a box of w x h (w - 12, h - 6 inner). */
    static double fitScale(String text, double w, double h, double max) {
        double s = max;
        while (s > 0.5 && (textWidth(text, s) > w - 12 || textHeight(s) > h)) s -= 0.5;
        return s;
    }

    // ------------------------------------------------------------------ small deterministic random

    static final class Rng {
        private long s;

        Rng(long seed) {
            s = seed * 6364136223846793005L + 1442695040888963407L;
        }

        int next(int lo, int hi) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            long x = (s >>> 33);
            return lo + (int) (x % (hi - lo + 1));
        }

        boolean chance(int percent) {
            return next(0, 99) < percent;
        }

        boolean permille(int pm) {
            return next(0, 999) < pm;
        }
    }

    // ------------------------------------------------------------------ elements

    static final class El {
        final String type, name;
        final LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        /** purpose of the element in the reply / harness (e.g. "background", "hud", "control", "world") */
        String role = "";
        /** off-screen on purpose (spawn templates): the bounds check skips it */
        boolean offscreen;
        /** TEXT only: the longest text the element will ever show (the harness checks the box against it) */
        String widest = "";

        El(String type, String name) {
            this.type = type;
            this.name = name;
        }

        El p(String k, Object v) {
            props.put(k, v);
            return this;
        }

        El at(double x, double y) {
            p("x", x);
            p("y", y);
            return this;
        }

        El size(double w, double h) {
            p("width", w);
            p("height", h);
            return this;
        }

        El z(int z) {
            return p("z", z);
        }

        El tint(String rgb, double alpha) {
            return p("Tint", TemplateKit.tint(rgb, alpha));
        }

        El tint(String rgb) {
            return tint(rgb, 1);
        }

        El img(String path) {
            return p("image", path);
        }

        El body(String t) {
            return p("type", t);
        }

        El role(String r) {
            this.role = r;
            return this;
        }

        El off() {
            this.offscreen = true;
            return this;
        }

        double num(String k, double def) {
            Object o = props.get(k);
            return o instanceof Number ? ((Number) o).doubleValue() : def;
        }

        int zIndex() {
            return (int) num("z", 0);
        }

        JsonObject op() {
            JsonObject pr = new JsonObject();
            for (Map.Entry<String, Object> e : props.entrySet()) pr.add(e.getKey(), val(e.getValue()));
            return obj("op", "add", "type", type, "name", name, "props", pr);
        }
    }

    // ------------------------------------------------------------------ blocks (JSON, the format of the `events` tool)

    /** {"block":title,"fields":{...}}; kv = field, value, field, value... (value: String raw Java / Number / Boolean / reporter JsonObject). */
    static JsonObject b(String title, Object... kv) {
        JsonObject o = new JsonObject();
        o.addProperty("block", title);
        if (kv.length > 0) {
            JsonObject f = new JsonObject();
            for (int i = 0; i + 1 < kv.length; i += 2) f.add(String.valueOf(kv[i]), val(kv[i + 1]));
            o.add("fields", f);
        }
        return o;
    }

    /** A container block with a "do" part. */
    static JsonObject c(String title, JsonObject fields, JsonArray doPart) {
        JsonObject o = new JsonObject();
        o.addProperty("block", title);
        o.add("fields", fields);
        o.add("do", doPart);
        return o;
    }

    static JsonArray seq(Object... blocks) {
        JsonArray a = new JsonArray();
        for (Object o : blocks) {
            if (o instanceof JsonArray) for (JsonElement e : (JsonArray) o) a.add(e);
            else a.add(val(o));
        }
        return a;
    }

    /** Value block (reporter): {"r": display, "args": [...]}. */
    static JsonObject r(String display, Object... args) {
        JsonObject o = new JsonObject();
        o.addProperty("r", display);
        JsonArray a = new JsonArray();
        for (Object x : args) a.add(val(x));
        o.add("args", a);
        return o;
    }

    static JsonObject ifThen(Object cond, Object... doBlocks) {
        return c("ifThen", obj("boolean", cond), seq(doBlocks));
    }

    static JsonObject ifElse(Object cond, JsonArray yes, JsonArray no) {
        JsonObject o = c("if", obj("boolean", cond), yes);
        o.add("else", no);
        return o;
    }

    static JsonObject after(Object seconds, Object... doBlocks) {
        return c("waitSeconds", obj("seconds", seconds), seq(doBlocks));
    }

    static JsonObject every(Object seconds, Object... doBlocks) {
        return c("everySeconds", obj("seconds", seconds), seq(doBlocks));
    }

    static JsonObject code(String java) {
        return b("codeLine", "code", java);
    }

    static JsonObject setVar(String v, Object value) {
        return b("setVariable", "variable", v, "value", value);
    }

    static JsonObject addVar(String v, Object by) {
        return b("changeVariable", "variable", v, "by", by);
    }

    static JsonObject setText(String el, String javaExpr) {
        return b("setText", "ui", el, "text", javaExpr);
    }

    static JsonObject show(String el) {
        return b("show", "body", el);
    }

    static JsonObject hide(String el) {
        return b("hide", "body", el);
    }

    static JsonObject sound(String file) {
        return b("playSound", "Sound", file);
    }

    static JsonObject openScene(String scene) {
        return b("OpenScene", "scene", scene);
    }

    /** hide + remove after a moment (safe inside collision callbacks: the physics world is locked there, the timer runs later). */
    static JsonArray dispose(String item, Object seconds) {
        return seq(hide(item), b("destroyAfter", "body", item, "seconds", seconds));
    }

    /** Removes {@code item} after a while, but only if it is still alive (a destroyed element is never destroyed twice). */
    static JsonObject lifetime(String item, Object seconds) {
        return after(seconds, ifThen(item + ".getActor().getStage() != null", b("destroy", "body", item)));
    }

    static JsonObject velX(String body, Object speed) {
        return b("setVelocityX", "body", body, "speed", speed);
    }

    static JsonObject velY(String body, Object speed) {
        return b("setVelocityY", "body", body, "speed", speed);
    }

    static JsonObject goTo(String body, Object x, Object y) {
        return b("goTo", "body", body, "x", x, "y", y);
    }

    // reporters
    static JsonObject xOf(String el) {
        return r("x of ___", el);
    }

    static JsonObject yOf(String el) {
        return r("y of ___", el);
    }

    static JsonObject gt(Object a, Object b) {
        return r("___ > ___", a, b);
    }

    static JsonObject lt(Object a, Object b) {
        return r("___ < ___", a, b);
    }

    static JsonObject ge(Object a, Object b) {
        return r("___ >= ___", a, b);
    }

    static JsonObject le(Object a, Object b) {
        return r("___ <= ___", a, b);
    }

    static JsonObject and(Object a, Object b) {
        return r("___ and ___", a, b);
    }

    static JsonObject or(Object a, Object b) {
        return r("___ or ___", a, b);
    }

    static JsonObject not(Object a) {
        return r("not ___", a);
    }

    static JsonObject mul(Object a, Object b) {
        return r("___ * ___", a, b);
    }

    static JsonObject plus(Object a, Object b) {
        return r("___ + ___", a, b);
    }

    static JsonObject minus(Object a, Object b) {
        return r("___ - ___", a, b);
    }

    static JsonObject abs(Object a) {
        return r("abs ___", a);
    }

    static JsonObject min(Object a, Object b) {
        return r("min ___ , ___", a, b);
    }

    static JsonObject max(Object a, Object b) {
        return r("max ___ , ___", a, b);
    }

    /** Raw Java condition: the pair (body1, body2) is one element called exactly {@code a} and one whose name starts with {@code bPrefix}. */
    static String pairStarts(String a, String bPrefix) {
        return "(body1.getName().equals(\"" + a + "\") && body2.getName().startsWith(\"" + bPrefix + "\")) || (body2.getName().equals(\"" + a
                + "\") && body1.getName().startsWith(\"" + bPrefix + "\"))";
    }

    static String pairEquals(String a, String b) {
        return "(body1.getName().equals(\"" + a + "\") && body2.getName().equals(\"" + b + "\")) || (body2.getName().equals(\"" + a
                + "\") && body1.getName().equals(\"" + b + "\"))";
    }

    static String pairPrefixes(String a, String b) {
        return "(body1.getName().startsWith(\"" + a + "\") && body2.getName().startsWith(\"" + b + "\")) || (body2.getName().startsWith(\"" + a
                + "\") && body1.getName().startsWith(\"" + b + "\"))";
    }

    /** Java line: local {@code var} = whichever of body1/body2 has a name starting with {@code prefix}. */
    static JsonObject pick(String var, String prefix) {
        return code("PlayerItem " + var + " = body1.getName().startsWith(\"" + prefix + "\") ? body1 : body2;");
    }

    // ------------------------------------------------------------------ the plan

    static final class Step {
        final String tool;
        final JsonObject args;
        final String label;

        Step(String tool, JsonObject args, String label) {
            this.tool = tool;
            this.args = args;
            this.label = label;
        }
    }

    static final class Plan {
        final int w, h;
        final boolean landscape;
        final String scene;
        final String sceneColor;
        private final LinkedHashMap<String, JsonObject> images = new LinkedHashMap<>();
        private final LinkedHashMap<String, JsonObject> sounds = new LinkedHashMap<>();
        final JsonArray vars = new JsonArray();
        final ArrayList<El> elements = new ArrayList<>();
        final LinkedHashMap<String, JsonArray> sceneEvents = new LinkedHashMap<>();
        final ArrayList<JsonObject> elementEvents = new ArrayList<>();
        final ArrayList<String> centered = new ArrayList<>();
        /** short lines for the reply: what to change afterwards */
        final ArrayList<String> tweaks = new ArrayList<>();
        final ArrayList<String> info = new ArrayList<>();
        /** how many scene-level bounds-checked physics speeds etc. the harness should know about: free-form key/values for the checker */
        final LinkedHashMap<String, Object> meta = new LinkedHashMap<>();

        Plan(int w, int h, boolean landscape, String scene, String sceneColor) {
            this.w = w;
            this.h = h;
            this.landscape = landscape;
            this.scene = scene;
            this.sceneColor = sceneColor;
        }

        // ---- assets
        Plan image(String path, JsonObject spec) {
            if (!images.containsKey(path)) {
                JsonObject o = spec.deepCopy();
                o.addProperty("path", "images/" + path);
                images.put(path, o);
            }
            return this;
        }

        Plan rounded(String path, int wpx, int hpx, double radius, String fill, String stroke, double strokeWidth) {
            JsonObject o = obj("kind", "shape", "shape", "rounded", "width", wpx, "height", hpx, "radius", radius, "fill", fill);
            if (stroke != null) {
                o.addProperty("stroke", stroke);
                o.addProperty("strokeWidth", strokeWidth);
            }
            return image(path, o);
        }

        Plan whitePixel() {
            return image("white.png", obj("kind", "shape", "shape", "rect", "width", 16, "height", 16, "fill", "#FFFFFF"));
        }

        Plan gradientImage(String path, int wpx, int hpx, String top, String bottom) {
            return image(path, obj("kind", "gradient", "width", wpx, "height", hpx, "colors", arr(top, bottom), "angle", 90));
        }

        Plan pixels(String path, String[] rows, LinkedHashMap<Character, String> palette, int scale) {
            JsonObject pal = new JsonObject();
            for (Map.Entry<Character, String> e : palette.entrySet()) pal.addProperty(String.valueOf(e.getKey()), e.getValue());
            JsonArray rs = new JsonArray();
            for (String s : rows) rs.add(s);
            return image(path, obj("kind", "pixels", "palette", pal, "rows", rs, "scale", scale));
        }

        Plan sound(String file, String preset) {
            sounds.put(file, obj("path", "sounds/" + file, "preset", preset));
            return this;
        }

        // ---- variables
        Plan var(String name, String type, String value) {
            vars.add(obj("op", "add", "name", name, "type", type, "value", value));
            return this;
        }

        // ---- elements
        El add(El e) {
            for (El x : elements) if (x.name.equals(e.name)) throw new IllegalStateException("duplicate element name " + e.name);
            elements.add(e);
            return e;
        }

        El box(String name, double x, double y, double w, double h) {
            return add(new El("BOX", name).at(x, y).size(w, h));
        }

        /** CIRCLE given by its CENTER (x, y are the bottom-left of its bounding square in the engine). */
        El circle(String name, double cx, double cy, double radius) {
            return add(new El("CIRCLE", name).at(cx - radius, cy - radius).p("radius", radius));
        }

        /**
         * A TEXT. {@code scale} is shrunk when the text would not fit w x h. Centered (alignment set in OnCreate) when {@code center}.
         * The box is made at least as wide as the text needs, so it never wraps.
         */
        El text(String name, String text, double x, double y, double w, double h, double scale, String color, int z, boolean center) {
            double s = fitScale(text, w, h, scale);
            double need = textWidth(text, s);
            double width = Math.max(w, need + 8);
            double xx = center ? x - (width - w) / 2 : x;
            El e = add(new El("TEXT", name).at(xx, y).size(width, Math.max(h, textHeight(s))).z(z).p("Text", text).p("Font Scale", s).p("Text Color", color).role("hud"));
            e.widest = text;
            if (center) centered.add(name);
            return e;
        }

        /**
         * Text whose content changes while the game runs: the box is sized for {@code widest} (the longest text it will show).
         */
        El liveText(String name, String initial, String widest, double x, double y, double h, double scale, String color, int z, boolean center, double wMin) {
            double need = textWidth(widest.length() > initial.length() ? widest : initial, scale);
            double width = Math.max(wMin, need + 8);
            El e = add(new El("TEXT", name).at(x, y).size(width, Math.max(h, textHeight(scale))).z(z).p("Text", initial).p("Font Scale", scale).p("Text Color", color).role("hud"));
            e.widest = widest.length() > initial.length() ? widest : initial;
            if (center) centered.add(name);
            return e;
        }

        /**
         * A button: rounded image box (UI) + label TEXT on top. Returns the BOX (put the touch events on it, never on the label).
         * {@code colorTop/colorBottom}: "#RRGGBB" gradient of the image.
         */
        El button(String name, String label, double x, double y, double w, double h, String colorTop, String colorBottom, String textColor, double maxScale, int z) {
            String img = "btn_" + name + ".png";
            int iw = (int) Math.round(w), ih = (int) Math.round(h);
            rounded(img, iw, ih, Math.min(iw, ih) * 0.28, "linear:" + colorTop + "," + colorBottom + ",90", darker(colorBottom, 0.35), Math.max(2, Math.min(iw, ih) / 28.0));
            El box = add(new El("BOX", name).body("UI").at(x, y).size(w, h).img("/" + img).z(z).role("button"));
            text(name + "Label", label, x, y, w, h, maxScale, textColor, z + 10, true).role("label");
            return box;
        }

        // ---- events
        JsonArray on(String sceneEvent) {
            JsonArray a = sceneEvents.get(sceneEvent);
            if (a == null) {
                a = new JsonArray();
                sceneEvents.put(sceneEvent, a);
            }
            return a;
        }

        Plan onElement(String element, String event, JsonArray blocks) {
            elementEvents.add(obj("element", element, "event", event, "blocks", blocks));
            return this;
        }

        Plan tweak(String line) {
            tweaks.add(line);
            return this;
        }

        Plan info(String line) {
            info.add(line);
            return this;
        }

        /** The ordered tool calls: images, sounds, helper blocks, variables, elements (z ascending, so draw order and touch order agree), events. */
        List<Step> steps() {
            ArrayList<Step> out = new ArrayList<>();
            for (JsonObject o : images.values()) out.add(new Step("create_image", o, "image " + o.get("path").getAsString()));
            for (JsonObject o : sounds.values()) out.add(new Step("create_sound", o, "sound " + o.get("path").getAsString()));
            if (vars.size() > 0) out.add(new Step("variables", obj("ops", vars), "variables"));
            ArrayList<El> sorted = new ArrayList<>(elements);
            // stable: equal z keep their creation order
            java.util.Collections.sort(sorted, (a, b) -> Integer.compare(a.zIndex(), b.zIndex()));
            JsonArray ops = new JsonArray();
            int chunk = 0;
            for (El e : sorted) {
                ops.add(e.op());
                if (ops.size() >= 30) {
                    out.add(new Step("elements", obj("ops", ops), "elements " + (++chunk)));
                    ops = new JsonArray();
                }
            }
            if (ops.size() > 0) out.add(new Step("elements", obj("ops", ops), "elements " + (++chunk)));
            JsonArray evs = new JsonArray();
            LinkedHashMap<String, JsonArray> scene = new LinkedHashMap<>();
            for (Map.Entry<String, JsonArray> e : sceneEvents.entrySet()) scene.put(e.getKey(), e.getValue().deepCopy());
            if (!centered.isEmpty()) {
                JsonArray create = scene.get("OnCreate");
                if (create == null) {
                    create = new JsonArray();
                    scene.put("OnCreate", create);
                }
                for (String name : centered)
                    create.add(code("((com.badlogic.gdx.scenes.scene2d.ui.Label)" + name + ".getActor()).setAlignment(com.badlogic.gdx.utils.Align.center);"));
            }
            for (Map.Entry<String, JsonArray> e : scene.entrySet())
                if (e.getValue().size() > 0) evs.add(obj("op", "set", "scope", "scene", "event", e.getKey(), "blocks", e.getValue()));
            for (JsonObject eo : elementEvents)
                evs.add(obj("op", "set", "scope", "element", "element", eo.get("element").getAsString(), "event", eo.get("event").getAsString(), "blocks", eo.get("blocks")));
            out.add(new Step("events", obj("ops", evs), "events"));
            return out;
        }
    }
}
