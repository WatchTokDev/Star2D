package com.star4droid.template.ui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/** Creates the actor tree of a document: one {@link UIActor} per node. */
final class UIBuilder {
    private UIBuilder() {}

    static UIActor build(UIView view, UINode n) {
        UIActor a;
        switch (n.type) {
            case UINode.LABEL: a = new UIWidgets.LabelW(view, n); break;
            case UINode.IMAGE: case UINode.ICON: a = new UIWidgets.ImageW(view, n); break;
            case UINode.BUTTON: a = new UIWidgets.ButtonW(view, n); break;
            case UINode.PROGRESS: case UINode.SLIDER: a = new UIWidgets.BarW(view, n); break;
            case UINode.CHECKBOX: case UINode.RADIO: a = new UIWidgets.CheckW(view, n); break;
            case UINode.SWITCH: a = new UIWidgets.SwitchW(view, n); break;
            case UINode.TEXTFIELD: a = new UIWidgets.FieldW(view, n); break;
            case UINode.SCROLL: a = new UIWidgets.ScrollW(view, n); break;
            default: a = new UIWidgets.Box(view, n); break;
        }
        view.register(a);
        boolean generic = !(a instanceof UIWidgets.ButtonW) && !(a instanceof UIWidgets.BarW) && !(a instanceof UIWidgets.CheckW)
                && !(a instanceof UIWidgets.SwitchW) && !(a instanceof UIWidgets.FieldW) && !(a instanceof UIWidgets.ScrollW);
        if (generic) addGenericClick(view, a);
        if (n.isContainer()) {
            for (UINode c : n.children) a.childParent().addActor(build(view, c));
        }
        return a;
    }

    /** Panels, labels, images... can be clicked too (when touchable): onClick / onTouchStart / onTouchEnd of the node. */
    private static void addGenericClick(final UIView view, final UIActor a) {
        a.addListener(new ClickListener() {
            boolean mine(InputEvent e) {
                return UIActor.owner(e.getTarget()) == a;
            }

            @Override
            public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                if (!mine(e) || a.isOff()) return false;
                boolean r = super.touchDown(e, x, y, pointer, button);
                if (r) view.fireTouchStart(a);
                return r;
            }

            @Override
            public void touchUp(InputEvent e, float x, float y, int pointer, int button) {
                boolean was = isPressed();
                super.touchUp(e, x, y, pointer, button);
                if (was) view.fireTouchEnd(a);
            }

            @Override
            public void clicked(InputEvent e, float x, float y) {
                if (mine(e)) view.fireClick(a);
            }
        });
    }
}
