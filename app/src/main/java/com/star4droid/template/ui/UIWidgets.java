package com.star4droid.template.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

/** The widget actors of the .ui runtime. Each one draws itself from {@link UIArt} pictures or from project images. */
final class UIWidgets {
    private UIWidgets() {}

    // ------------------------------------------------------------------ helpers

    static BitmapFont fontOf(UIView v, UINode n) {
        return UIAssets.font(v.projectPath(), n.has("font") ? n.str("font", "") : v.theme(n).font);
    }

    static float sizeOf(UIView v, UINode n) {
        return n.num("fontSize", v.theme(n).number("fontSize"));
    }

    static int textColor(UIView v, UINode n) {
        return v.theme(n).resolveColor(n.str("color", ""), v.theme(n).color("text"));
    }

    static UIParts.Text label(UIView v, UINode n, String text, int color) {
        BitmapFont f = fontOf(v, n);
        Label.LabelStyle st = new Label.LabelStyle(f, UIParts.color(color));
        UIParts.Text l = new UIParts.Text(text, st);
        l.setFontScale(UIAssets.fontScale(f, sizeOf(v, n)));
        l.setTouchable(Touchable.disabled);
        return l;
    }

    /** Picture of generated art, placed so its face covers the widget rectangle. */
    static UIParts.Pic pic(UIArt.Art a) {
        UIParts.Pic p = new UIParts.Pic(UIAssets.art(a));
        p.setBounds(-a.margin, -a.margin, a.drawW, a.drawH);
        p.setTouchable(Touchable.disabled);
        return p;
    }

    static void setArt(UIParts.Pic p, UIArt.Art a) {
        p.setDrawable(UIAssets.art(a));
        p.setBounds(-a.margin, -a.margin, a.drawW, a.drawH);
    }

    static Drawable project(UIView v, UINode n, String key) {
        return n.has(key) ? UIAssets.image(v.projectPath(), n.str(key, "")) : null;
    }

    static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    // ------------------------------------------------------------------ containers (Panel, Stack, Linear, Grid)

    static class Box extends UIActor {
        UIParts.Pic bg;

        Box(UIView v, UINode n) {
            super(v, n);
        }

        @Override
        protected void onLayout() {
            if (bg != null) bg.remove();
            bg = null;
            UITheme th = view.theme(node);
            Drawable img = project(view, node, "image");
            if (img != null) {
                bg = new UIParts.Pic(img);
                bg.setBounds(0, 0, node.rw, node.rh);
                bg.setScaling(Scaling.stretch);
                bg.setTouchable(Touchable.disabled);
            } else if (node.type.equals(UINode.PANEL)) {
                bg = pic(UIArt.panel(node, th, node.rw, node.rh));
            } else if (node.has("bgColor")) {
                bg = pic(UIArt.plain(th.resolveColor(node.str("bgColor", ""), 0), 0, node.rw, node.rh));
            }
            if (bg != null) addActorAt(0, bg);
            setClip(node.bool("clip", false));
            if (node.type.equals(UINode.PANEL) && node.has("padding")) { /* padding only affects layout */ }
        }
    }

    // ------------------------------------------------------------------ Label

    static class LabelW extends UIActor {
        UIParts.Text text;

        LabelW(UIView v, UINode n) {
            super(v, n);
            text = label(v, n, n.str("text", ""), textColor(v, n));
            addActor(text);
        }

        @Override
        protected void onLayout() {
            text.setBounds(0, 0, node.rw, node.rh);
            text.setWrap(node.bool("wrap", false));
            String al = node.str("textAlign", "left");
            text.setAlignment(al.equals("center") ? Align.center : al.equals("right") ? Align.right : Align.left, al.equals("center") ? Align.center : al.equals("right") ? Align.right : Align.left);
            BitmapFont f = fontOf(view, node);
            text.setFontScale(UIAssets.fontScale(f, sizeOf(view, node)));
            text.getStyle().fontColor = UIParts.color(textColor(view, node));
            text.setText(node.str("text", ""));
        }

        @Override
        public void refresh() {
            text.setText(node.str("text", ""));
        }
    }

    // ------------------------------------------------------------------ Image / Icon

    static class ImageW extends UIActor {
        UIParts.Pic pic;

        ImageW(UIView v, UINode n) {
            super(v, n);
        }

        @Override
        protected void onLayout() {
            if (pic != null) pic.remove();
            Drawable d = project(view, node, "image");
            if (d == null && view.placeholders()) d = UIAssets.art(UIArt.plain(0xffffff30, 6, node.rw, node.rh));
            if (d == null) {
                pic = null;
                return;
            }
            pic = new UIParts.Pic(d);
            String sc = node.str("scaling", "fit");
            pic.setScaling(sc.equals("fill") ? Scaling.fill : sc.equals("stretch") ? Scaling.stretch : sc.equals("none") ? Scaling.none : Scaling.fit);
            pic.setAlign(Align.center);
            pic.setBounds(0, 0, node.rw, node.rh);
            pic.setOrigin(node.rw / 2f, node.rh / 2f);
            pic.setScale(node.bool("flipX", false) ? -1 : 1, node.bool("flipY", false) ? -1 : 1);
            pic.setTouchable(Touchable.disabled);
            if (node.type.equals(UINode.ICON)) pic.setColor(UIParts.color(view.theme(node).resolveColor(node.str("color", "text"), view.theme(node).color("text"))));
            addActor(pic);
            setClip(sc.equals("fill"));
        }

        @Override
        public void refresh() {
            onLayout();
        }
    }

    // ------------------------------------------------------------------ Button

    static class ButtonW extends UIActor {
        UIParts.Pic bg, icon;
        UIParts.Text text;
        boolean pressed;
        ClickListener click;

        ButtonW(UIView v, UINode n) {
            super(v, n);
            text = label(v, n, n.str("text", ""), UIArt.buttonTextColor(n, v.theme(n)));
            addActor(text);
            click = new ClickListener() {
                @Override
                public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                    if (isOff()) return false;
                    boolean r = super.touchDown(e, x, y, pointer, button);
                    if (r) {
                        setPressed(true);
                        view.fireTouchStart(ButtonW.this);
                    }
                    return r;
                }

                @Override
                public void touchUp(InputEvent e, float x, float y, int pointer, int button) {
                    boolean was = isPressed();
                    super.touchUp(e, x, y, pointer, button);
                    if (was) {
                        setPressed(false);
                        view.fireTouchEnd(ButtonW.this);
                    }
                }

                @Override
                public void clicked(InputEvent e, float x, float y) {
                    view.fireClick(ButtonW.this);
                }
            };
            addListener(click);
        }

        @Override
        protected boolean wantsTransform() {
            return node.bool("pressScale", true);
        }

        void setPressed(boolean p) {
            pressed = p;
            updateVisual();
            if (node.bool("pressScale", true)) {
                float base = node.num("scale", 1f), amt = node.num("pressScaleAmount", 0.94f);
                clearActions();
                float s = p ? base * amt : base;
                addAction(Actions.scaleTo(s, s, 0.06f));
            }
        }

        void updateVisual() {
            if (bg == null) return;
            UITheme th = view.theme(node);
            Drawable img = project(view, node, "image");
            if (img != null) {
                Drawable pr = pressed ? project(view, node, "pressedImage") : null;
                if (pressed && pr == null) {
                    String twin = UIAssets.pressedTwin(view.projectPath(), node.str("image", ""));
                    if (twin != null) pr = UIAssets.image(view.projectPath(), twin);
                }
                bg.setDrawable(pr != null ? pr : img);
                bg.setBounds(0, 0, node.rw, node.rh);
                bg.setColor(pressed && pr == null ? new Color(0.8f, 0.8f, 0.8f, 1f) : Color.WHITE);
                text.setY(pressed && pr == null ? -1 : 0);
            } else {
                setArt(bg, UIArt.button(node, th, node.rw, node.rh, pressed));
                text.setY(pressed ? -1 : 0);
            }
        }

        @Override
        protected void onLayout() {
            if (bg == null) {
                bg = new UIParts.Pic(UIAssets.art(UIArt.button(node, view.theme(node), Math.max(4, node.rw), Math.max(4, node.rh), false)));
                bg.setTouchable(Touchable.disabled);
                addActorAt(0, bg);
            }
            updateVisual();
            UITheme th = view.theme(node);
            float w = node.rw, h = node.rh, fs = sizeOf(view, node);
            BitmapFont f = fontOf(view, node);
            text.setFontScale(UIAssets.fontScale(f, fs));
            text.getStyle().fontColor = UIParts.color(UIArt.buttonTextColor(node, th));
            text.setText(node.str("text", ""));
            text.setWrap(false);
            Drawable ic = project(view, node, "icon");
            if (icon != null) icon.remove();
            icon = null;
            String pos = node.str("iconPosition", "left");
            if (ic == null) {
                text.setBounds(0, 0, w, h);
                text.setAlignment(Align.center, Align.center);
            } else {
                float is = Math.min(h - 12, fs * 1.4f);
                if (pos.equals("top")) is = Math.min(is, h * 0.5f);
                icon = new UIParts.Pic(ic);
                icon.setScaling(Scaling.fit);
                icon.setTouchable(Touchable.disabled);
                icon.setColor(UIParts.color(UIArt.buttonTextColor(node, th)));
                float[] tm = node.str("text", "").isEmpty() ? new float[]{0, 0} : UIAssets.measure(f, node.str("text", ""), fs, 0);
                float gap = tm[0] > 0 ? 8 : 0;
                if (pos.equals("top")) {
                    float total = is + gap + tm[1];
                    float y0 = (h - total) / 2f;
                    icon.setBounds((w - is) / 2f, y0 + tm[1] + gap, is, is);
                    text.setBounds(0, y0, w, tm[1]);
                } else {
                    float total = is + gap + tm[0];
                    float x0 = (w - total) / 2f;
                    boolean right = pos.equals("right");
                    float ix = right ? x0 + tm[0] + gap : x0, tx = right ? x0 : x0 + is + gap;
                    icon.setBounds(ix, (h - is) / 2f, is, is);
                    text.setBounds(tx, 0, tm[0] + 4, h);
                }
                text.setAlignment(Align.center, Align.center);
                addActor(icon);
            }
            setOrigin(w / 2f, h / 2f);
        }

        @Override
        public void refresh() {
            text.setText(node.str("text", ""));
            onLayout();
        }
    }

    // ------------------------------------------------------------------ Progress / Slider

    /** Shared by Progress and Slider: track, clipped fill, optional knob. */
    static class BarW extends UIActor {
        UIParts.Pic track, fillPic, knob;
        ClipGroup fillClip;
        UIParts.Text valueText;
        float value, shown, min, max;
        final boolean slider;
        boolean dragging;
        float len, thick, kw, kh;
        boolean vertical;

        BarW(UIView v, UINode n) {
            super(v, n);
            slider = n.type.equals(UINode.SLIDER);
            min = n.num("min", 0);
            max = n.num("max", slider ? 1 : 100);
            value = clamp(n.num("value", slider ? 0.5f : 50f), Math.min(min, max), Math.max(min, max));
            shown = value;
            if (slider) installDrag();
        }

        void installDrag() {
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                    if (isOff() || pointer != 0) return false;
                    dragging = true;
                    view.fireTouchStart(BarW.this);
                    move(x, y, false);
                    return true;
                }

                @Override
                public void touchDragged(InputEvent e, float x, float y, int pointer) {
                    if (dragging) move(x, y, false);
                }

                @Override
                public void touchUp(InputEvent e, float x, float y, int pointer, int button) {
                    if (!dragging) return;
                    dragging = false;
                    move(x, y, true);
                    view.fireTouchEnd(BarW.this);
                }
            });
        }

        float step() {
            return node.num("step", 0);
        }

        void move(float x, float y, boolean release) {
            float f = vertical ? (y - kh / 2f) / Math.max(1f, len - kh) : (x - kw / 2f) / Math.max(1f, len - kw);
            f = clamp(f, 0, 1);
            float v = min + (max - min) * f;
            float st = step();
            if (st > 0) v = min + Math.round((v - min) / st) * st;
            v = clamp(v, Math.min(min, max), Math.max(min, max));
            boolean cont = !node.str("changeMode", "continuous").equals("release");
            boolean changed = v != value;
            value = v;
            if (!node.bool("snapToStep", true) && st > 0) shown = min + (max - min) * f; // knob follows the finger, value is stepped
            else shown = v;
            place();
            if (changed && cont) view.fireChange(this, value);
            else if (release && !cont) view.fireChange(this, value);
        }

        float fraction(float v) {
            return max == min ? 0 : clamp((v - min) / (max - min), 0, 1);
        }

        void place() {
            if (fillClip == null) return;
            float f = fraction(shown);
            float w = node.rw, h = node.rh;
            float tx = vertical ? (w - thick) / 2f : 0, ty = vertical ? 0 : (h - thick) / 2f;
            UIStyles.Spec sp = UIStyles.spec(node, view.theme(node));
            float bw = sp.f("trackBorder", 0);
            if (slider) {
                float c = vertical ? kh / 2f + (len - kh) * f : kw / 2f + (len - kw) * f; // distance of the knob center from the start
                knob.setPosition((vertical ? (w - kw) / 2f : c - kw / 2f) - knobMargin, (vertical ? c - kh / 2f : (h - kh) / 2f) - knobMargin);
                float fl = clamp(c, 0, len);
                clipFill(tx, ty, bw, fl);
                if (valueText != null) {
                    valueText.setText(fmt(value));
                    valueText.setPosition(knob.getX() + knobMargin + kw / 2f - valueText.getWidth() / 2f, knob.getY() + knobMargin + kh + 2);
                }
            } else {
                clipFill(tx, ty, bw, len * f);
                if (valueText != null) {
                    String t = node.str("textFormat", "percent");
                    valueText.setText(t.equals("value") ? String.valueOf(Math.round(shown)) : t.equals("fraction") ? Math.round(shown) + "/" + Math.round(max) : Math.round(f * 100) + "%");
                }
            }
        }

        String fmt(float v) {
            int d = node.integer("decimals", 0);
            return d <= 0 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%." + d + "f", v);
        }

        void clipFill(float tx, float ty, float bw, float fillLen) {
            // the fill art is drawn at full size inside the border; the clip group shows the filled part only
            float in = len - 2 * bw, ti = thick - 2 * bw;
            float cl = clamp(fillLen - bw, 0, in);
            if (vertical) {
                fillClip.setBounds(tx + bw, ty + bw, ti, cl);
                fillPic.setPosition(0, 0);
            } else {
                fillClip.setBounds(tx + bw, ty + bw, cl, ti);
                fillPic.setPosition(0, 0);
            }
            fillClip.setVisible(cl > 0.5f);
        }

        @Override
        protected void onLayout() {
            UITheme th = view.theme(node);
            vertical = node.bool("vertical", false);
            min = node.num("min", 0);
            max = node.num("max", slider ? 1 : 100);
            float w = node.rw, h = node.rh;
            len = vertical ? h : w;
            thick = UIArt.trackThickness(node, th, vertical ? w : h);
            thick = Math.min(thick, vertical ? w : h);
            if (track != null) track.remove();
            if (fillClip != null) fillClip.remove();
            if (knob != null) knob.remove();
            if (valueText != null) valueText.remove();
            float tx = vertical ? (w - thick) / 2f : 0, ty = vertical ? 0 : (h - thick) / 2f;
            Drawable ti = project(view, node, "trackImage");
            if (ti != null) {
                track = new UIParts.Pic(ti);
                track.setBounds(tx, ty, vertical ? thick : len, vertical ? len : thick);
                track.setTouchable(Touchable.disabled);
            } else {
                UIArt.Art ta = UIArt.track(node, th, len, thick, vertical);
                track = pic(ta);
                track.setPosition(tx - ta.margin, ty - ta.margin);
            }
            addActor(track);
            fillClip = new ClipGroup();
            fillClip.setClip(true);
            fillClip.setTouchable(Touchable.disabled);
            UIStyles.Spec sp = UIStyles.spec(node, th);
            float bw = sp.f("trackBorder", 0);
            float in = Math.max(2, len - 2 * bw), ti2 = Math.max(2, thick - 2 * bw);
            Drawable fi = project(view, node, "fillImage");
            if (fi != null) {
                fillPic = new UIParts.Pic(fi);
                fillPic.setSize(vertical ? ti2 : in, vertical ? in : ti2);
            } else {
                UIArt.Art fa = UIArt.fill(node, th, len, thick, vertical);
                fillPic = pic(fa);
                fillPic.setBounds(-fa.margin, -fa.margin, fa.drawW, fa.drawH);
            }
            fillPic.setTouchable(Touchable.disabled);
            fillClip.addActor(fillPic);
            addActor(fillClip);
            if (slider) {
                Drawable kd = project(view, node, "knobImage");
                UIArt.Art ka = UIArt.knob(node, th);
                if (kd != null) {
                    knob = new UIParts.Pic(kd);
                    float ks = sp.f("knobSize", 32f);
                    knob.setSize(ks, ks);
                    kw = kh = ks;
                } else {
                    knob = new UIParts.Pic(UIAssets.art(ka));
                    kw = ka.drawW - 2 * ka.margin;
                    kh = ka.drawH - 2 * ka.margin;
                    knob.setSize(ka.drawW, ka.drawH);
                }
                knobMargin = kd == null ? ka.margin : 0;
                knob.setTouchable(Touchable.disabled);
                addActor(knob);
            }
            boolean showTxt = slider ? node.bool("showValueLabel", false) : node.bool("showText", false);
            if (showTxt) {
                int col = textColor(view, node);
                valueText = label(view, node, "", col);
                valueText.setSize(slider ? 60 : w, slider ? sizeOf(view, node) * 1.2f : h);
                valueText.setAlignment(Align.center);
                if (!slider) valueText.setFontScale(UIAssets.fontScale(fontOf(view, node), Math.min(sizeOf(view, node), thick * 0.8f)));
                else valueText.setFontScale(UIAssets.fontScale(fontOf(view, node), sizeOf(view, node) * 0.7f));
                addActor(valueText);
            }
            place();
        }

        float knobMargin;

        @Override
        public void act(float delta) {
            super.act(delta);
            if (!slider && !dragging && shown != value) {
                if (node.bool("animate", true)) {
                    float d = value - shown;
                    shown += d * Math.min(1f, delta * 10f);
                    if (Math.abs(value - shown) < (max - min) * 0.002f) shown = value;
                } else shown = value;
                place();
            }
        }

        void setValue(float v, boolean fire) {
            v = clamp(v, Math.min(min, max), Math.max(min, max));
            float st = step();
            if (st > 0 && slider) v = min + Math.round((v - min) / st) * st;
            boolean changed = v != value;
            value = v;
            if (slider || !node.bool("animate", true)) shown = v;
            place();
            if (changed && fire) view.fireChange(this, v);
        }
    }

    // ------------------------------------------------------------------ Checkbox / Radio

    static class CheckW extends UIActor {
        UIParts.Pic box;
        UIParts.Text text;
        boolean checked;
        final boolean radio;

        CheckW(UIView v, UINode n) {
            super(v, n);
            radio = n.type.equals(UINode.RADIO);
            checked = n.bool("checked", false);
            text = label(v, n, n.str("text", ""), textColor(v, n));
            addActor(text);
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent e, float x, float y) {
                    if (isOff()) return;
                    if (radio) {
                        if (!checked) set(true, true);
                    } else set(!checked, true);
                    view.fireClick(CheckW.this);
                }
            });
        }

        float boxSize() {
            return node.num("boxSize", Math.max(32, sizeOf(view, node) * 1.2f));
        }

        void set(boolean c, boolean fire) {
            if (c == checked) return;
            checked = c;
            node.set("checked", c);
            if (radio && c) view.uncheckOthers(this);
            refresh();
            if (fire) view.fireChange(this, c);
        }

        @Override
        protected void onLayout() {
            float bs = boxSize();
            if (box != null) box.remove();
            UIArt.Art a = UIArt.check(node, view.theme(node), bs, checked);
            box = pic(a);
            box.setPosition(-a.margin, (node.rh - bs) / 2f - a.margin);
            addActorAt(0, box);
            BitmapFont f = fontOf(view, node);
            text.setFontScale(UIAssets.fontScale(f, sizeOf(view, node)));
            text.getStyle().fontColor = UIParts.color(textColor(view, node));
            text.setText(node.str("text", ""));
            text.setBounds(bs + 12, 0, Math.max(0, node.rw - bs - 12), node.rh);
            text.setAlignment(Align.left, Align.left);
        }

        @Override
        public void refresh() {
            checked = node.bool("checked", checked);
            onLayout();
        }
    }

    // ------------------------------------------------------------------ Switch

    static class SwitchW extends UIActor {
        UIParts.Pic track, knob;
        boolean on;
        float pos; // 0 off .. 1 on

        SwitchW(UIView v, UINode n) {
            super(v, n);
            on = n.bool("checked", false);
            pos = on ? 1 : 0;
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent e, float x, float y) {
                    if (isOff()) return;
                    set(!on, true);
                    view.fireClick(SwitchW.this);
                }
            });
        }

        void set(boolean c, boolean fire) {
            if (c == on) return;
            on = c;
            node.set("checked", c);
            UIArt.Art a = UIArt.switchTrack(node, view.theme(node), node.rw, node.rh, on);
            setArt(track, a);
            if (fire) view.fireChange(this, c);
        }

        @Override
        protected void onLayout() {
            if (track != null) track.remove();
            if (knob != null) knob.remove();
            track = pic(UIArt.switchTrack(node, view.theme(node), node.rw, node.rh, on));
            UIArt.Art ka = UIArt.switchKnob(node, view.theme(node), node.rh);
            knob = new UIParts.Pic(UIAssets.art(ka));
            knob.setSize(ka.drawW, ka.drawH);
            knob.setTouchable(Touchable.disabled);
            addActor(track);
            addActor(knob);
            placeKnob();
        }

        void placeKnob() {
            float d = knob.getWidth();
            float pad = (node.rh - d) / 2f;
            knob.setPosition(pad + (node.rw - d - 2 * pad) * pos, pad);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            float target = on ? 1 : 0;
            if (pos != target) {
                pos += (target - pos) * Math.min(1f, delta * 14f);
                if (Math.abs(target - pos) < 0.01f) pos = target;
                if (knob != null) placeKnob();
            }
        }

        @Override
        public void refresh() {
            on = node.bool("checked", on);
            pos = on ? 1 : 0;
            onLayout();
        }
    }

    // ------------------------------------------------------------------ TextField

    static class FieldW extends UIActor {
        UIParts.Pic bg;
        TextField field;
        BitmapFont ownFont;
        boolean focused;

        FieldW(UIView v, UINode n) {
            super(v, n);
        }

        @Override
        protected void onLayout() {
            UITheme th = view.theme(node);
            String keep = field != null ? field.getText() : node.str("text", "");
            if (field != null) field.remove();
            if (bg != null) bg.remove();
            bg = pic(UIArt.textField(node, th, node.rw, node.rh, focused));
            addActor(bg);
            float fs = sizeOf(view, node);
            BitmapFont f;
            if (node.has("font") || th.font != null && !th.font.isEmpty()) f = fontOf(view, node);
            else {
                if (ownFont == null) ownFont = new BitmapFont(com.badlogic.gdx.Gdx.files.internal("files/default.fnt"));
                f = ownFont;
                ownFont.getData().setScale(UIAssets.fontScale(ownFont, fs));
            }
            TextField.TextFieldStyle st = new TextField.TextFieldStyle();
            st.font = f;
            st.fontColor = UIParts.color(textColor(view, node));
            st.messageFont = f;
            st.messageFontColor = UIParts.color(th.color("textDim"));
            int focusCol = UIStyles.spec(node, th).color("focusColor", "primary");
            st.cursor = UIAssets.solid(focusCol);
            st.selection = UIAssets.solid(UITheme.withAlpha(focusCol, 0.35f));
            BaseDrawable pad = new BaseDrawable();
            pad.setLeftWidth(16);
            pad.setRightWidth(16);
            st.background = pad;
            field = new TextField(keep, st);
            field.setMessageText(node.str("hint", ""));
            field.setBounds(0, 0, node.rw, node.rh);
            field.setAlignment(Align.left);
            field.setPasswordMode(node.bool("password", false));
            field.setPasswordCharacter('*');
            int ml = node.integer("maxLength", 0);
            if (ml > 0) field.setMaxLength(ml);
            if (node.bool("numeric", false)) field.setTextFieldFilter(new TextField.TextFieldFilter() {
                @Override
                public boolean acceptChar(TextField tf, char c) {
                    return Character.isDigit(c) || c == '.' || c == '-';
                }
            });
            field.setDisabled(node.bool("readOnly", false));
            field.setTextFieldListener(new TextField.TextFieldListener() {
                @Override
                public void keyTyped(TextField tf, char c) {
                    if (c == '\r' || c == '\n') {
                        view.fireSubmit(FieldW.this, tf.getText());
                        if (tf.getStage() != null) tf.getStage().setKeyboardFocus(null);
                        return;
                    }
                    node.set("text", tf.getText());
                    view.fireChange(FieldW.this, tf.getText());
                }
            });
            field.addListener(new FocusListener() {
                @Override
                public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean f) {
                    focused = f;
                    setArt(bg, UIArt.textField(node, view.theme(node), node.rw, node.rh, f));
                }
            });
            addActor(field);
        }

        @Override
        public void refresh() {
            if (field != null) field.setText(node.str("text", ""));
        }

        void dispose() {
            if (ownFont != null) {
                try { ownFont.dispose(); } catch (Throwable ignored) {}
                ownFont = null;
            }
        }
    }

    // ------------------------------------------------------------------ ScrollView

    static class ScrollW extends UIActor {
        ScrollPane pane;
        Group content = new Group();
        float lastX = -1, lastY = -1;
        UIParts.Pic bg;

        ScrollW(UIView v, UINode n) {
            super(v, n);
            content.setTouchable(Touchable.childrenOnly);
        }

        @Override
        public Group childParent() {
            return content;
        }

        @Override
        protected void onLayout() {
            UITheme th = view.theme(node);
            if (bg != null) bg.remove();
            bg = null;
            if (node.has("bgColor")) {
                bg = pic(UIArt.plain(th.resolveColor(node.str("bgColor", ""), 0), 0, node.rw, node.rh));
                addActorAt(0, bg);
            }
            float sx = 0, sy = 0;
            if (pane != null) {
                sx = pane.getScrollX();
                sy = pane.getScrollY();
                pane.remove();
            }
            String dir = node.str("direction", "vertical").toLowerCase();
            boolean vert = dir.startsWith("v") || dir.equals("both"), hor = dir.startsWith("h") || dir.equals("both");
            ScrollPane.ScrollPaneStyle st = new ScrollPane.ScrollPaneStyle();
            if (node.bool("scrollbar", true)) {
                int c = UITheme.withAlpha(th.color("text"), 0.45f);
                st.vScrollKnob = UIAssets.art(UIArt.plain(c, 10, 8, 40));
                st.hScrollKnob = UIAssets.art(UIArt.plain(c, 10, 40, 8));
            }
            float[] pad = UILayout.padding(node);
            content.setSize(Math.max(1, node.contentW), Math.max(1, node.contentH));
            pane = new ScrollPane(content, st);
            pane.setBounds(pad[0], pad[3], Math.max(0, node.rw - pad[0] - pad[2]), Math.max(0, node.rh - pad[1] - pad[3]));
            pane.setScrollingDisabled(!hor, !vert);
            pane.setOverscroll(node.bool("overscroll", true) && hor, node.bool("overscroll", true) && vert);
            pane.setFlingTime(node.bool("fling", true) ? 1f : 0f);
            pane.setFadeScrollBars(true);
            pane.setClamp(true);
            pane.layout();
            pane.setScrollX(sx);
            pane.setScrollY(sy);
            addActor(pane);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (pane != null && (pane.getScrollX() != lastX || pane.getScrollY() != lastY)) {
                boolean first = lastX < 0;
                lastX = pane.getScrollX();
                lastY = pane.getScrollY();
                if (!first) view.fireScroll(this, lastX, lastY);
            }
        }

        void scrollTo(float x, float y) {
            if (pane != null) {
                pane.setScrollX(x);
                pane.setScrollY(y);
            }
        }
    }
}
