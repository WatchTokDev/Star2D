package com.star4droid.template.ui;

import com.star4droid.star2d.ElementDefs.UIHolderDef;

/**
 * Where a UI holder goes when its layout is "screen": relative to the screen box (the logic frame of the scene) instead of fixed
 * x / y / width / height. Used by the game and by the editor so both show the same thing. Pure Java.
 */
public final class UIHolderLayout {
    private UIHolderLayout() {}

    public static boolean isScreen(String layout) {
        return layout != null && layout.equals("screen");
    }

    /**
     * @return {x, y, width, height} in screen px (origin bottom left, y up) of a holder in the screen box sw x sh.
     * Width: stretch X = whole width minus the margins; else the percent of the screen when &gt; 0; else the fixed width.
     */
    public static float[] compute(String anchor, boolean stretchX, boolean stretchY, float wPct, float hPct,
                                  float mx, float my, float fixedW, float fixedH, float sw, float sh) {
        float w = stretchX ? sw - 2 * mx : (wPct > 0 ? sw * wPct / 100f : fixedW);
        float h = stretchY ? sh - 2 * my : (hPct > 0 ? sh * hPct / 100f : fixedH);
        if (w < 1) w = 1;
        if (h < 1) h = 1;
        String a = anchor == null ? "topleft" : anchor;
        boolean left = a.contains("left"), right = a.contains("right");
        boolean top = a.contains("top"), bottom = a.contains("bottom");
        float x, y;
        if (stretchX || left) x = mx;
        else if (right) x = sw - w - mx;
        else x = (sw - w) / 2f + mx;
        if (stretchY || bottom) y = my;
        else if (top) y = sh - h - my;
        else y = (sh - h) / 2f + my;
        return new float[]{x, y, w, h};
    }

    public static float[] compute(UIHolderDef d, float sw, float sh) {
        return compute(d.anchor, d.Stretch_X, d.Stretch_Y, d.Width_Percent, d.Height_Percent, d.Margin_X, d.Margin_Y,
                d.width, d.height, sw, sh);
    }
}
