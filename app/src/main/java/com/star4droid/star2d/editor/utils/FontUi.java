package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.VisUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * Small LibGDX widget kit for the font maker (see {@link FontMakerForm}): a dark theme that matches the Android editor shell
 * (same palette as com.star4droid.star2d.ui.Ui), rounded panels, buttons, a touch friendly slider and switch.
 * Everything is drawn from ONE tintable rounded nine-patch per radius (created at runtime), so no image assets are needed.
 * GL thread only. Create a {@link Theme} when the UI is built and dispose it when the UI goes away.
 */
public final class FontUi {
    private FontUi() {}

    // ----------------------------------------------------------------- palette (same values as ui/Ui.java)
    public static final Color BG = Color.valueOf("17191DFF");
    public static final Color SURFACE = Color.valueOf("202328FF");
    public static final Color SURFACE_2 = Color.valueOf("292D33FF");
    public static final Color SURFACE_3 = Color.valueOf("333840FF");
    public static final Color BORDER = Color.valueOf("3A4048FF");
    public static final Color ACCENT = Color.valueOf("2196F3FF");
    public static final Color ACCENT_DARK = Color.valueOf("1976D2FF");
    public static final Color TEXT = Color.valueOf("F2F4F7FF");
    public static final Color TEXT_2 = Color.valueOf("B0B6BFFF");
    public static final Color TEXT_3 = Color.valueOf("7D8590FF");
    public static final Color DANGER = Color.valueOf("EF5350FF");
    private static final Color PRESS = new Color(1f, 1f, 1f, 0.14f);

    // ----------------------------------------------------------------- theme
    public static final class Theme implements Disposable {
        /** Pixels per layout unit ("dp"): about 0.7 x the screen density, see below. */
        public final float d;
        public final BitmapFont fontMain, fontSmall;
        private final Drawable white;
        private final HashMap<Integer, NinePatch> patches = new HashMap<>();
        private final ArrayList<Texture> textures = new ArrayList<>();

        public Theme() {
            float den = 1f;
            try {
                den = Gdx.graphics.getDensity();
            } catch (Throwable ignored) {
            }
            if (!(den >= 1f)) den = 1f;
            // the editor's LibGDX skin fonts are a fixed 30 px whatever the screen density, so controls are only
            // scaled to ~70% of the density: big enough for fingers, still in proportion with the text
            d = Math.max(1f, Math.min(den * 0.7f, 2.2f));
            Skin s = VisUI.getSkin();
            BitmapFont main = font(s, "default-font", null);
            if (main == null) main = s.get(Label.LabelStyle.class).font;
            fontMain = main;
            BitmapFont small = font(s, "small-font", main);
            fontSmall = small;
            white = s.getDrawable("white");
        }

        private static BitmapFont font(Skin s, String name, BitmapFont fallback) {
            try {
                if (s.has(name, BitmapFont.class)) return s.getFont(name);
            } catch (Throwable ignored) {
            }
            return fallback;
        }

        public float dp(float v) {
            return v * d;
        }

        private NinePatch patch(int r) {
            NinePatch p = patches.get(r);
            if (p != null) return p;
            int s = r * 2 + 2;
            Pixmap pm = new Pixmap(s, s, Pixmap.Format.RGBA8888);
            pm.setBlending(Pixmap.Blending.None);
            float half = s / 2f;
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    float px = x + 0.5f - half, py = y + 0.5f - half;
                    float qx = Math.abs(px) - (half - r), qy = Math.abs(py) - (half - r);
                    float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
                    float dist = (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
                    float a = Math.max(0f, Math.min(1f, 0.5f - dist));
                    pm.drawPixel(x, y, 0xFFFFFF00 | Math.round(a * 255f));
                }
            }
            Texture t = new Texture(pm);
            pm.dispose();
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            NinePatch np = new NinePatch(new TextureRegion(t), r, r, r, r);
            np.setPadding(0, 0, 0, 0);
            textures.add(t);
            patches.put(r, np);
            return np;
        }

        /** Fills a (rounded) rectangle with a colour; radius is in pixels and is limited to half the shorter side. */
        public void fill(Batch b, float x, float y, float w, float h, Color c, float alpha, float radiusPx) {
            float a = c.a * alpha;
            if (a <= 0f || w <= 0f || h <= 0f) return;
            b.setColor(c.r, c.g, c.b, a);
            int r = (int) Math.min(Math.round(radiusPx), Math.floor(Math.min(w, h) / 2f));
            if (r <= 0) white.draw(b, x, y, w, h);
            else patch(r).draw(b, x, y, w, h);
            b.setColor(Color.WHITE);
        }

        /** A drawable of the nine-patch tinted with a colour (scroll bar knobs). */
        public Drawable tinted(float radiusDp, Color c) {
            return new NinePatchDrawable(patch(Math.max(1, Math.round(dp(radiusDp))))).tint(c);
        }

        public Label.LabelStyle style(boolean small, Color color) {
            return new Label.LabelStyle(small ? fontSmall : fontMain, color);
        }

        public Label label(String text, boolean small, Color color) {
            Label l = new Label(text == null ? "" : text, style(small, color));
            l.setEllipsis(true);
            return l;
        }

        @Override
        public void dispose() {
            for (Texture t : textures) {
                try {
                    t.dispose();
                } catch (Throwable ignored) {
                }
            }
            textures.clear();
            patches.clear();
        }
    }

    // ----------------------------------------------------------------- panel / button
    /** A table with a rounded, optionally outlined, background drawn with the theme (no skin drawables, no automatic padding). */
    public static class Panel extends Table {
        protected final Theme th;
        protected Color fill, border;
        protected float radius, borderPx;

        public Panel(Theme th, Color fill, float radiusDp) {
            this.th = th;
            this.fill = fill;
            this.radius = th.dp(radiusDp);
        }

        public Panel outline(Color c, float px) {
            border = c;
            borderPx = px;
            return this;
        }

        public Panel fillColor(Color c) {
            fill = c;
            return this;
        }

        public Color fillColor() {
            return fill;
        }

        @Override
        protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
            float a = parentAlpha * getColor().a;
            float w = getWidth(), h = getHeight();
            if (border != null && borderPx > 0f) {
                th.fill(batch, x, y, w, h, border, a, radius);
                th.fill(batch, x + borderPx, y + borderPx, w - 2 * borderPx, h - 2 * borderPx, fill, a, Math.max(0f, radius - borderPx));
            } else if (fill != null) {
                th.fill(batch, x, y, w, h, fill, a, radius);
            }
        }
    }

    /** A tappable panel with a pressed highlight. */
    public static class Btn extends Panel {
        private final ClickListener cl;
        private Runnable action;
        private boolean enabled = true;

        public Btn(Theme th, Color fill, float radiusDp, Runnable action) {
            super(th, fill, radiusDp);
            this.action = action;
            setTouchable(Touchable.enabled);
            cl = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    Runnable r = Btn.this.action;
                    if (enabled && r != null) r.run();
                }
            };
            addListener(cl);
        }

        public Btn onClick(Runnable r) {
            action = r;
            return this;
        }

        public Btn enabled(boolean e) {
            enabled = e;
            getColor().a = e ? 1f : 0.5f;
            return this;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            if (enabled && cl.isVisualPressed())
                th.fill(batch, getX(), getY(), getWidth(), getHeight(), PRESS, parentAlpha * getColor().a, radius);
        }
    }

    // ----------------------------------------------------------------- slider
    /** Horizontal slider with a big knob. Reports only changes made by the user; {@link #setValue} is silent. */
    public static class DSlider extends Widget {
        private final Theme th;
        private final float min, max, step;
        private float value;
        private final Consumer<Float> onChange;
        private final Runnable onGrab, onRelease;
        private boolean dragging;

        public DSlider(Theme th, float min, float max, float step, Consumer<Float> onChange, Runnable onGrab, Runnable onRelease) {
            this.th = th;
            this.min = min;
            this.max = Math.max(max, min + 0.0001f);
            this.step = step > 0 ? step : 0;
            this.onChange = onChange;
            this.onGrab = onGrab;
            this.onRelease = onRelease;
            this.value = min;
            setTouchable(Touchable.enabled);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    if (pointer != 0 || dragging) return false;
                    dragging = true;
                    if (DSlider.this.onGrab != null) DSlider.this.onGrab.run();
                    moveTo(x);
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    if (dragging) moveTo(x);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    if (!dragging) return;
                    dragging = false;
                    if (DSlider.this.onRelease != null) DSlider.this.onRelease.run();
                }
            });
        }

        private float knobR() {
            return th.dp(11);
        }

        private void moveTo(float x) {
            float pad = knobR();
            float t = (x - pad) / Math.max(1f, getWidth() - 2 * pad);
            t = Math.max(0f, Math.min(1f, t));
            float v = min + t * (max - min);
            if (step > 0) v = Math.round((v - min) / step) * step + min;
            v = Math.max(min, Math.min(max, Math.round(v * 1000f) / 1000f));
            if (v != value) {
                value = v;
                if (onChange != null) onChange.accept(v);
            }
        }

        public void setValue(float v) {
            value = Math.max(min, Math.min(max, v));
        }

        @Override
        public float getPrefWidth() {
            return th.dp(120);
        }

        @Override
        public float getPrefHeight() {
            return th.dp(34);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float a = parentAlpha * getColor().a;
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            float kr = knobR(), trackH = th.dp(5);
            float t = (value - min) / (max - min);
            float cx = x + kr + t * (w - 2 * kr), cy = y + h / 2f;
            th.fill(batch, x + kr, cy - trackH / 2f, w - 2 * kr, trackH, SURFACE_3, a, trackH / 2f);
            th.fill(batch, x + kr, cy - trackH / 2f, Math.max(0f, cx - x - kr), trackH, ACCENT, a, trackH / 2f);
            if (dragging) th.fill(batch, cx - kr * 1.55f, cy - kr * 1.55f, kr * 3.1f, kr * 3.1f, new Color(ACCENT.r, ACCENT.g, ACCENT.b, 0.25f), a, kr * 1.55f);
            th.fill(batch, cx - kr, cy - kr, kr * 2, kr * 2, TEXT, a, kr);
            th.fill(batch, cx - kr + th.dp(3), cy - kr + th.dp(3), kr * 2 - th.dp(6), kr * 2 - th.dp(6), ACCENT, a, kr);
        }
    }

    // ----------------------------------------------------------------- switch
    public static class Switch extends Widget {
        private final Theme th;
        private boolean on;
        private final Consumer<Boolean> onChange;

        public Switch(Theme th, boolean on, Consumer<Boolean> onChange) {
            this.th = th;
            this.on = on;
            this.onChange = onChange;
            setTouchable(Touchable.enabled);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    toggle();
                }
            });
        }

        public void toggle() {
            on = !on;
            if (onChange != null) onChange.accept(on);
        }

        public void setOn(boolean v) {
            on = v;
        }

        @Override
        public float getPrefWidth() {
            return th.dp(46);
        }

        @Override
        public float getPrefHeight() {
            return th.dp(26);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float a = parentAlpha * getColor().a;
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            th.fill(batch, x, y, w, h, on ? ACCENT : SURFACE_3, a, h / 2f);
            float k = h - th.dp(6);
            float kx = on ? x + w - k - th.dp(3) : x + th.dp(3);
            th.fill(batch, kx, y + th.dp(3), k, k, on ? TEXT : TEXT_2, a, k / 2f);
        }
    }

    // ----------------------------------------------------------------- colour swatch
    /** The colour on a mid-grey plate (so a translucent colour is visible as such). */
    public static class Swatch extends Widget {
        private final Theme th;
        private final Color grey = Color.valueOf("8A9099FF");
        private Color color = Color.WHITE;

        public Swatch(Theme th) {
            this.th = th;
        }

        public void setSwatch(Color c) {
            color = c;
        }

        @Override
        public float getPrefWidth() {
            return th.dp(40);
        }

        @Override
        public float getPrefHeight() {
            return th.dp(22);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float a = parentAlpha * getColor().a;
            float x = getX(), y = getY(), w = getWidth(), h = getHeight(), r = th.dp(5);
            th.fill(batch, x, y, w, h, TEXT_2, a, r);
            th.fill(batch, x + 1, y + 1, w - 2, h - 2, grey, a, r);
            th.fill(batch, x + 1, y + 1, w - 2, h - 2, color, a, r);
        }
    }

    // ----------------------------------------------------------------- chevron
    /** A small triangle (up or down), built from pixel rows so it needs no glyph or image. */
    public static class Chevron extends Widget {
        private final Theme th;
        private boolean up;
        private final Color color;

        public Chevron(Theme th, boolean up, Color color) {
            this.th = th;
            this.up = up;
            this.color = color;
        }

        public void setUp(boolean v) {
            up = v;
        }

        @Override
        public float getPrefWidth() {
            return th.dp(14);
        }

        @Override
        public float getPrefHeight() {
            return th.dp(14);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float a = parentAlpha * getColor().a;
            float w = Math.min(getWidth(), th.dp(12)), h = th.dp(7);
            int rows = Math.max(3, Math.round(h));
            float rowH = h / rows;
            float cx = getX() + getWidth() / 2f, y0 = getY() + (getHeight() - h) / 2f;
            for (int i = 0; i < rows; i++) {
                float f = 1f - (float) i / rows; // 1 at the wide side
                float rw = Math.max(1f, w * f);
                float ry = up ? y0 + i * rowH : y0 + h - (i + 1) * rowH;
                th.fill(batch, cx - rw / 2f, ry, rw, rowH + 0.5f, color, a, 0);
            }
        }
    }
}
