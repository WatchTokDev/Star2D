package com.star4droid.star2d.buttons;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Draws a {@link ButtonSpec} into a bitmap with plain Canvas calls (works on any thread, no GL).
 *
 * Layout of the bitmap, outside in: transparent margin sized for shadow/glow, the body (rings, fill, effects) and below it the 3D lip.
 * Besides the picture it computes the 9-patch data: which columns/rows may stretch (everything between the corner caps)
 * and the content (padding) box where text fits.
 */
public final class ButtonRenderer {
    private ButtonRenderer() {}

    public static final class Result {
        public Bitmap bitmap;
        public boolean stretchable;
        /** Stretchable columns [sx0, sx1) and rows [sy0, sy1) of the bitmap (pixel indices, bitmap coordinates). */
        public int sx0, sx1, sy0, sy1;
        /** Content box [cx0, cx1) x [cy0, cy1): where a label fits inside the button. */
        public int cx0, cx1, cy0, cy1;
        /** Smallest size the 9-patch should be shown at (the caps would overlap below this). */
        public int minW, minH;
    }

    private static final class Geo {
        RectF body;
        float radius, chamfer;
        int padL, padR, padT, padB;
    }

    public static Result render(ButtonSpec s) {
        s.sanitize(new java.util.ArrayList<>());
        final int W = s.width, H = s.height;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Geo g = geometry(s);

        Path silhouette = shape(s, g, 0, 0);
        if (s.lip > 0.5f) silhouette.addPath(shape(s, g, 0, s.lip));

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);

        // glow, then drop shadow (both below everything)
        if (ButtonColors.used(s.glowColor) && s.glowBlur > 0.1f) {
            p.setColor(s.glowColor);
            p.setMaskFilter(new BlurMaskFilter(Math.max(0.5f, s.glowBlur), BlurMaskFilter.Blur.NORMAL));
            cv.drawPath(silhouette, p);
            p.setMaskFilter(null);
        }
        if (ButtonColors.used(s.shadowColor)) {
            p.setColor(s.shadowColor);
            if (s.shadowBlur > 0.1f) p.setMaskFilter(new BlurMaskFilter(s.shadowBlur, BlurMaskFilter.Blur.NORMAL));
            cv.save();
            cv.translate(s.shadowDx, s.shadowDy);
            cv.drawPath(silhouette, p);
            cv.restore();
            p.setMaskFilter(null);
        }

        // 3D lip: the whole silhouette in the darker colour, the body goes on top
        if (s.lip > 0.5f) {
            int lc = ButtonColors.used(s.lipColor) ? s.lipColor : ButtonColors.darken(s.fill[s.fill.length - 1], 0.22f);
            p.setColor(lc);
            cv.drawPath(silhouette, p);
        }

        // rings (annuli, so a translucent fill never shows the ring colour through)
        float cum = 0;
        int[] rc = {s.outlineColor, s.frameColor, s.borderColor};
        float[] rw = {s.outlineWidth, s.frameWidth, s.borderWidth};
        for (int i = 0; i < 3; i++) {
            if (!ButtonColors.used(rc[i]) || rw[i] < 0.2f) continue;
            Path ring = new Path();
            ring.setFillType(Path.FillType.EVEN_ODD);
            ring.addPath(shape(s, g, cum, 0));
            ring.addPath(shape(s, g, cum + rw[i], 0));
            p.setColor(rc[i]);
            cv.drawPath(ring, p);
            cum += rw[i];
        }

        // fill
        Path fp = shape(s, g, cum, 0);
        RectF fb = new RectF();
        fp.computeBounds(fb, true);
        p.setShader(fillShader(s, fb));
        if (s.fill.length == 1) p.setColor(s.fill[0]);
        cv.drawPath(fp, p);
        p.setShader(null);

        // effects, clipped to the fill with an anti-aliased mask
        final float lcum = cum;
        if (fb.width() > 2 && fb.height() > 2) {
            RectF layer = new RectF(fb.left - 2, fb.top - 2, fb.right + 2, fb.bottom + 2);
            int sc = cv.saveLayer(layer, null);
            drawEffects(cv, s, g, fp, fb, lcum);
            Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
            m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            m.setColor(Color.BLACK);
            cv.drawPath(fp, m);
            cv.restoreToCount(sc);
        }

        Result r = new Result();
        r.bitmap = bmp;
        r.stretchable = s.isStretchable();

        // content box
        float k = contentInset(s, g, fb);
        r.cx0 = clampI(Math.round(fb.left + k), 0, W - 1);
        r.cx1 = clampI(Math.round(fb.right - k), r.cx0 + 1, W);
        r.cy0 = clampI(Math.round(fb.top + k), 0, H - 1);
        r.cy1 = clampI(Math.round(fb.bottom - k), r.cy0 + 1, H);

        if (r.stretchable) {
            float e = capExtent(s, g);
            int sx0 = (int) Math.ceil(g.body.left + e), sx1 = (int) Math.floor(g.body.right - e);
            int sy0 = (int) Math.ceil(g.body.top + e), sy1 = (int) Math.floor(g.body.bottom - e);
            if (sx1 - sx0 < 2) {
                int mid = Math.round((g.body.left + g.body.right) / 2f);
                sx0 = mid - 1;
                sx1 = mid + 1;
            }
            if (sy1 - sy0 < 2) {
                int mid = Math.round((g.body.top + g.body.bottom) / 2f);
                sy0 = mid - 1;
                sy1 = mid + 1;
            }
            r.sx0 = clampI(sx0, 0, W - 2);
            r.sx1 = clampI(sx1, r.sx0 + 1, W - 1);
            r.sy0 = clampI(sy0, 0, H - 2);
            r.sy1 = clampI(sy1, r.sy0 + 1, H - 1);
            r.minW = r.sx0 + (W - r.sx1);
            r.minH = r.sy0 + (H - r.sy1);
        } else {
            r.minW = W;
            r.minH = H;
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------ geometry

    private static Geo geometry(ButtonSpec s) {
        Geo g = new Geo();
        float sb = ButtonColors.used(s.shadowColor) ? s.shadowBlur : 0f;
        float gb = ButtonColors.used(s.glowColor) ? s.glowBlur : 0f;
        float k = 1.25f;
        g.padL = g.padR = Math.max(1, (int) Math.ceil(Math.max(sb * k + Math.abs(s.shadowDx), gb * k)));
        g.padT = Math.max(1, (int) Math.ceil(Math.max(sb * k - s.shadowDy, gb * k)));
        g.padB = Math.max(1, (int) Math.ceil(Math.max(sb * k + s.shadowDy, gb * k)));
        // never let the margins eat the button
        int maxX = Math.max(1, (int) (s.width * 0.3f)), maxY = Math.max(1, (int) (s.height * 0.3f));
        g.padL = Math.min(g.padL, maxX);
        g.padR = Math.min(g.padR, maxX);
        g.padT = Math.min(g.padT, maxY);
        g.padB = Math.min(g.padB, maxY);
        g.body = new RectF(g.padL, g.padT + s.drop, s.width - g.padR, s.height - g.padB - s.lip);
        float minSide = Math.max(1f, Math.min(g.body.width(), g.body.height()));
        switch (s.shape) {
            case "rect": g.radius = 0; break;
            case "pill": g.radius = minSide / 2f; break;
            default:
                g.radius = s.radius >= 0 ? s.radius : s.radiusRatio * minSide;
                if (s.shape.equals("circle")) g.radius = minSide / 2f;
        }
        g.radius = Math.max(0f, Math.min(g.radius, minSide / 2f));
        g.chamfer = s.chamfer >= 0 ? s.chamfer : s.chamferRatio * g.body.height();
        g.chamfer = Math.max(0f, Math.min(g.chamfer, minSide / 2f));
        return g;
    }

    /** Size of the corner a 9-patch must keep fixed. */
    private static float capExtent(ButtonSpec s, Geo g) {
        switch (s.shape) {
            case "rect": return 0f;
            case "chamfer": return g.chamfer;
            default: return g.radius;
        }
    }

    private static float contentInset(ButtonSpec s, Geo g, RectF fb) {
        switch (s.shape) {
            case "rect": return 0f;
            case "chamfer": return g.chamfer * 0.5f;
            case "rounded":
            case "pill": return g.radius * 0.29f;
            case "circle": return Math.min(fb.width(), fb.height()) * 0.146f;
            default: return Math.min(fb.width(), fb.height()) * 0.22f;
        }
    }

    /** The shape of the body shrunk by {@code inset} px on every side, optionally moved down by {@code dy}. Always clockwise. */
    static Path shape(ButtonSpec s, Geo g, float inset, float dy) {
        float l = g.body.left + inset, t = g.body.top + inset + dy, r = g.body.right - inset, b = g.body.bottom - inset + dy;
        float w = r - l, h = b - t;
        Path p = new Path();
        if (w <= 0.5f || h <= 0.5f) return p;
        switch (s.shape) {
            case "rect":
                p.addRect(l, t, r, b, Path.Direction.CW);
                break;
            case "rounded":
            case "pill": {
                float rad = Math.max(0f, Math.min(g.radius - inset, Math.min(w, h) / 2f));
                p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW);
                break;
            }
            case "chamfer": {
                float c = Math.max(0f, Math.min(g.chamfer - inset * 0.586f, Math.min(w, h) / 2f));
                p.moveTo(l + c, t);
                p.lineTo(r - c, t);
                p.lineTo(r, t + c);
                p.lineTo(r, b - c);
                p.lineTo(r - c, b);
                p.lineTo(l + c, b);
                p.lineTo(l, b - c);
                p.lineTo(l, t + c);
                p.close();
                break;
            }
            case "circle":
                p.addCircle((l + r) / 2f, (t + b) / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
                break;
            case "blob":
                polygon(p, 5, -90f, l, t, r, b, Math.max(0f, g.radius - inset * 0.5f));
                break;
            case "triangle_left":
                polygon(p, 3, 180f, l, t, r, b, Math.max(0f, g.radius - inset * 0.5f));
                break;
            case "triangle_right":
                polygon(p, 3, 0f, l, t, r, b, Math.max(0f, g.radius - inset * 0.5f));
                break;
            case "triangle_up":
                polygon(p, 3, -90f, l, t, r, b, Math.max(0f, g.radius - inset * 0.5f));
                break;
            default: // triangle_down
                polygon(p, 3, 90f, l, t, r, b, Math.max(0f, g.radius - inset * 0.5f));
                break;
        }
        return p;
    }

    /** Regular polygon, first vertex at {@code startDeg} (screen coordinates, clockwise), scaled to fit the rect and centred, corners rounded by {@code round}. */
    private static void polygon(Path out, int n, float startDeg, float l, float t, float r, float b, float round) {
        float[] x = new float[n], y = new float[n];
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(startDeg + 360.0 * i / n);
            x[i] = (float) Math.cos(a);
            y[i] = (float) Math.sin(a);
            minX = Math.min(minX, x[i]);
            maxX = Math.max(maxX, x[i]);
            minY = Math.min(minY, y[i]);
            maxY = Math.max(maxY, y[i]);
        }
        float side = Math.min(r - l, b - t);
        float sc = side / Math.max(maxX - minX, maxY - minY);
        float cx = (l + r) / 2f, cy = (t + b) / 2f, mx = (minX + maxX) / 2f, my = (minY + maxY) / 2f;
        for (int i = 0; i < n; i++) {
            x[i] = cx + (x[i] - mx) * sc;
            y[i] = cy + (y[i] - my) * sc;
        }
        for (int i = 0; i < n; i++) {
            int pi = (i + n - 1) % n, ni = (i + 1) % n;
            float px = x[pi] - x[i], py = y[pi] - y[i], nx = x[ni] - x[i], ny = y[ni] - y[i];
            float pl = (float) Math.hypot(px, py), nl = (float) Math.hypot(nx, ny);
            float d = Math.min(round, Math.min(pl, nl) * 0.45f);
            float ax = x[i] + px / pl * d, ay = y[i] + py / pl * d;
            float bx = x[i] + nx / nl * d, by = y[i] + ny / nl * d;
            if (i == 0) out.moveTo(ax, ay);
            else out.lineTo(ax, ay);
            out.quadTo(x[i], y[i], bx, by);
        }
        out.close();
    }

    // ------------------------------------------------------------------------------------------ paint

    private static Shader fillShader(ButtonSpec s, RectF fb) {
        if (s.fill.length == 1) return null;
        double a = Math.toRadians(s.angle);
        float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
        float half = (Math.abs(fb.width() * dx) + Math.abs(fb.height() * dy)) / 2f;
        float cx = fb.centerX(), cy = fb.centerY();
        float[] pos = s.fill.length == 2 ? new float[]{0f, 1f} : new float[]{0f, 0.5f, 1f};
        return new LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, s.fill, pos, Shader.TileMode.CLAMP);
    }

    private static void drawEffects(Canvas cv, ButtonSpec s, Geo g, Path fp, RectF fb, float ringsInset) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (ButtonColors.used(s.innerGlowColor) && s.innerGlowWidth > 0.2f) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s.innerGlowWidth * 1.6f);
            p.setColor(s.innerGlowColor);
            p.setMaskFilter(new BlurMaskFilter(Math.max(0.5f, s.innerGlowWidth * 0.5f), BlurMaskFilter.Blur.NORMAL));
            cv.drawPath(fp, p);
            p.setMaskFilter(null);
        }
        if (s.bevel > 0.1f) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s.bevel * 2f);
            p.setShader(new LinearGradient(0, fb.top, 0, fb.bottom,
                    new int[]{0x73FFFFFF, 0x00FFFFFF, 0x00000000, 0x4D000000}, new float[]{0f, 0.28f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            cv.drawPath(fp, p);
            p.setShader(null);
        }
        if (!s.gloss.equals("none") && s.glossStrength > 0.01f) gloss(cv, s, g, fb);
    }

    private static void gloss(Canvas cv, ButtonSpec s, Geo g, RectF fb) {
        float l = fb.left, t = fb.top, r = fb.right, b = fb.bottom, w = r - l, h = b - t;
        float y0 = t + s.glossY * h, gh = s.glossHeight * h;
        float inset = s.glossInset >= 0 ? s.glossInset : Math.min(w, h) * 0.08f;
        int gc = s.glossColor;
        float st = s.glossStrength;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        switch (s.gloss) {
            case "top_half": {
                Path q = new Path();
                q.moveTo(l, y0);
                q.lineTo(r, y0);
                q.lineTo(r, y0 + gh);
                q.quadTo((l + r) / 2f, y0 + gh + h * 0.10f, l, y0 + gh);
                q.close();
                p.setShader(new LinearGradient(0, y0, 0, y0 + gh, ButtonColors.alpha(gc, st), ButtonColors.alpha(gc, st * 0.45f), Shader.TileMode.CLAMP));
                cv.drawPath(q, p);
                break;
            }
            case "top_band": {
                RectF q = new RectF(l + inset, y0 + inset * 0.55f, r - inset, y0 + gh);
                if (q.height() < 2 || q.width() < 2) break;
                float rr = Math.min(Math.max(g.radius * 0.8f, 2f), q.height() / 2f);
                p.setShader(new LinearGradient(0, q.top, 0, q.bottom, ButtonColors.alpha(gc, st), ButtonColors.alpha(gc, st * 0.12f), Shader.TileMode.CLAMP));
                cv.drawRoundRect(q, rr, rr, p);
                break;
            }
            case "top_arc": {
                RectF q = new RectF(l - w * 0.12f, y0 - gh * 1.15f, r + w * 0.12f, y0 + gh);
                p.setShader(new LinearGradient(0, y0, 0, y0 + gh, ButtonColors.alpha(gc, st), ButtonColors.alpha(gc, 0f), Shader.TileMode.CLAMP));
                cv.drawOval(q, p);
                break;
            }
            case "top_left_ellipse": {
                float cx = l + 0.22f * w, cy = y0 + 0.26f * h;
                float rx = Math.min(0.16f * w, 0.9f * h), ry = Math.max(3f, 0.13f * h);
                p.setColor(ButtonColors.alpha(gc, st));
                cv.save();
                cv.rotate(-18f, cx, cy);
                cv.drawOval(new RectF(cx - rx, cy - ry, cx + rx, cy + ry), p);
                p.setColor(ButtonColors.alpha(gc, st * 0.7f));
                float dr = ry * 0.55f;
                cv.drawCircle(cx + rx + dr * 2.2f, cy, dr, p);
                cv.restore();
                break;
            }
            case "streak": {
                float skew = h * 0.5f, x1 = l + 0.20f * w, wd = Math.max(4f, 0.10f * w);
                p.setColor(ButtonColors.alpha(gc, st * 0.8f));
                Path q = new Path();
                q.moveTo(x1 + skew, t);
                q.lineTo(x1 + skew + wd, t);
                q.lineTo(x1 + wd, b);
                q.lineTo(x1, b);
                q.close();
                cv.drawPath(q, p);
                float x2 = x1 + wd * 1.8f, w2 = wd * 0.35f;
                Path q2 = new Path();
                q2.moveTo(x2 + skew, t);
                q2.lineTo(x2 + skew + w2, t);
                q2.lineTo(x2 + w2, b);
                q2.lineTo(x2, b);
                q2.close();
                cv.drawPath(q2, p);
                break;
            }
            case "bottom_glow": {
                float rad = Math.max(w, h) * 0.6f;
                p.setShader(new RadialGradient((l + r) / 2f, b, rad, ButtonColors.alpha(gc, st * 0.6f), ButtonColors.alpha(gc, 0f), Shader.TileMode.CLAMP));
                cv.drawRect(fb, p);
                break;
            }
            default:
                break;
        }
    }

    private static int clampI(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
