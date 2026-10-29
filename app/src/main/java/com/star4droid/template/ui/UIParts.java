package com.star4droid.template.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/** Small building blocks (picture and text) that honour the tint of their {@link UIActor} ancestors. */
final class UIParts {
    private UIParts() {}

    private static final Color TMP = new Color();

    static class Pic extends Image {
        Pic(Drawable d) {
            super(d);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color c = getColor();
            float r = c.r, g = c.g, b = c.b, a = c.a;
            if (UIActor.tintOf(this, TMP)) c.mul(TMP);
            super.draw(batch, parentAlpha);
            c.set(r, g, b, a);
        }
    }

    static class Text extends Label {
        Text(CharSequence text, LabelStyle style) {
            super(text, style);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Color c = getColor();
            float r = c.r, g = c.g, b = c.b, a = c.a;
            if (UIActor.tintOf(this, TMP)) c.mul(TMP);
            super.draw(batch, parentAlpha);
            c.set(r, g, b, a);
        }
    }

    static Color color(int rgba) {
        Color c = new Color();
        Color.rgba8888ToColor(c, rgba);
        return c;
    }
}
