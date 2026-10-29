package com.star4droid.star2d.ui.uimaker;

import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UILayout;
import com.star4droid.template.ui.UINode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Layout problems of a document, as short sentences for a person or an AI (UI Maker warnings, `ui` tool).
 * Pure Java: sizes of text come from the measurer (approximate when nothing better is given).
 */
public final class UiLint {
    private UiLint() {}

    /** Smallest comfortable touch target in px (a finger is ~7-10 mm wide; 44 px at 360 wide phones is about 5 mm... kept as the common rule). */
    public static final float MIN_TOUCH = 44f;

    private static boolean interactive(UINode n) {
        switch (n.type) {
            case UINode.BUTTON: case UINode.SLIDER: case UINode.CHECKBOX: case UINode.RADIO: case UINode.SWITCH: case UINode.TEXTFIELD:
                return true;
            default:
                return false;
        }
    }

    /** Gives the sentences of the warnings a translation: {@code t(key, englishFormat)}. The UI Maker passes its own; the AI tool uses English. */
    public interface Translator {
        String t(String key, String englishFormat);
    }

    private static String msg(Translator tr, String key, String english, Object... args) {
        String f = tr == null ? english : tr.t(key, english);
        try {
            return String.format(Locale.ROOT, f, args);
        } catch (RuntimeException e) {
            return String.format(Locale.ROOT, english, args); // a broken translation must not break the list
        }
    }

    /** Checks the document in the given screen size. */
    public static List<String> check(UIDocument doc, float w, float h, UILayout.Measurer m) {
        return check(doc, w, h, m, null);
    }

    /** Checks the document in the given screen size, with the sentences in the language of {@code tr} (null = English). */
    public static List<String> check(UIDocument doc, float w, float h, UILayout.Measurer m, Translator tr) {
        ArrayList<String> out = new ArrayList<>();
        UiRects rects = UiRects.compute(doc, w, h, m);
        String tag = (int) w + "x" + (int) h;
        for (UiRects.R r : rects.rects.values()) {
            if (!r.shown || r.node.parent == null) continue;
            UINode n = r.node;
            if (!r.scrolled && !insideScroll(n)) {
                float over = Math.max(Math.max(-r.x, r.x + r.w - w), Math.max(-r.y, r.y + r.h - h));
                if (over > 8 && r.w > 0 && r.h > 0)
                    out.add(msg(tr, "uim_lint_offscreen", "[%1$s] '%2$s' (%3$s) leaves the screen by %4$d px. Use an anchor + margin instead of a big x/y offset, or make it smaller.", tag, n.id, n.type, Math.round(over)));
            }
            if (interactive(n) && n.enabled && !(n.type.equals(UINode.SLIDER) && n.bool("vertical", false))) {
                float minDim = Math.min(r.w, r.h);
                float need = n.type.equals(UINode.BUTTON) || n.type.equals(UINode.TEXTFIELD) ? MIN_TOUCH : 36f;
                if (n.type.equals(UINode.SLIDER)) minDim = r.h;
                if (r.w > 0 && r.h > 0 && minDim < need)
                    out.add(msg(tr, "uim_lint_touch", "[%1$s] touch target '%2$s' is only %3$d px thick (%4$d px or more is easy to hit).", tag, n.id, Math.round(minDim), Math.round(need)));
            }
            if (n.type.equals(UINode.LABEL) && !n.bool("wrap", false) && n.w != null && n.width().kind == UINode.PX) {
                float fs = n.num("fontSize", doc.theme.number("fontSize"));
                float[] tw = (m == null ? UILayout.APPROX : m).text(n.str("text", ""), fs, 0, n);
                if (tw != null && tw[0] > n.width().v * 1.15f)
                    out.add(msg(tr, "uim_lint_label", "[%1$s] label '%2$s' text is wider than its width (%3$d > %4$d px): set wrap=true, a bigger width or a smaller fontSize.", tag, n.id, Math.round(tw[0]), Math.round(n.width().v)));
            }
            if (n.type.equals(UINode.BUTTON) && n.has("text") && n.w != null && n.width().kind == UINode.PX) {
                float fs = n.num("fontSize", doc.theme.number("fontSize"));
                float[] tw = (m == null ? UILayout.APPROX : m).text(n.str("text", ""), fs, 0, n);
                if (tw != null && tw[0] > n.width().v - 8)
                    out.add(msg(tr, "uim_lint_button", "[%1$s] button '%2$s' text may not fit (%3$d px of text in %4$d px).", tag, n.id, Math.round(tw[0]), Math.round(n.width().v)));
            }
        }
        overlaps(rects, out, tag, tr);
        return dedupe(out);
    }

    /** Portrait and landscape of the same screen size. {@code long}/{@code short} are the sides, e.g. 1280 and 720. */
    public static List<String> checkBoth(UIDocument doc, float longSide, float shortSide, UILayout.Measurer m) {
        return checkBoth(doc, longSide, shortSide, m, null);
    }

    public static List<String> checkBoth(UIDocument doc, float longSide, float shortSide, UILayout.Measurer m, Translator tr) {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(check(doc, shortSide, longSide, m, tr));
        out.addAll(check(doc, longSide, shortSide, m, tr));
        return out;
    }

    private static boolean insideScroll(UINode n) {
        for (UINode p = n.parent; p != null; p = p.parent) if (p.type.equals(UINode.SCROLL)) return true;
        return false;
    }

    private static void overlaps(UiRects rects, ArrayList<String> out, String tag, Translator tr) {
        ArrayList<UiRects.R> list = new ArrayList<>();
        for (UiRects.R r : rects.rects.values()) if (r.shown && r.node.parent != null && interactive(r.node) && r.node.enabled) list.add(r);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                UiRects.R a = list.get(i), b = list.get(j);
                if (a.scrolled != b.scrolled) continue;
                float ox = Math.min(a.x + a.w, b.x + b.w) - Math.max(a.x, b.x);
                float oy = Math.min(a.y + a.h, b.y + b.h) - Math.max(a.y, b.y);
                if (ox <= 2 || oy <= 2) continue;
                float ov = ox * oy, small = Math.min(a.area(), b.area());
                if (small > 0 && ov / small > 0.25f)
                    out.add(msg(tr, "uim_lint_overlap", "[%1$s] '%2$s' and '%3$s' overlap (%4$d%% of the smaller). Put them in a Linear/Grid or give them different anchors.", tag, a.node.id, b.node.id, Math.round(100 * ov / small)));
            }
        }
    }

    private static List<String> dedupe(ArrayList<String> l) {
        ArrayList<String> o = new ArrayList<>();
        for (String s : l) if (!o.contains(s)) o.add(s);
        return o;
    }

    /** Short text for a rectangle, e.g. "120,40 180x64". */
    public static String rectText(UiRects.R r) {
        return String.format(Locale.ROOT, "%d,%d %dx%d", Math.round(r.x), Math.round(r.y), Math.round(r.w), Math.round(r.h));
    }
}
