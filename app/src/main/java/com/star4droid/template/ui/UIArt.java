package com.star4droid.template.ui;

/**
 * Describes the pictures that the built-in look of every widget needs (button face, panel, slider track / fill / knob, check box,
 * switch, text field background) and renders them to pixels with {@link UIShapes}. No libGDX here: the game turns an {@link Art}
 * into a texture / 9-patch, offline tests turn it into PNG. Art is cheap to describe (just numbers) and expensive to render, so
 * callers cache the result by {@link Art#key}.
 *
 * Geometry: an Art covers the widget rectangle PLUS {@link Art#margin} px on every side (glow / shadow). Draw it at (-margin,-margin)
 * with size (w + 2*margin, h + 2*margin). When {@link Art#nine} is true the texture is small and has stretchable middle
 * (insets l, r, t, b in texture px); otherwise it is drawn 1:1.
 */
public final class UIArt {
    private UIArt() {}

    /** Test switch: render stretchable art at its real size (previews). */
    public static boolean EXACT = false;

    public static final class Art {
        public String key;
        /** Texture size. */
        public int w, h;
        /** Logical size = widget size + 2*margin. */
        public int drawW, drawH;
        public int margin;
        public boolean nine;
        public int l, r, t, b;

        UIShapes.Box box;
        boolean circle;
        String symbol;
        int symbolColor;
        float symbolScale = 0.7f;
        int segments, segmentGap;
        boolean stripes, vertical;
        int overlayColor;
        boolean underline;
        float underlineWidth;
        int underlineColor;
        /** The shape is drawn inside a texture that is bigger (knob in a box...). */

        /** Renders the pixels (RGBA8888, row 0 top, w*h entries). */
        public int[] render() {
            int[] px = UIShapes.box(box);
            if (underline) {
                int rows = Math.max(1, Math.round(underlineWidth));
                for (int y = 0; y < box.h; y++) for (int x = 0; x < box.w; x++) px[y * box.w + x] = 0;
                for (int y = box.h - margin - rows; y < box.h - margin; y++)
                    for (int x = margin; x < box.w - margin; x++) if (y >= 0) px[y * box.w + x] = underlineColor;
            }
            if (segments > 1) UIShapes.segments(px, box.w, box.h, segments, overlayColor, Math.max(2, segmentGap), vertical);
            if (stripes) UIShapes.stripes(px, box.w, box.h, Math.max(6, Math.min(box.w, box.h) / 3), overlayColor, vertical);
            if (symbol != null) {
                int d = Math.round(Math.min(box.w - 2 * margin, box.h - 2 * margin) * symbolScale);
                if (d >= 4) {
                    int[] s = UIShapes.symbol(d, symbol, symbolColor);
                    int ox = (box.w - d) / 2, oy = (box.h - d) / 2;
                    for (int y = 0; y < d; y++) for (int x = 0; x < d; x++) {
                        int i = (y + oy) * box.w + x + ox;
                        px[i] = UIShapes.over(px[i], s[y * d + x], 1f);
                    }
                }
            }
            return px;
        }
    }

    // ------------------------------------------------------------------ construction helpers

    private static Art finish(UIShapes.Box bx, int faceW, int faceH, boolean nine, String keyExtra) {
        Art a = new Art();
        int m = bx.margin();
        a.margin = m;
        a.drawW = faceW + 2 * m;
        a.drawH = faceH + 2 * m;
        a.nine = nine && !EXACT;
        a.box = bx;
        if (a.nine) {
            int k = m + (int) Math.ceil(Math.min(bx.radius, Math.min(faceW, faceH) / 2f)) + (int) Math.ceil(bx.bevel + bx.borderWidth) + 2;
            int tw = a.drawW > 2 * k + 2 ? 2 * k + 2 : a.drawW, th = a.drawH > 2 * k + 2 ? 2 * k + 2 : a.drawH;
            a.w = Math.max(3, tw);
            a.h = Math.max(3, th);
            a.l = tw == a.drawW ? (a.w - 1) / 2 : k;
            a.r = tw == a.drawW ? a.w - 1 - a.l : k;
            a.t = th == a.drawH ? (a.h - 1) / 2 : k;
            a.b = th == a.drawH ? a.h - 1 - a.t : k;
        } else {
            a.w = a.drawW;
            a.h = a.drawH;
        }
        bx.w = a.w;
        bx.h = a.h;
        a.key = keyExtra + "|" + a.w + "x" + a.h;
        return a;
    }

    private static String k(Object... v) {
        StringBuilder sb = new StringBuilder();
        for (Object o : v) sb.append(o).append(',');
        return sb.toString();
    }

    private static float clampRadius(float r, float w, float h) {
        return Math.max(0, Math.min(r, Math.min(w, h) / 2f));
    }

    private static int fillAlpha(int c, float a) {
        if (a >= 1f) return c;
        return UITheme.withAlpha(c, ((c & 255) / 255f) * a);
    }

    // ------------------------------------------------------------------ Button

    public static Art button(UINode n, UITheme th, float w, float h, boolean pressed) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        int base = sp.color("buttonColor", "primary");
        String colorSpec = sp.s("buttonColor", "primary");
        if (colorSpec.equals("ghost")) base = 0x00000000;
        boolean outline = sp.f("outline", 0) > 0;
        String shape = n.str("shape", sp.s("shape", ""));
        boolean cut = shape.equals("cut") || (shape.isEmpty() && th.cutCorners());
        float radius = n.has("radius") ? n.num("radius", 12) : th.number("radius");
        if (shape.equals("pill")) radius = 10000;
        else if (shape.equals("square")) radius = 4;
        else if (cut && !n.has("radius")) radius = 14;
        radius = clampRadius(radius, w, h);
        UIShapes.Box b = new UIShapes.Box();
        b.radius = radius;
        b.cut = cut;
        b.borderWidth = sp.f("border", th.number("borderWidth"));
        if (base != 0 && (base & 255) > 0) {
            if (outline) {
                b.borderColor = base;
                b.fillTop = b.fillBottom = UITheme.withAlpha(base, sp.f("glow", 0) > 0 ? 0.10f : 0.14f);
            } else {
                boolean grad = th.gradient() || sp.f("gloss", 0) > 0;
                b.fillTop = grad ? UITheme.lighten(base, .16f) : base;
                b.fillBottom = grad ? UITheme.darken(base, .12f) : base;
                b.borderColor = sp.has("borderColor") ? sp.color("borderColor", "border") : UITheme.darken(base, .5f);
            }
        } else {
            b.fillTop = b.fillBottom = 0;
            b.borderColor = 0;
            b.borderWidth = 0;
        }
        float fa = sp.f("fillAlpha", 1f);
        if (fa < 1f) {
            b.fillTop = fillAlpha(b.fillTop, fa);
            b.fillBottom = fillAlpha(b.fillBottom, fa);
            if (!outline) b.borderColor = UITheme.withAlpha(base, 0.5f);
        }
        b.gloss = sp.f("gloss", th.number("gloss"));
        b.glow = sp.f("glow", th.number("glow"));
        b.glowColor = outline || b.glow > 0 ? (base != 0 ? base : th.color("primary")) : base;
        b.shadow = sp.f("shadow", th.number("shadow"));
        b.shadowColor = 0x00000070;
        b.bevel = sp.f("bevel", th.number("bevel"));
        b.bevelColor = UITheme.withAlpha(UITheme.darken(base != 0 ? base : th.color("surface"), .45f), 1f);
        if (b.bevel > 0 && b.shadow > 0) b.shadow = Math.min(b.shadow, 3);
        if (pressed) {
            b.pressed = b.bevel > 0 ? Math.min(b.bevel, Math.max(2f, b.bevel * 0.75f)) : 0;
            b.fillTop = UITheme.darken(b.fillTop, .10f);
            b.fillBottom = UITheme.darken(b.fillBottom, .10f);
            b.shadow *= 0.4f;
            b.gloss *= 0.6f;
        }
        return finish(b, Math.round(w), Math.round(h), true, "btn" + k(radius, cut, b.borderWidth, b.borderColor, b.fillTop, b.fillBottom, b.gloss, b.glow, b.glowColor, b.shadow, b.bevel, b.pressed, Math.round(w) < 1 ? 0 : 1));
    }

    /** Text color of a button: "color" property, else dark/light depending on the fill so it is always readable. */
    public static int buttonTextColor(UINode n, UITheme th) {
        if (n.has("color")) return th.resolveColor(n.str("color", ""), th.color("text"));
        UIStyles.Spec sp = UIStyles.spec(n, th);
        String cs = sp.s("buttonColor", "primary");
        boolean flatLook = sp.f("outline", 0) > 0 || cs.equals("ghost") || cs.equals("flat") || sp.f("fillAlpha", 1f) < 1f;
        if (flatLook) return th.color("text");
        return UITheme.onColor(sp.color("buttonColor", "primary"), th);
    }

    // ------------------------------------------------------------------ Panel

    public static Art panel(UINode n, UITheme th, float w, float h) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        int base = sp.color("panelColor", "surface");
        if ("text".equals(sp.s("panelColor", "surface")) && sp.f("fillAlpha", 1) < 1) base = th.color("text");
        UIShapes.Box b = new UIShapes.Box();
        b.radius = clampRadius(n.has("radius") ? n.num("radius", 12) : th.number("radius"), w, h);
        b.cut = th.cutCorners() && !n.has("radius");
        b.borderWidth = sp.f("border", th.number("borderWidth"));
        b.borderColor = sp.color("borderColor", "border");
        b.fillTop = b.fillBottom = fillAlpha(base, sp.f("fillAlpha", 1f));
        b.gloss = sp.f("gloss", 0);
        b.shadow = sp.f("shadow", th.number("shadow"));
        b.bevel = sp.f("bevel", 0);
        b.glow = sp.f("glow", th.number("glow") > 0 ? 0.6f : 0);
        b.glowColor = b.borderColor;
        b.bevelColor = UITheme.darken(base, .35f);
        b.outlineOnly = (base & 255) == 0;
        return finish(b, Math.round(w), Math.round(h), true, "pnl" + k(b.radius, b.cut, b.borderWidth, b.borderColor, b.fillTop, b.gloss, b.shadow, b.bevel, b.glow, b.outlineOnly));
    }

    /** Background of Stack / Linear / Grid / ScrollView when bgColor is set. */
    public static Art plain(int color, float radius, float w, float h) {
        UIShapes.Box b = new UIShapes.Box();
        b.radius = clampRadius(radius, w, h);
        b.fillTop = b.fillBottom = color;
        return finish(b, Math.round(w), Math.round(h), true, "bg" + k(radius, color));
    }

    // ------------------------------------------------------------------ Slider / Progress

    /** Thickness of the bar of a Slider / Progress node. */
    public static float trackThickness(UINode n, UITheme th, float boxH) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        float t = sp.f("trackHeight", n.type.equals(UINode.SLIDER) ? 8f : 16f);
        if (n.type.equals(UINode.PROGRESS) && !n.has("trackHeight") && n.h != null) t = boxH;
        return t;
    }

    /** Empty part of the bar. {@code len x thick} is the bar rectangle (for vertical bars thick is the width). */
    public static Art track(UINode n, UITheme th, float len, float thick, boolean vertical) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        UIShapes.Box b = new UIShapes.Box();
        float bw = sp.f("trackBorder", 0);
        float rad = sp.f("trackRadius", -1);
        b.radius = rad < 0 ? 10000 : rad;
        b.radius = clampRadius(b.radius, vertical ? thick : len, vertical ? len : thick);
        b.borderWidth = bw;
        b.borderColor = sp.color("borderColor", "border");
        int c = sp.color("trackColor", "surfaceAlt");
        b.fillTop = b.fillBottom = c;
        if (sp.f("glow", 0) > 0) {
            b.glow = sp.f("glow", 0) * 0.5f;
            b.glowColor = sp.color("fillColor", "primary");
        }
        int fw = Math.round(vertical ? thick : len), fh = Math.round(vertical ? len : thick);
        Art a = finish(b, fw, fh, true, "trk" + k(b.radius, bw, b.borderColor, c, b.glow, b.glowColor, vertical));
        return a;
    }

    /** Filled part of the bar at FULL size; the widget clips it to the current value. */
    public static Art fill(UINode n, UITheme th, float len, float thick, boolean vertical) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        float bw = sp.f("trackBorder", 0);
        float rad = sp.f("trackRadius", -1);
        float lenIn = Math.max(2, len - 2 * bw), thickIn = Math.max(2, thick - 2 * bw);
        UIShapes.Box b = new UIShapes.Box();
        b.radius = rad < 0 ? 10000 : Math.max(0, rad - bw);
        b.radius = clampRadius(b.radius, vertical ? thickIn : lenIn, vertical ? lenIn : thickIn);
        int base = sp.color("fillColor", "primary");
        boolean grad = sp.f("gloss", 0) > 0 || th.gradient();
        b.fillTop = grad ? UITheme.lighten(base, .18f) : base;
        b.fillBottom = grad ? UITheme.darken(base, .14f) : base;
        b.gloss = sp.f("gloss", 0);
        if (sp.f("glow", 0) > 0) {
            b.glow = sp.f("glow", 0);
            b.glowColor = base;
        }
        int fw = Math.round(vertical ? thickIn : lenIn), fh = Math.round(vertical ? lenIn : thickIn);
        Art a = finish(b, fw, fh, true, "fil" + k(b.radius, base, b.gloss, b.glow, grad, vertical, sp.f("segments", 0), sp.f("stripes", 0)));
        a.segments = (int) sp.f("segments", 0);
        a.segmentGap = 2;
        a.stripes = sp.f("stripes", 0) > 0;
        a.vertical = vertical;
        a.overlayColor = a.stripes ? 0xffffff22 : UITheme.withAlpha(sp.color("trackColor", "background"), 1f);
        if (a.segments > 1) a.key += "s" + a.segments;
        return a;
    }

    /** Overlay of segments on the TRACK too (game_bar), applied to the track art by the caller through this flag. */
    public static boolean trackHasSegments(UINode n, UITheme th) {
        return UIStyles.spec(n, th).f("segments", 0) > 1;
    }

    /** Knob of a Slider (circle, rect or diamond). */
    public static Art knob(UINode n, UITheme th) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        String shape = sp.s("knobShape", "circle");
        float size = sp.f("knobSize", 32f);
        float kw = shape.equals("rect") ? sp.f("knobW", size * 0.5f) : size, kh = shape.equals("rect") ? sp.f("knobH", size) : size;
        if (n.has("knobSize") && shape.equals("rect")) {
            kw = size * 0.45f;
            kh = size;
        }
        if (shape.equals("none")) {
            kw = kh = 2;
        }
        UIShapes.Box b = new UIShapes.Box();
        b.radius = shape.equals("rect") ? 4 : 10000;
        b.cut = shape.equals("diamond");
        if (b.cut) b.radius = Math.min(kw, kh) / 2f;
        int base = sp.color("knobColor", "text");
        boolean glossy = sp.f("knobGloss", 0) > 0;
        b.fillTop = glossy ? UITheme.lighten(base, .2f) : base;
        b.fillBottom = glossy ? UITheme.darken(base, .1f) : base;
        b.gloss = sp.f("knobGloss", 0);
        b.borderWidth = sp.f("knobBorder", 0);
        b.borderColor = sp.color("knobBorderColor", "border");
        b.glow = sp.f("knobGlow", 0);
        b.glowColor = b.borderColor;
        b.shadow = 0;
        if (shape.equals("none")) b.fillTop = b.fillBottom = 0;
        return finish(b, Math.round(kw), Math.round(kh), false, "knb" + k(shape, kw, kh, base, b.gloss, b.borderWidth, b.borderColor, b.glow));
    }

    // ------------------------------------------------------------------ Checkbox / Radio / Switch

    public static Art check(UINode n, UITheme th, float size, boolean checked) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        boolean radio = n.type.equals(UINode.RADIO);
        UIShapes.Box b = new UIShapes.Box();
        b.radius = radio ? 10000 : clampRadius(sp.f("boxRadius", 6), size, size);
        int box = sp.color("boxColor", "surface");
        b.fillTop = b.fillBottom = box;
        b.gloss = sp.f("gloss", 0);
        if (b.gloss > 0) {
            b.fillTop = UITheme.lighten(box, .08f);
            b.fillBottom = UITheme.darken(box, .06f);
        }
        b.borderWidth = sp.f("border", 2);
        b.borderColor = checked && sp.f("glow", 0) > 0 ? sp.color("checkColor", "primary") : sp.color("borderColor", "border");
        b.glow = checked ? sp.f("glow", 0) * 0.6f : 0;
        b.glowColor = sp.color("checkColor", "primary");
        Art a = finish(b, Math.round(size), Math.round(size), false, "chk" + k(radio, b.radius, box, b.gloss, b.borderWidth, b.borderColor, b.glow, checked, sp.s("checkShape", "tick"), sp.color("checkColor", "primary")));
        if (checked) {
            a.symbol = sp.s("checkShape", radio ? "dot" : "tick");
            a.symbolColor = sp.color("checkColor", "primary");
        }
        return a;
    }

    public static Art switchTrack(UINode n, UITheme th, float w, float h, boolean on) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        UIShapes.Box b = new UIShapes.Box();
        float rad = sp.f("trackRadius", -1);
        b.radius = clampRadius(rad < 0 ? 10000 : rad, w, h);
        int c = on ? sp.color("trackOnColor", "success") : sp.color("trackOffColor", "surfaceAlt");
        boolean grad = sp.f("gloss", 0) > 0;
        b.fillTop = grad ? UITheme.lighten(c, .15f) : c;
        b.fillBottom = grad ? UITheme.darken(c, .12f) : c;
        b.gloss = sp.f("gloss", 0);
        b.borderWidth = sp.f("border", 0);
        b.borderColor = on && sp.f("glow", 0) > 0 ? c : sp.color("borderColor", "border");
        b.glow = on ? sp.f("glow", 0) * 0.7f : 0;
        b.glowColor = c;
        return finish(b, Math.round(w), Math.round(h), true, "swt" + k(b.radius, c, b.gloss, b.borderWidth, b.borderColor, b.glow, on));
    }

    public static Art switchKnob(UINode n, UITheme th, float h) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        float d = Math.max(6, h * sp.f("knobScale", 0.8f));
        boolean rect = "rect".equals(sp.s("knobShape", "circle"));
        UIShapes.Box b = new UIShapes.Box();
        b.radius = rect ? 3 : 10000;
        int c = sp.color("knobColor", "text");
        b.fillTop = c;
        b.fillBottom = UITheme.darken(c, .05f);
        b.shadow = 0;
        return finish(b, Math.round(d), Math.round(d), false, "skn" + k(rect, c, Math.round(d)));
    }

    // ------------------------------------------------------------------ TextField

    public static Art textField(UINode n, UITheme th, float w, float h, boolean focused) {
        UIStyles.Spec sp = UIStyles.spec(n, th);
        UIShapes.Box b = new UIShapes.Box();
        b.radius = clampRadius(n.has("radius") ? n.num("radius", 8) : sp.f("radius", 10), w, h);
        int bg = sp.color("bgColor", "surface");
        b.fillTop = b.fillBottom = bg;
        b.gloss = sp.f("gloss", 0);
        b.borderWidth = sp.f("border", 2);
        b.borderColor = focused ? sp.color("focusColor", "primary") : sp.color("borderColor", "border");
        b.glow = sp.f("glow", 0) * (focused ? 1f : 0.35f);
        b.glowColor = focused ? sp.color("focusColor", "primary") : b.borderColor;
        Art a = finish(b, Math.round(w), Math.round(h), true, "txf" + k(b.radius, bg, b.gloss, b.borderWidth, b.borderColor, b.glow, focused, sp.f("underline", 0)));
        if (sp.f("underline", 0) > 0) {
            a.underline = true;
            a.underlineWidth = Math.max(2, sp.f("border", 2));
            a.underlineColor = b.borderColor;
            a.key += "u";
        }
        return a;
    }
}
