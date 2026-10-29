package com.star4droid.template.ui;

/**
 * Pure-Java rasterizer for the built-in look of the widgets (rounded boxes with border, gloss, glow, shadow, 3D edge, tick marks).
 * It returns RGBA8888 pixel arrays (row 0 = top), the gdx layer turns them into textures; offline tests turn them into PNG.
 * Anti-aliased with signed distance fields, so any radius looks smooth.
 */
public final class UIShapes {
    private UIShapes() {}

    /** Parameters of one box. Colors are RGBA8888. */
    public static final class Box {
        public int w, h;
        /** Corner radius; &gt;= min(w,h)/2 gives a pill / circle. */
        public float radius = 12;
        /** Cut corners (hexagon-ish arcade look) instead of round ones. */
        public boolean cut;
        public float borderWidth;
        public int borderColor = 0x000000ff;
        public int fillTop = 0xffffffff, fillBottom = 0xffffffff;
        /** 0..1 white highlight on the upper half. */
        public float gloss;
        /** 0..1 neon halo outside the shape (uses glowColor). */
        public float glow;
        public int glowColor = 0xffffffff;
        /** Drop shadow offset / softness in px. */
        public float shadow;
        public int shadowColor = 0x00000066;
        /** 3D edge: a darker copy of the shape shown below the face (px). */
        public float bevel;
        public int bevelColor = 0x00000066;
        /** How far the face is pushed down into the bevel (pressed look), px. */
        public float pressed;
        /** Draw only the border and glow, no fill. */
        public boolean outlineOnly;

        /** Space that must stay free around the face so glow / shadow fit inside the image. */
        public int margin() {
            float m = Math.max(shadow * 1.4f, glow * 10f);
            return (int) Math.ceil(m) + (shadow > 0 || glow > 0 ? 1 : 0);
        }

        public Box size(int w, int h) {
            this.w = w;
            this.h = h;
            return this;
        }
    }

    private static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    private static int ch(int c, int shift) {
        return (c >>> shift) & 255;
    }

    /** Straight-alpha "over": src over dst, both RGBA8888. */
    static int over(int dst, int src, float srcCov) {
        float sa = (src & 255) / 255f * srcCov;
        if (sa <= 0) return dst;
        float da = (dst & 255) / 255f;
        float oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        int r = Math.round((ch(src, 24) * sa + ch(dst, 24) * da * (1 - sa)) / oa);
        int g = Math.round((ch(src, 16) * sa + ch(dst, 16) * da * (1 - sa)) / oa);
        int b = Math.round((ch(src, 8) * sa + ch(dst, 8) * da * (1 - sa)) / oa);
        return ((r & 255) << 24) | ((g & 255) << 16) | ((b & 255) << 8) | (Math.round(oa * 255) & 255);
    }

    /** Signed distance (px, negative inside) to a rounded / cut box centered at (cx,cy) with half sizes hx,hy. */
    static float sdf(float px, float py, float cx, float cy, float hx, float hy, float r, boolean cut) {
        float dx = Math.abs(px - cx), dy = Math.abs(py - cy);
        if (cut) {
            float c = Math.min(r, Math.min(hx, hy));
            float box = Math.max(dx - hx, dy - hy);
            float diag = (dx + dy - (hx + hy - c)) * 0.70710678f;
            return Math.max(box, diag);
        }
        r = Math.min(r, Math.min(hx, hy));
        float qx = dx - (hx - r), qy = dy - (hy - r);
        float outside = (float) Math.sqrt(Math.max(qx, 0) * Math.max(qx, 0) + Math.max(qy, 0) * Math.max(qy, 0));
        return outside + Math.min(Math.max(qx, qy), 0) - r;
    }

    /** Renders a box. Result length = w*h, row 0 on top. */
    public static int[] box(Box b) {
        int w = Math.max(2, b.w), h = Math.max(2, b.h);
        int[] out = new int[w * h];
        int m = b.margin();
        float bevel = Math.max(0, b.bevel);
        float press = Math.min(Math.max(0, b.pressed), bevel);
        float bodyW = w - 2 * m;
        float faceH = h - 2 * m - bevel;
        if (bodyW < 2 || faceH < 2) { bodyW = Math.max(2, bodyW); faceH = Math.max(2, faceH); }
        float cx = w / 2f, hx = bodyW / 2f, hy = faceH / 2f;
        float faceTop = m + press;
        float cyFace = faceTop + hy;
        float cyBevel = m + bevel + hy; // the underlay sits at the bottom
        float bw = clamp(b.borderWidth, 0, Math.min(hx, hy));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                int c = 0;
                if (b.shadow > 0) {
                    float d = sdf(px, py, cx, cyBevel + b.shadow * 0.45f, hx, hy, b.radius, b.cut);
                    float cov = clamp(0.5f - d / (b.shadow * 1.2f + 1f), 0, 1);
                    cov = cov * cov;
                    c = over(c, b.shadowColor, cov);
                }
                float dFace = sdf(px, py, cx, cyFace, hx, hy, b.radius, b.cut);
                if (b.glow > 0) {
                    float r = Math.max(2f, b.glow * 8f);
                    float cov = dFace > 0 ? (float) Math.exp(-dFace / (r * 0.45f)) : 1f;
                    c = over(c, b.glowColor, clamp(cov * 0.8f * Math.min(1f, b.glow), 0, 1) * (dFace > 0 ? 1 : 0.0f));
                }
                if (bevel > 0) {
                    float dB = sdf(px, py, cx, cyBevel, hx, hy, b.radius, b.cut);
                    c = over(c, b.bevelColor, clamp(0.5f - dB, 0, 1));
                }
                float covOuter = clamp(0.5f - dFace, 0, 1);
                if (covOuter > 0) {
                    float dIn = dFace + bw;
                    float covIn = bw > 0 ? clamp(0.5f - dIn, 0, 1) : covOuter;
                    if (!b.outlineOnly) {
                        float t = clamp((py - (faceTop)) / Math.max(1f, 2 * hy), 0, 1);
                        int fill = UITheme.mix(b.fillTop, b.fillBottom, t);
                        c = over(c, fill, bw > 0 ? covOuter : covOuter);
                        if (b.gloss > 0) {
                            float gy = (py - faceTop - bw) / Math.max(1f, hy * 0.95f);
                            if (gy >= 0 && gy < 1) {
                                float fall = (1 - gy);
                                float edge = clamp(Math.min(px - (cx - hx), (cx + hx) - px) / Math.max(4f, hy * 0.6f), 0, 1);
                                c = over(c, 0xffffffff, clamp(b.gloss * 0.55f * fall * fall * (0.35f + 0.65f * edge), 0, 1) * covIn);
                            }
                        }
                    }
                    if (bw > 0) c = over(c, b.borderColor, clamp(covOuter - covIn, 0, 1));
                }
                out[y * w + x] = c;
            }
        }
        return out;
    }

    /** A filled circle (knobs, radio dots) with the same effects; diameter d. */
    public static int[] circle(int d, int fillTop, int fillBottom, float border, int borderColor, float gloss, float glow, int glowColor, float shadow) {
        Box b = new Box();
        b.w = d + 2 * ((int) Math.ceil(Math.max(shadow * 1.4f, glow * 10f)) + ((shadow > 0 || glow > 0) ? 1 : 0));
        b.h = b.w;
        b.radius = b.w;
        b.fillTop = fillTop;
        b.fillBottom = fillBottom;
        b.borderWidth = border;
        b.borderColor = borderColor;
        b.gloss = gloss;
        b.glow = glow;
        b.glowColor = glowColor;
        b.shadow = shadow;
        return box(b);
    }

    // ------------------------------------------------------------------ symbols

    private static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay, wx = px - ax, wy = py - ay;
        float t = clamp((wx * vx + wy * vy) / (vx * vx + vy * vy), 0, 1);
        float dx = px - (ax + t * vx), dy = py - (ay + t * vy);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Check mark, cross or dot of the given color inside a size x size image. */
    public static int[] symbol(int size, String shape, int color) {
        int[] out = new int[size * size];
        float s = size, thick = Math.max(2f, s * 0.14f);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float px = x + 0.5f, py = y + 0.5f, d;
                switch (shape) {
                    case "cross": {
                        float a = segDist(px, py, s * .25f, s * .25f, s * .75f, s * .75f), b2 = segDist(px, py, s * .75f, s * .25f, s * .25f, s * .75f);
                        d = Math.min(a, b2) - thick / 2;
                        break;
                    }
                    case "dot": {
                        float dx = px - s / 2, dy = py - s / 2;
                        d = (float) Math.sqrt(dx * dx + dy * dy) - s * 0.28f;
                        break;
                    }
                    case "square": {
                        d = Math.max(Math.abs(px - s / 2), Math.abs(py - s / 2)) - s * 0.26f;
                        break;
                    }
                    default: { // tick
                        float a = segDist(px, py, s * .24f, s * .52f, s * .43f, s * .72f), b2 = segDist(px, py, s * .43f, s * .72f, s * .78f, s * .30f);
                        d = Math.min(a, b2) - thick / 2;
                        break;
                    }
                }
                out[y * size + x] = over(0, color, clamp(0.5f - d, 0, 1));
            }
        }
        return out;
    }

    /** Segmented bar overlay: dark gaps every {@code w/segments} px (used by game_bar). */
    public static void segments(int[] px, int w, int h, int segments, int gapColor, int gap, boolean vertical) {
        if (segments < 2) return;
        int len = vertical ? h : w;
        for (int s = 1; s < segments; s++) {
            int p0 = Math.round(s * (float) len / segments) - gap / 2;
            for (int p = p0; p < p0 + Math.max(1, gap); p++) {
                if (p < 0 || p >= len) continue;
                int cross = vertical ? w : h;
                for (int q = 0; q < cross; q++) {
                    int i = vertical ? p * w + q : q * w + p;
                    if ((px[i] & 255) != 0) px[i] = over(px[i], gapColor, 1f);
                }
            }
        }
    }

    /** Diagonal stripes overlay (progress "candy" look). */
    public static void stripes(int[] px, int w, int h, int period, int color, boolean vertical) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if ((px[i] & 255) == 0) continue;
                int t = Math.floorMod(vertical ? x - y : x + y, period * 2);
                if (t < period) px[i] = over(px[i], color, ((px[i] & 255) / 255f));
            }
        }
    }
}
