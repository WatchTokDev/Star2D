package com.star4droid.star2d.buttons;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;

/**
 * Android-style 9-patch ("name.9.png"): the picture plus a 1 px border.
 *  top row / left column    : black = the part that STRETCHES (x resp. y)
 *  bottom row / right column: black = the content (padding) box where a label fits
 * Everything else in the border is fully transparent, including the four corners.
 * Index rule: picture pixel (x, y) lives at (x + 1, y + 1) of the file.
 */
public final class NinePatchWriter {
    private NinePatchWriter() {}

    public static final int BLACK = 0xFF000000;

    /** The 9-patch bitmap, (W + 2) x (H + 2). */
    public static Bitmap toNinePatch(ButtonRenderer.Result r) {
        Bitmap src = r.bitmap;
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w + 2, h + 2, Bitmap.Config.ARGB_8888);
        out.eraseColor(Color.TRANSPARENT);
        new android.graphics.Canvas(out).drawBitmap(src, 1, 1, null);
        for (int x = r.sx0; x < r.sx1; x++) out.setPixel(x + 1, 0, BLACK);
        for (int y = r.sy0; y < r.sy1; y++) out.setPixel(0, y + 1, BLACK);
        for (int x = r.cx0; x < r.cx1; x++) out.setPixel(x + 1, h + 1, BLACK);
        for (int y = r.cy0; y < r.cy1; y++) out.setPixel(w + 1, y + 1, BLACK);
        return out;
    }

    /** PNG bytes of the saved file (9-patch when stretchable, else the plain picture), with the spec stored in a tEXt chunk. */
    public static byte[] encode(ButtonRenderer.Result r, ButtonSpec spec) {
        Bitmap b = r.stretchable ? toNinePatch(r) : r.bitmap;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bo);
        if (b != r.bitmap) b.recycle();
        return PngMeta.withText(bo.toByteArray(), ButtonStore.META_KEY, spec.toJson().toString());
    }
}
