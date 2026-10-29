package com.star4droid.star2d.remote;

import static com.star4droid.star2d.remote.TemplateKit.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What every template shares: the template description + parameter parsing, and the HUD / control / game-over pieces.
 * Pure Java (Gson only), see {@link TemplateKit}.
 */
final class TemplateLib {
    private TemplateLib() {}

    // ------------------------------------------------------------------ template description

    /** One parameter of a template: key, default (as shown), what it does. */
    static final class P {
        final String key, def, doc;

        P(String key, String def, String doc) {
            this.key = key;
            this.def = def;
            this.doc = doc;
        }
    }

    abstract static class Tpl {
        abstract String id();

        /** One short line for the catalog. */
        abstract String about();

        abstract List<P> params();

        /** Sections of the docs topic: what you get (one paragraph) and which variables to tweak. */
        abstract String details();

        /** JSON param sets the harness also builds (extremes of the parameters). */
        String[] samples() {
            return new String[0];
        }

        /** Builds everything; w x h = the REAL frame of the scene (portrait: w is the short side). */
        abstract Plan build(Params p, int w, int h, String scene);
    }

    // ------------------------------------------------------------------ parameters

    static final class Params {
        final JsonObject raw;
        /** accepted values, as used (key -> value text) */
        final LinkedHashMap<String, String> used = new LinkedHashMap<>();
        final ArrayList<String> notes = new ArrayList<>();

        Params(JsonObject raw) {
            this.raw = raw == null ? new JsonObject() : raw;
        }

        private JsonElement get(String k) {
            for (Map.Entry<String, JsonElement> e : raw.entrySet()) if (e.getKey().equalsIgnoreCase(k)) return e.getValue();
            return null;
        }

        double num(String k, double def, double min, double max) {
            JsonElement e = get(k);
            double v = def;
            if (e != null && e.isJsonPrimitive()) {
                try {
                    v = Double.parseDouble(e.getAsString().trim());
                } catch (Exception ex) {
                    notes.add(k + ": '" + e.getAsString() + "' is not a number, used " + fmt(def));
                    v = def;
                }
                if (v < min || v > max) {
                    double c = Math.max(min, Math.min(max, v));
                    notes.add(k + ": " + fmt(v) + " is outside " + fmt(min) + ".." + fmt(max) + ", used " + fmt(c));
                    v = c;
                }
            } else if (e != null) notes.add(k + ": expected a number, used " + fmt(def));
            used.put(k, fmt(v));
            return v;
        }

        int integer(String k, int def, int min, int max) {
            return (int) Math.round(num(k, def, min, max));
        }

        String choice(String k, String def, String... allowed) {
            JsonElement e = get(k);
            String v = def;
            if (e != null && e.isJsonPrimitive()) {
                String t = e.getAsString().trim().toLowerCase(Locale.ROOT);
                boolean ok = false;
                for (String a : allowed) if (a.equals(t)) ok = true;
                if (ok) v = t;
                else notes.add(k + ": '" + e.getAsString() + "' is not one of " + String.join("|", allowed) + ", used " + def);
            }
            used.put(k, v);
            return v;
        }

        String text(String k, String def, int max) {
            JsonElement e = get(k);
            String v = def;
            if (e != null && e.isJsonPrimitive()) {
                String t = asciiText(e.getAsString(), def, max);
                if (!t.equals(e.getAsString().trim())) notes.add(k + ": the default font draws plain ASCII only and at most " + max + " characters, used '" + t + "'");
                v = t;
            }
            used.put(k, v);
            return v;
        }

        /** A scene name (letters, digits, _). */
        String sceneName(String k, String def) {
            JsonElement e = get(k);
            String v = def;
            if (e != null && e.isJsonPrimitive()) {
                String t = e.getAsString().trim();
                if (t.matches("[A-Za-z_][A-Za-z0-9_]*")) v = t;
                else notes.add(k + ": '" + t + "' is not a valid scene name (letters, digits, _), used " + def);
            }
            used.put(k, v);
            return v;
        }

        String color(String k, String def) {
            JsonElement e = get(k);
            String v = def;
            if (e != null && e.isJsonPrimitive()) {
                String t = e.getAsString().trim();
                if (!t.startsWith("#") && t.matches("[0-9a-fA-F]{6}")) t = "#" + t;
                if (isHexColor(t)) v = t.toUpperCase(Locale.ROOT);
                else notes.add(k + ": '" + e.getAsString() + "' is not #RRGGBB, used " + def);
            }
            used.put(k, v);
            return v;
        }

        /** Keys the caller gave that the template does not know. */
        void unknown(List<P> known) {
            for (String k : raw.keySet()) {
                boolean ok = false;
                for (P p : known) if (p.key.equalsIgnoreCase(k)) ok = true;
                if (!ok) notes.add("unknown param '" + k + "' ignored (known: " + keys(known) + ")");
            }
        }

        private static String keys(List<P> known) {
            StringBuilder sb = new StringBuilder();
            for (P p : known) sb.append(sb.length() > 0 ? ", " : "").append(p.key);
            return sb.toString();
        }
    }

    static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.valueOf(Math.round(v * 100) / 100.0);
    }

    // ------------------------------------------------------------------ themes

    /** Sky gradient top/bottom + the text color that reads on it. */
    static String[] sky(String theme) {
        switch (theme) {
            case "night": return new String[]{"#0B1026", "#34457F", "#FFFFFF"};
            case "sunset": return new String[]{"#4A2A7A", "#F5A64B", "#FFFFFF"};
            default: return new String[]{"#4FA3E0", "#CFE9FA", "#FFFFFF"};
        }
    }

    // ------------------------------------------------------------------ screen pieces

    /** Full-screen gradient (UI layer, z 0): the lowest thing in the scene. */
    static El background(Plan p, String top, String bottom) {
        p.gradientImage("bg.png", 8, 256, argb(top, 1), argb(bottom, 1));
        return p.add(new El("BOX", "Background").body("UI").at(0, 0).size(p.w, p.h).img("/bg.png").z(Z_BG).role("background"));
    }

    /** A few soft clouds on the UI layer (fixed, z 1: behind everything that moves). */
    static void clouds(Plan p, int n, long seed) {
        Rng r = new Rng(seed);
        cloudImage(p);
        for (int i = 0; i < n; i++) {
            int cw = r.next(150, 260), ch = (int) (cw * 0.38);
            double x = r.next(0, Math.max(1, p.w - cw)), y = p.h * 0.55 + r.next(0, (int) Math.max(1, p.h * 0.38 - ch));
            p.add(new El("BOX", "Cloud" + (i + 1)).body("UI").at(x, Math.min(y, p.h - ch - 10)).size(cw, ch).img("/cloud.png").tint("#FFFFFF", 0.75).z(Z_BG + 1).role("decor"));
        }
    }

    static void cloudImage(Plan p) {
        TemplateArt.cloud(p, "cloud.png", 160, 60);
    }

    static final int MARGIN = 24;

    /** Size of the on-screen control buttons for this frame. */
    static int btn(Plan p) {
        return (int) Math.max(110, Math.min(170, Math.min(p.w, p.h) * 0.2));
    }

    /** Top HUD line: a TEXT whose content changes (box sized for the widest text). left = false puts it against the right edge. */
    static El hud(Plan p, String name, String text, String widest, boolean left, int row, double scale, String color) {
        double h = textHeight(scale);
        double w = Math.max(60, textWidth(widest.length() > text.length() ? widest : text, scale) + 8);
        double x = left ? MARGIN : p.w - MARGIN - w;
        double y = p.h - MARGIN - h - row * (h + 6);
        return p.liveText(name, text, widest, x, y, h, scale, color, Z_HUD, false, 60);
    }

    /** A round-ish control button (UI BOX + label). Returns the BOX. */
    static El control(Plan p, String name, String label, double x, double y, double size, String top, String bottom) {
        El b = p.button(name, label, x, y, size, size, top, bottom, "#FFFFFF", 2, Z_CTRL);
        b.tint("#FFFFFF", 0.82);
        return b;
    }

    /** Left / right / action layout used by side-view games: Left+Right bottom-left, one action bottom-right. */
    static void sideControls(Plan p, String action, String actionLabel) {
        int s = btn(p);
        control(p, "LeftBtn", "<", MARGIN, MARGIN, s, "#5B6C8F", "#34405C");
        control(p, "RightBtn", ">", MARGIN + s + 18, MARGIN, s, "#5B6C8F", "#34405C");
        control(p, action, actionLabel, p.w - MARGIN - s * 1.25, MARGIN, s * 1.25, "#F0A33A", "#C2701A");
    }

    // ------------------------------------------------------------------ game-over / win panel

    /** Names of the end-of-game elements, in the order they are shown. */
    static final String[] END_NAMES = {"EndPanel", "EndTitle", "EndSub", "RestartBtn", "RestartBtnLabel"};

    /** Hidden panel with a title, a sub line and a restart button that reopens {@code scene}. */
    static void endOverlay(Plan p, String scene, String titleWidest, String subWidest) {
        p.whitePixel();
        p.add(new El("BOX", "EndPanel").body("UI").at(0, 0).size(p.w, p.h).img("/white.png").tint("#000000", 0.7).z(Z_OVERLAY).p("Visible", "false").role("overlay"));
        double ts = fitScale(titleWidest, p.w - 2 * MARGIN, 80, 3);
        double th = textHeight(ts);
        El t = p.liveText("EndTitle", titleWidest, titleWidest, 0, 0, th, ts, "#FFFFFF", Z_OVERLAY + 10, true, 100);
        t.p("x", (p.w - t.num("width", 100)) / 2).p("y", p.h * 0.58).p("Visible", "false").role("overlay");
        double ss = 1.5, sh = textHeight(ss);
        El s = p.liveText("EndSub", subWidest, subWidest, 0, 0, sh, ss, "#E0E6F5", Z_OVERLAY + 10, true, 100);
        s.p("x", (p.w - s.num("width", 100)) / 2).p("y", p.h * 0.58 - sh - 18).p("Visible", "false").role("overlay");
        double bw = 260, bh = 84;
        El b = p.button("RestartBtn", "PLAY AGAIN", (p.w - bw) / 2, p.h * 0.58 - sh - 18 - bh - 40, bw, bh, "#3DDC84", "#1E9E5A", "#FFFFFF", 2, Z_OVERLAY + 2);
        b.p("Visible", "false");
        for (El e : p.elements) if (e.name.equals("RestartBtnLabel")) e.p("Visible", "false");
        p.onElement("RestartBtn", "onClick", seq(openScene(scene)));
    }

    /** Blocks that show the end panel with a title and a sub line (Java string expressions). */
    static JsonArray showEnd(String titleExpr, String subExpr) {
        JsonArray a = new JsonArray();
        a.add(setText("EndTitle", titleExpr));
        a.add(setText("EndSub", subExpr));
        for (String n : END_NAMES) a.add(show(n));
        return a;
    }

    // ------------------------------------------------------------------ small geometry helpers

    static int clampInt(double v, int lo, int hi) {
        return (int) Math.max(lo, Math.min(hi, Math.round(v)));
    }

    /** "1.5f" style float literal for Java expressions. */
    static String f(double v) {
        String s = fmt(v);
        return s + (s.contains(".") ? "f" : ".0f");
    }
}
