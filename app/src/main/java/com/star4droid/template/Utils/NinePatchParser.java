package com.star4droid.template.Utils;

/**
 * Pure (no libgdx, no android) reader of the Android 9-patch border markers, so it can be unit-checked with plain javac.
 *
 * Format of "name.9.png": the image has a 1px border added around the real picture.
 *  - top row    : black pixels mark the STRETCHABLE columns of the picture (x)
 *  - left column: black pixels mark the STRETCHABLE rows of the picture (y)
 *  - bottom row : black pixels mark the horizontal CONTENT (padding) area
 *  - right column: black pixels mark the vertical CONTENT (padding) area
 * The four border corners are ignored. Several stretch spans are reduced to their outer bounds.
 * An axis without any stretch marker is stretched as a whole (a UI button is never meant to stay at its pixel size).
 */
public final class NinePatchParser {
    private NinePatchParser() {}

    /** Pixel source: RGBA8888 (r in the high byte, alpha in the low byte), exactly what libgdx Pixmap.getPixel returns. */
    public interface Pixels {
        int rgba(int x, int y);
    }

    public static final class Parsed {
        /** false when the image is too small to carry markers (then use it as a plain image). */
        public boolean valid;
        /** Size of the picture without the 1px border. */
        public int contentW, contentH;
        /** Fixed (non-stretching) sizes in pixels at each side of the picture: left/right/top/bottom. */
        public int left, right, top, bottom;
        /** true when the corresponding axis carried at least one stretch marker. */
        public boolean markedX, markedY;
        /** Content (padding) insets in pixels, 0 when the bottom row / right column has no markers. */
        public int padLeft, padRight, padTop, padBottom;

        @Override
        public String toString() {
            return "valid=" + valid + " content=" + contentW + "x" + contentH + " splits(l,r,t,b)=" + left + "," + right + "," + top + "," + bottom
                    + " pad(l,r,t,b)=" + padLeft + "," + padRight + "," + padTop + "," + padBottom + " markedX=" + markedX + " markedY=" + markedY;
        }
    }

    /** Opaque (alpha >= 128) and dark (every channel < 128). Tolerates anti-aliased / near-black exports. */
    public static boolean isMarker(int rgba) {
        int a = rgba & 0xFF;
        if (a < 128) return false;
        int r = (rgba >>> 24) & 0xFF, g = (rgba >>> 16) & 0xFF, b = (rgba >>> 8) & 0xFF;
        return r < 128 && g < 128 && b < 128;
    }

    public static Parsed parse(int w, int h, Pixels px) {
        Parsed p = new Parsed();
        if (w < 3 || h < 3) return p;
        p.valid = true;
        int cw = w - 2, ch = h - 2;
        p.contentW = cw;
        p.contentH = ch;

        // stretch along x: top row (y = 0), columns 1..w-2 -> picture column = x - 1
        int sx1 = -1, sx2 = -1;
        for (int x = 1; x <= w - 2; x++) {
            if (isMarker(px.rgba(x, 0))) {
                if (sx1 < 0) sx1 = x - 1;
                sx2 = x - 1;
            }
        }
        // stretch along y: left column (x = 0), rows 1..h-2
        int sy1 = -1, sy2 = -1;
        for (int y = 1; y <= h - 2; y++) {
            if (isMarker(px.rgba(0, y))) {
                if (sy1 < 0) sy1 = y - 1;
                sy2 = y - 1;
            }
        }
        p.markedX = sx1 >= 0;
        p.markedY = sy1 >= 0;
        p.left = p.markedX ? sx1 : 0;
        p.right = p.markedX ? cw - 1 - sx2 : 0;
        p.top = p.markedY ? sy1 : 0;
        p.bottom = p.markedY ? ch - 1 - sy2 : 0;

        // padding: bottom row (y = h-1) and right column (x = w-1)
        int px1 = -1, px2 = -1;
        for (int x = 1; x <= w - 2; x++) {
            if (isMarker(px.rgba(x, h - 1))) {
                if (px1 < 0) px1 = x - 1;
                px2 = x - 1;
            }
        }
        int py1 = -1, py2 = -1;
        for (int y = 1; y <= h - 2; y++) {
            if (isMarker(px.rgba(w - 1, y))) {
                if (py1 < 0) py1 = y - 1;
                py2 = y - 1;
            }
        }
        if (px1 >= 0) {
            p.padLeft = px1;
            p.padRight = cw - 1 - px2;
        }
        if (py1 >= 0) {
            p.padTop = py1;
            p.padBottom = ch - 1 - py2;
        }
        return p;
    }
}
