package com.star4droid.star2d.ui.uimaker;

import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UILayout;
import com.star4droid.template.ui.UINode;

import java.util.LinkedHashMap;

/**
 * Absolute rectangles of every node of a document for a given screen size. Pure Java (no libGDX, no android): the layout comes from
 * {@link UILayout}, so it works in the wireframe preview, in the AI tool and in offline tests.
 *
 * Coordinates: logical pixels of the screen, origin TOP-LEFT, y down (what Android views use).
 */
public final class UiRects {
    /** One node rectangle. */
    public static final class R {
        public float x, y, w, h;
        /** False when the node (or an ancestor) is hidden: it has no rectangle. */
        public boolean shown = true;
        /** Inside a ScrollView: the position is the one at scroll 0 and may be outside the viewport. */
        public boolean scrolled;
        public UINode node;

        public boolean contains(float px, float py) {
            return shown && px >= x && px <= x + w && py >= y && py <= y + h;
        }

        public float area() {
            return Math.max(0, w) * Math.max(0, h);
        }
    }

    public final float width, height;
    /** Insertion order = tree order (parents before children). */
    public final LinkedHashMap<String, R> rects = new LinkedHashMap<>();

    private UiRects(float w, float h) {
        width = w;
        height = h;
    }

    /** Lays the document out in a screen of {@code w x h} and collects the rectangles. Changes the layout fields of the nodes. */
    public static UiRects compute(UIDocument doc, float w, float h, UILayout.Measurer m) {
        UILayout.layout(doc.root, doc.theme, w, h, m);
        UiRects out = new UiRects(w, h);
        out.walk(doc.root, 0, 0, false);
        return out;
    }

    /** Rectangles measured by the real renderer: id -> {x, y(top), w, h, shown}. Nodes missing from the map count as hidden. */
    public static UiRects fromMap(UIDocument doc, float w, float h, java.util.Map<String, float[]> m) {
        UiRects out = new UiRects(w, h);
        for (UINode n : doc.allNodes()) {
            R r = new R();
            r.node = n;
            float[] a = m.get(n.id);
            if (a == null || a[4] == 0f) r.shown = false;
            else {
                r.x = a[0];
                r.y = a[1];
                r.w = a[2];
                r.h = a[3];
            }
            for (UINode p = n.parent; p != null; p = p.parent) if (p.type.equals(UINode.SCROLL)) r.scrolled = true;
            out.rects.put(n.id, r);
        }
        return out;
    }

    private void walk(UINode n, float px, float py, boolean scrolled) {
        float ax = px + n.rx, ay = py + n.ry;
        R r = new R();
        r.node = n;
        r.x = ax;
        r.y = height - (ay + n.rh);
        r.w = n.rw;
        r.h = n.rh;
        r.scrolled = scrolled;
        rects.put(n.id, r);
        if (!n.isContainer()) return;
        float cx = ax, cy = ay;
        boolean sc = scrolled;
        if (n.type.equals(UINode.SCROLL)) {
            float[] p = UILayout.padding(n);
            float viewH = Math.max(0, n.rh - p[1] - p[3]);
            cx = ax + p[0];
            cy = ay + p[3] + viewH - n.contentH;
            sc = true;
        }
        for (UINode c : n.children) {
            if (!c.visible) {
                markHidden(c);
                continue;
            }
            walk(c, cx, cy, sc);
        }
    }

    private void markHidden(UINode n) {
        R r = new R();
        r.node = n;
        r.shown = false;
        rects.put(n.id, r);
        for (UINode c : n.children) markHidden(c);
    }

    public R get(String id) {
        return rects.get(id);
    }

    /** Deepest shown node at the point (children win over parents, later siblings over earlier ones), or null. Hidden / scrolled-out parts are respected. */
    public R hit(float px, float py) {
        R best = null;
        for (R r : rects.values()) {
            if (!r.shown) continue;
            if (r.node.parent == null) continue; // the root fills everything: tapping empty space selects nothing
            if (!r.contains(px, py)) continue;
            if (!insideClips(r, px, py)) continue;
            best = r; // later in the walk = deeper / above
        }
        return best;
    }

    /** A node inside a ScrollView is only reachable through the viewport of that ScrollView. */
    private boolean insideClips(R r, float px, float py) {
        for (UINode p = r.node.parent; p != null; p = p.parent) {
            if (p.type.equals(UINode.SCROLL)) {
                R pr = rects.get(p.id);
                if (pr != null && pr.shown && !pr.contains(px, py)) return false;
            }
        }
        return true;
    }
}
