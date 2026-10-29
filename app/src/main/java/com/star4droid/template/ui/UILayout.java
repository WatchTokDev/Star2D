package com.star4droid.template.ui;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Layout of a .ui tree. Pure Java (no libGDX): the same code runs in the game, in the editor preview and in offline tests.
 *
 * After {@link #layout} every node has {@code rx, ry, rw, rh}: its rectangle relative to the BOTTOM-LEFT corner of its parent
 * (y up, pixels), and ScrollViews have {@code contentW/contentH}. Rules are written in docs/UI.md.
 */
public final class UILayout {
    private UILayout() {}

    /** Gives the layout the sizes it cannot know by itself (text and pictures). */
    public interface Measurer {
        /** {width, height} of the text at fontSize px; wrapWidth &lt;= 0 means one line. */
        float[] text(String text, float fontSize, float wrapWidth, UINode node);

        /** Natural {width, height} of a project image, or null when unknown. */
        float[] image(String path);
    }

    /** Rough measurer used when nothing better exists (tests, headless): 0.55 em per glyph. */
    public static final Measurer APPROX = new Measurer() {
        @Override
        public float[] text(String text, float fs, float wrap, UINode node) {
            if (text == null) text = "";
            String[] lines = text.split("\n", -1);
            float gw = fs * 0.55f, lh = fs * 1.25f;
            float w = 0;
            int count = 0;
            for (String l : lines) {
                float lw = l.length() * gw;
                if (wrap > 0 && lw > wrap) {
                    int per = Math.max(1, (int) (wrap / gw));
                    count += (l.length() + per - 1) / per;
                    lw = Math.min(lw, wrap);
                } else count++;
                w = Math.max(w, lw);
            }
            return new float[]{w, Math.max(1, count) * lh};
        }

        @Override
        public float[] image(String path) {
            return null;
        }
    };

    private static final float BIG = 100000f;

    // ------------------------------------------------------------------ public

    /** Lays out the whole tree in a view of {@code w x h}. The root always fills the view. */
    public static void layout(UINode root, UITheme theme, float w, float h, Measurer m) {
        if (m == null) m = APPROX;
        Ctx c = new Ctx(theme == null ? new UITheme() : theme, m);
        root.rx = 0;
        root.ry = 0;
        root.rw = w;
        root.rh = h;
        layoutChildren(root, c);
    }

    // ------------------------------------------------------------------ defaults

    /** Default width spec of a type when the node has no "w". */
    public static UINode.Size defaultWidth(UINode n) {
        switch (n.type) {
            case UINode.PANEL: case UINode.STACK: case UINode.SCROLL: case UINode.LINEAR: case UINode.GRID: return UINode.SIZE_FILL;
            case UINode.BUTTON: return new UINode.Size(UINode.PX, 160);
            case UINode.ICON: return new UINode.Size(UINode.PX, 32);
            case UINode.IMAGE: return new UINode.Size(UINode.PX, 64);
            case UINode.PROGRESS: return new UINode.Size(UINode.PX, 200);
            case UINode.SLIDER: return new UINode.Size(UINode.PX, 200);
            case UINode.SWITCH: return new UINode.Size(UINode.PX, 56);
            case UINode.TEXTFIELD: return new UINode.Size(UINode.PX, 240);
            default: return UINode.SIZE_WRAP; // Label, Checkbox, Radio
        }
    }

    public static UINode.Size defaultHeight(UINode n, UITheme th) {
        switch (n.type) {
            case UINode.PANEL: case UINode.STACK: case UINode.SCROLL: return UINode.SIZE_FILL;
            case UINode.LINEAR: case UINode.GRID: return UINode.SIZE_WRAP;
            case UINode.BUTTON: return new UINode.Size(UINode.PX, 56);
            case UINode.ICON: return new UINode.Size(UINode.PX, 32);
            case UINode.IMAGE: return new UINode.Size(UINode.PX, 64);
            case UINode.PROGRESS: return new UINode.Size(UINode.PX, Math.max(16, UIArt.trackThickness(n, th, 0)));
            case UINode.SLIDER: return new UINode.Size(UINode.PX, Math.max(32, UIStyles.spec(n, th).f("knobSize", 32f) + 8));
            case UINode.SWITCH: return new UINode.Size(UINode.PX, 32);
            case UINode.TEXTFIELD: return new UINode.Size(UINode.PX, 48);
            default: return UINode.SIZE_WRAP;
        }
    }

    private static UINode.Size wSpec(UINode n) {
        UINode.Size s = n.width();
        return s.kind == UINode.AUTO ? defaultWidth(n) : s;
    }

    private static UINode.Size hSpec(UINode n, Ctx c) {
        UINode.Size s = n.height();
        return s.kind == UINode.AUTO ? defaultHeight(n, UITheme.forNode(n, c.theme)) : s;
    }

    // ------------------------------------------------------------------ internals

    private static final class Ctx {
        final UITheme theme;
        final Measurer m;

        Ctx(UITheme t, Measurer m) {
            this.theme = t;
            this.m = m;
        }
    }

    /** Insets of the content box: l, t, r, b. */
    public static float[] padding(UINode n) {
        float def = (n.type.equals(UINode.LINEAR) || n.type.equals(UINode.GRID)) ? 8f : 0f;
        float l = def, t = def, r = def, b = def;
        if (n.has("padding")) {
            try {
                com.google.gson.JsonElement e = n.props.get("padding");
                if (e.isJsonArray()) {
                    com.google.gson.JsonArray a = e.getAsJsonArray();
                    if (a.size() == 1) l = t = r = b = a.get(0).getAsFloat();
                    else if (a.size() == 2) { l = r = a.get(0).getAsFloat(); t = b = a.get(1).getAsFloat(); }
                    else if (a.size() >= 4) { l = a.get(0).getAsFloat(); t = a.get(1).getAsFloat(); r = a.get(2).getAsFloat(); b = a.get(3).getAsFloat(); }
                } else l = t = r = b = e.getAsFloat();
            } catch (Exception ignored) {
            }
        }
        return new float[]{l, t, r, b};
    }

    static float fontSize(UINode n, Ctx c) {
        return n.num("fontSize", UITheme.forNode(n, c.theme).number("fontSize"));
    }

    /** Visible nodes only: invisible nodes take no room (like "gone"). */
    private static ArrayList<UINode> live(UINode n) {
        ArrayList<UINode> l = new ArrayList<>();
        for (UINode c : n.children) if (c.visible) l.add(c);
        return l;
    }

    // ---- intrinsic (wrap) size

    /** Size of {@code n} when it must wrap its content; {@code availW} is the width it may use (for wrapping text). */
    private static float[] intrinsic(UINode n, float availW, Ctx c) {
        switch (n.type) {
            case UINode.LABEL: {
                float fs = fontSize(n, c);
                boolean wrap = n.bool("wrap", false);
                float[] s = c.m.text(n.str("text", ""), fs, wrap && availW < BIG ? availW : 0, n);
                return new float[]{s[0] + 2, s[1] + 2};
            }
            case UINode.BUTTON: {
                float fs = fontSize(n, c);
                float padX = n.num("padding", 24f);
                String txt = n.str("text", "");
                float[] s = txt.isEmpty() ? new float[]{0, 0} : c.m.text(txt, fs, 0, n);
                float iconW = n.has("icon") ? fs * 1.2f + (txt.isEmpty() ? 0 : 8) : 0;
                return new float[]{Math.max(56, s[0] + iconW + padX * 2), Math.max(48, s[1] + 20)};
            }
            case UINode.CHECKBOX: case UINode.RADIO: {
                float fs = fontSize(n, c);
                float box = n.num("boxSize", Math.max(32, fs * 1.2f));
                String txt = n.str("text", "");
                float[] s = txt.isEmpty() ? new float[]{0, 0} : c.m.text(txt, fs, 0, n);
                return new float[]{box + (txt.isEmpty() ? 0 : 12 + s[0]), Math.max(box, s[1])};
            }
            case UINode.IMAGE: case UINode.ICON: {
                String img = n.str("image", n.str("icon", ""));
                float[] nat = img.isEmpty() ? null : c.m.image(img);
                if (nat != null && nat[0] > 0) return new float[]{nat[0], nat[1]};
                float d = n.type.equals(UINode.ICON) ? 32 : 64;
                return new float[]{d, d};
            }
            case UINode.PROGRESS: case UINode.SLIDER: case UINode.SWITCH: case UINode.TEXTFIELD:
                return new float[]{resolveFixed(wSpec(n), 200, 200), resolveFixed(hSpec(n, c), 32, 32)};
            case UINode.LINEAR: return intrinsicLinear(n, availW, c);
            case UINode.GRID: return intrinsicGrid(n, availW, c);
            case UINode.SCROLL: return new float[]{Math.min(availW, 300), 200};
            default: return intrinsicStack(n, availW, c);
        }
    }

    private static float resolveFixed(UINode.Size s, float wrapVal, float fillVal) {
        if (s.kind == UINode.PX) return s.v;
        if (s.kind == UINode.WRAP) return wrapVal;
        return fillVal;
    }

    /** Natural size of a child in the main/cross context: fixed px, otherwise intrinsic. fill/pct count as 0 for wrapping parents. */
    private static float childW(UINode ch, float availW, Ctx c, float[] intrCache) {
        UINode.Size s = wSpec(ch);
        if (s.kind == UINode.PX) return s.v;
        if (s.kind == UINode.PCT) return availW >= BIG ? 0 : s.v * availW;
        if (s.kind == UINode.FILL || isStretchX(ch)) return 0;
        return intrCache[0];
    }

    private static float childH(UINode ch, float availH, Ctx c, float[] intrCache) {
        UINode.Size s = hSpec(ch, c);
        if (s.kind == UINode.PX) return s.v;
        if (s.kind == UINode.PCT) return availH >= BIG ? 0 : s.v * availH;
        if (s.kind == UINode.FILL || isStretchY(ch)) return 0;
        return intrCache[1];
    }

    private static boolean isStretchX(UINode n) {
        return n.ax() == UINode.STRETCH;
    }

    private static boolean isStretchY(UINode n) {
        return n.ay() == UINode.STRETCH;
    }

    private static float[] intrinsicStack(UINode n, float availW, Ctx c) {
        float[] p = padding(n);
        float w = 0, h = 0;
        for (UINode ch : live(n)) {
            float[] in = intrinsic(ch, availW >= BIG ? BIG : Math.max(0, availW - p[0] - p[2] - ch.ml - ch.mr), c);
            float cw = childW(ch, availW, c, in) + ch.ml + ch.mr + Math.abs(ch.x), chh = childH(ch, BIG, c, in) + ch.mt + ch.mb + Math.abs(ch.y);
            w = Math.max(w, cw);
            h = Math.max(h, chh);
        }
        return new float[]{w + p[0] + p[2], h + p[1] + p[3]};
    }

    private static boolean horizontal(UINode n) {
        return n.str("orientation", "vertical").toLowerCase(Locale.ROOT).startsWith("h");
    }

    private static float[] intrinsicLinear(UINode n, float availW, Ctx c) {
        float[] p = padding(n);
        boolean hor = horizontal(n);
        float sp = n.num("spacing", 8);
        float main = 0, cross = 0;
        int cnt = 0;
        for (UINode ch : live(n)) {
            float[] in = intrinsic(ch, availW >= BIG ? BIG : Math.max(0, availW - p[0] - p[2] - ch.ml - ch.mr), c);
            float cw = childW(ch, availW, c, in) + ch.ml + ch.mr, chh = childH(ch, BIG, c, in) + ch.mt + ch.mb;
            if (hor) {
                // flex children (fill / weight) fall back to their natural width when the parent wraps
                if ((wSpec(ch).kind == UINode.FILL || ch.weight > 0) && wSpec(ch).kind != UINode.PX) cw = in[0] + ch.ml + ch.mr;
                main += cw;
                cross = Math.max(cross, chh);
            } else {
                if ((hSpec(ch, c).kind == UINode.FILL || ch.weight > 0) && hSpec(ch, c).kind != UINode.PX) chh = in[1] + ch.mt + ch.mb;
                main += chh;
                cross = Math.max(cross, cw);
            }
            cnt++;
        }
        if (cnt > 1) main += sp * (cnt - 1);
        return hor ? new float[]{main + p[0] + p[2], cross + p[1] + p[3]} : new float[]{cross + p[0] + p[2], main + p[1] + p[3]};
    }

    private static int cols(UINode n) {
        return Math.max(1, n.integer("columns", 3));
    }

    private static float[] intrinsicGrid(UINode n, float availW, Ctx c) {
        float[] p = padding(n);
        int cols = cols(n);
        float hs = n.num("hspacing", n.num("spacing", 8)), vs = n.num("vspacing", n.num("spacing", 8));
        ArrayList<UINode> kids = live(n);
        float cellW = 0, rowsH = 0, rowH = n.num("rowHeight", 0);
        // natural cell size = biggest child
        float maxH = 0;
        for (UINode ch : kids) {
            float[] in = intrinsic(ch, BIG, c);
            cellW = Math.max(cellW, childW(ch, BIG, c, in) + ch.ml + ch.mr);
            maxH = Math.max(maxH, childH(ch, BIG, c, in) + ch.mt + ch.mb);
        }
        int rows = (kids.size() + cols - 1) / cols;
        float cw = cellW * cols + hs * (cols - 1);
        if (availW < BIG && n.width().kind == UINode.WRAP) cw = Math.max(cw, 0);
        rowsH = rows * (rowH > 0 ? rowH : maxH) + Math.max(0, rows - 1) * vs;
        return new float[]{cw + p[0] + p[2], rowsH + p[1] + p[3]};
    }

    // ---- resolving one child's rectangle inside a box

    /** Resolved size of a child along one axis for stack-like parents. */
    private static float resolveAxis(UINode.Size s, boolean stretch, float box, float m0, float m1, float intr) {
        if (stretch || s.kind == UINode.FILL) return Math.max(0, box - m0 - m1);
        if (s.kind == UINode.PX) return s.v;
        if (s.kind == UINode.PCT) return s.v * box;
        return intr;
    }

    // ---- main recursive layout

    private static void layoutChildren(UINode n, Ctx c) {
        float[] p = padding(n);
        float boxW = Math.max(0, n.rw - p[0] - p[2]), boxH = Math.max(0, n.rh - p[1] - p[3]);
        switch (n.type) {
            case UINode.LINEAR: layoutLinear(n, p, boxW, boxH, c); break;
            case UINode.GRID: layoutGrid(n, p, boxW, boxH, c); break;
            case UINode.SCROLL: layoutScroll(n, c); break;
            case UINode.PANEL: case UINode.STACK: layoutStack(n, p, boxW, boxH, c); break;
            default: break; // leaf widgets ignore children
        }
        for (UINode ch : n.children) if (ch.visible && !n.type.equals(UINode.SCROLL)) layoutChildren(ch, c);
    }

    /** Converts a "distance from top" rectangle in the parent's padded box to bottom-left coordinates. */
    private static void place(UINode ch, UINode parent, float[] pad, float left, float top, float w, float h) {
        ch.rw = w;
        ch.rh = h;
        ch.rx = pad[0] + left + ch.x;
        ch.ry = parent.rh - pad[1] - top - h + ch.y;
    }

    private static void layoutStack(UINode n, float[] p, float boxW, float boxH, Ctx c) {
        for (UINode ch : live(n)) {
            int ax = ch.ax(), ay = ch.ay();
            float[] in = null;
            UINode.Size ws = wSpec(ch), hs = hSpec(ch, c);
            if (ws.kind == UINode.WRAP || hs.kind == UINode.WRAP) in = intrinsic(ch, Math.max(0, boxW - ch.ml - ch.mr), c);
            float w = resolveAxis(ws, ax == UINode.STRETCH, boxW, ch.ml, ch.mr, in == null ? 0 : in[0]);
            float h = resolveAxis(hs, ay == UINode.STRETCH, boxH, ch.mt, ch.mb, in == null ? 0 : in[1]);
            float left, top;
            if (ax == UINode.STRETCH || ax == UINode.LEFT) left = ch.ml;
            else if (ax == UINode.CENTER) left = ch.ml + (boxW - ch.ml - ch.mr - w) / 2f;
            else left = boxW - ch.mr - w;
            if (ay == UINode.STRETCH || ay == UINode.TOP) top = ch.mt;
            else if (ay == UINode.MIDDLE) top = ch.mt + (boxH - ch.mt - ch.mb - h) / 2f;
            else top = boxH - ch.mb - h;
            place(ch, n, p, left, top, w, h);
        }
    }

    private static final class Item {
        UINode node;
        float main, cross; // outer sizes incl margins (main = fixed or natural), cross natural
        float weight;
        boolean flex;
    }

    private static void layoutLinear(UINode n, float[] p, float boxW, float boxH, Ctx c) {
        boolean hor = horizontal(n);
        float sp = n.num("spacing", 8);
        String align = n.str("align", "start").toLowerCase(Locale.ROOT);
        String gravity = n.str("gravity", "start").toLowerCase(Locale.ROOT).replace("-", "_");
        ArrayList<UINode> kids = live(n);
        float boxMain = hor ? boxW : boxH, boxCross = hor ? boxH : boxW;
        ArrayList<Item> items = new ArrayList<>();
        float used = 0, totalWeight = 0;
        for (UINode ch : kids) {
            Item it = new Item();
            it.node = ch;
            float m0 = hor ? ch.ml : ch.mt, m1 = hor ? ch.mr : ch.mb;
            UINode.Size ms = hor ? wSpec(ch) : hSpec(ch, c);
            float[] in = null;
            if (ms.kind == UINode.WRAP || (hor ? hSpec(ch, c) : wSpec(ch)).kind == UINode.WRAP || ms.kind == UINode.FILL)
                in = intrinsic(ch, hor ? BIG : Math.max(0, boxW - ch.ml - ch.mr), c);
            boolean flex = ch.weight > 0 || ms.kind == UINode.FILL;
            it.flex = flex;
            it.weight = ch.weight > 0 ? ch.weight : (ms.kind == UINode.FILL ? 1f : 0f);
            float natural;
            if (ms.kind == UINode.PX) natural = ms.v;
            else if (ms.kind == UINode.PCT) natural = ms.v * boxMain;
            else natural = in == null ? 0 : (hor ? in[0] : in[1]);
            it.main = natural;
            if (flex) {
                totalWeight += it.weight;
                used += m0 + m1;
            } else used += natural + m0 + m1;
            items.add(it);
        }
        float spacing = Math.max(0, items.size() - 1) * sp;
        float free = boxMain - used - spacing;
        float startOffset = 0, gap = sp;
        if (totalWeight > 0) {
            float per = Math.max(0, free) / totalWeight;
            for (Item it : items) if (it.flex) it.main = per * it.weight;
        } else if (!items.isEmpty()) {
            float f = Math.max(0, free);
            int cnt = items.size();
            switch (gravity) {
                case "center": startOffset = f / 2f; break;
                case "end": case "bottom": case "right": startOffset = f; break;
                case "space_between":
                    if (cnt > 1) gap = sp + f / (cnt - 1);
                    else startOffset = f / 2f;
                    break;
                case "space_around":
                    gap = sp + f / cnt;
                    startOffset = (f / cnt) / 2f;
                    break;
                case "space_evenly":
                    gap = sp + f / (cnt + 1);
                    startOffset = f / (cnt + 1);
                    break;
                default: break;
            }
        }
        float pos = startOffset;
        for (Item it : items) {
            UINode ch = it.node;
            float m0 = hor ? ch.ml : ch.mt, m1 = hor ? ch.mr : ch.mb;
            float cm0 = hor ? ch.mt : ch.ml, cm1 = hor ? ch.mb : ch.mr;
            UINode.Size cs = hor ? hSpec(ch, c) : wSpec(ch);
            int cax = hor ? ch.ay() : ch.ax();
            boolean anchorSet = !"topleft".equals(ch.anchor);
            String eff = align;
            if (anchorSet) {
                if (cax == UINode.STRETCH) eff = "stretch";
                else if (cax == UINode.CENTER) eff = "center";
                else if (cax == (hor ? UINode.BOTTOM : UINode.RIGHT)) eff = "end";
                else eff = "start";
            }
            float[] in = null;
            if (cs.kind == UINode.WRAP) in = intrinsic(ch, hor ? BIG : Math.max(0, boxW - ch.ml - ch.mr), c);
            boolean stretch = eff.equals("stretch") || eff.equals("fill");
            float cross = resolveAxis(cs, stretch, boxCross, cm0, cm1, in == null ? 0 : (hor ? in[1] : in[0]));
            float crossPos;
            switch (eff) {
                case "center": case "middle": crossPos = cm0 + (boxCross - cm0 - cm1 - cross) / 2f; break;
                case "end": case "bottom": case "right": crossPos = boxCross - cm1 - cross; break;
                default: crossPos = cm0; break;
            }
            float mainSize = it.main;
            float mainPos = pos + m0;
            if (hor) place(ch, n, p, mainPos, crossPos, mainSize, cross);
            else place(ch, n, p, crossPos, mainPos, cross, mainSize);
            pos += m0 + mainSize + m1 + gap;
        }
    }

    private static void layoutGrid(UINode n, float[] p, float boxW, float boxH, Ctx c) {
        int cols = cols(n);
        float hs = n.num("hspacing", n.num("spacing", 8)), vs = n.num("vspacing", n.num("spacing", 8));
        float rowH = n.num("rowHeight", 0);
        String align = n.str("align", "center").toLowerCase(Locale.ROOT);
        ArrayList<UINode> kids = live(n);
        float cellW = Math.max(0, (boxW - hs * (cols - 1)) / cols);
        int rows = (kids.size() + cols - 1) / cols;
        float top = 0;
        for (int r = 0; r < rows; r++) {
            float maxH = 0;
            for (int k = 0; k < cols; k++) {
                int idx = r * cols + k;
                if (idx >= kids.size()) break;
                UINode ch = kids.get(idx);
                UINode.Size hsz = hSpec(ch, c);
                float[] in = hsz.kind == UINode.WRAP ? intrinsic(ch, Math.max(0, cellW - ch.ml - ch.mr), c) : null;
                float h = hsz.kind == UINode.PX ? hsz.v : hsz.kind == UINode.WRAP ? in[1] : 0;
                maxH = Math.max(maxH, h + ch.mt + ch.mb);
            }
            float thisRow = rowH > 0 ? rowH : maxH;
            for (int k = 0; k < cols; k++) {
                int idx = r * cols + k;
                if (idx >= kids.size()) break;
                UINode ch = kids.get(idx);
                float cx = k * (cellW + hs);
                UINode.Size wsz = wSpec(ch), hsz = hSpec(ch, c);
                boolean stretchW = align.equals("stretch") || wsz.kind == UINode.FILL || ch.ax() == UINode.STRETCH;
                float[] in = wsz.kind == UINode.WRAP ? intrinsic(ch, Math.max(0, cellW - ch.ml - ch.mr), c) : null;
                float w = resolveAxis(wsz, stretchW, cellW, ch.ml, ch.mr, in == null ? 0 : in[0]);
                float[] inh = hsz.kind == UINode.WRAP ? intrinsic(ch, w, c) : null;
                float h = resolveAxis(hsz, hsz.kind == UINode.FILL || ch.ay() == UINode.STRETCH || (align.equals("stretch") && ch.height().kind == UINode.AUTO && thisRow > 0 && ch.type.equals(UINode.BUTTON)), thisRow, ch.mt, ch.mb, inh == null ? 0 : inh[1]);
                float left;
                if (align.equals("center") || align.equals("middle")) left = cx + ch.ml + (cellW - ch.ml - ch.mr - w) / 2f;
                else if (align.equals("end") || align.equals("right")) left = cx + cellW - ch.mr - w;
                else left = cx + ch.ml;
                place(ch, n, p, left, top + ch.mt, w, h);
            }
            top += thisRow + vs;
        }
    }

    private static void layoutScroll(UINode n, Ctx c) {
        String dir = n.str("direction", "vertical").toLowerCase(Locale.ROOT);
        boolean vert = dir.startsWith("v") || dir.equals("both"), hor = dir.startsWith("h") || dir.equals("both");
        float[] p = padding(n);
        float viewW = Math.max(0, n.rw - p[0] - p[2]), viewH = Math.max(0, n.rh - p[1] - p[3]);
        ArrayList<UINode> kids = live(n);
        float cw = viewW, chh = viewH;
        // pass 1: size of every child (scroll axes wrap, the others fill)
        for (UINode ch : kids) {
            UINode.Size wsz = ch.width(), hsz = ch.height();
            float[] in = intrinsic(ch, hor ? BIG : viewW - ch.ml - ch.mr, c);
            float w, h;
            if (hor && (wsz.kind == UINode.AUTO || wsz.kind == UINode.WRAP)) w = in[0];
            else w = resolveAxis(wsz.kind == UINode.AUTO ? UINode.SIZE_FILL : wsz, ch.ax() == UINode.STRETCH, viewW, ch.ml, ch.mr, in[0]);
            if (vert && (hsz.kind == UINode.AUTO || hsz.kind == UINode.WRAP)) {
                // the height of a wrapping container depends on its width: measure again with the real width
                float[] in2 = intrinsic(ch, w, c);
                h = in2[1];
            } else h = resolveAxis(hsz.kind == UINode.AUTO ? UINode.SIZE_FILL : hsz, ch.ay() == UINode.STRETCH, viewH, ch.mt, ch.mb, in[1]);
            ch.rw = w;
            ch.rh = h;
            cw = Math.max(cw, w + ch.ml + ch.mr);
            chh = Math.max(chh, h + ch.mt + ch.mb);
        }
        if (!hor) cw = viewW;
        if (!vert) chh = viewH;
        n.contentW = cw;
        n.contentH = chh;
        // pass 2: place children in the content area (top aligned) and lay out their subtree
        for (UINode ch : kids) {
            ch.rx = ch.ml + ch.x;
            ch.ry = chh - ch.mt - ch.rh + ch.y;
            layoutChildren(ch, c);
        }
    }
}
