package com.star4droid.template.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;

/**
 * Actor of one .ui node. It is a plain Group: the node's pieces (background, text, knob...) are its children and the nodes that are
 * children in the document are children too (inside {@link #childParent()}). Position and size come from {@link UILayout}.
 */
public class UIActor extends ClipGroup {
    public final UINode node;
    public final UIView view;
    /** Tint multiplied into every picture / text of this node and its descendants, or null. */
    Color tint;
    /** Node is switched off (enabled=false or interactable=false): dimmed, no touch. */
    boolean off;

    public UIActor(UIView view, UINode node) {
        this.view = view;
        this.node = node;
        setName(node.id);
    }

    /** Where the actors of the document's children go (ScrollView: its content group). */
    public Group childParent() {
        return this;
    }

    /** Called after bounds changed: widgets redraw their pieces for the new size. */
    protected void onLayout() {}

    /** Called when value / text / state changed from outside; widgets refresh. */
    public void refresh() {}

    /** Applies the geometry computed by the layout and the common node properties. */
    public void applyLayout() {
        setBounds(node.rx, node.ry, node.rw, node.rh);
        float px = node.num("pivotX", 0.5f), py = node.num("pivotY", 0.5f);
        setOrigin(node.rw * px, node.rh * py);
        float sc = node.num("scale", 1f);
        setScale(sc);
        setRotation(node.num("rotation", 0f));
        boolean needTransform = sc != 1f || node.num("rotation", 0f) != 0f || wantsTransform();
        setTransform(needTransform);
        getColor().a = node.alpha;
        if (node.has("tint")) setTintColor(UIParts.color(view.theme().resolveColor(node.str("tint", ""), 0xffffffff)));
        else tint = null;
        setVisible(node.visible);
        applyTouch();
        onLayout();
    }

    /** Button-like widgets need a transform for the press effect. */
    protected boolean wantsTransform() {
        return false;
    }

    /** Touchable default of the node type. */
    protected Touchable defaultTouchable() {
        switch (node.type) {
            case UINode.LABEL: case UINode.IMAGE: case UINode.ICON: case UINode.PROGRESS: return Touchable.disabled;
            case UINode.STACK: case UINode.LINEAR: case UINode.GRID:
                return node.has("bgColor") || node.has("image") ? Touchable.enabled : Touchable.childrenOnly;
            default: return Touchable.enabled;
        }
    }

    public void applyTouch() {
        Touchable t = defaultTouchable();
        String s = node.touchable;
        if (s != null) {
            switch (s.toLowerCase()) {
                case "enabled": case "true": t = Touchable.enabled; break;
                case "disabled": case "false": t = Touchable.disabled; break;
                case "childrenonly": t = Touchable.childrenOnly; break;
                default: break;
            }
        }
        off = !node.enabled || !node.bool("interactable", true);
        if (off) t = Touchable.disabled;
        setTouchable(t);
    }

    /** Nearest UIActor that is {@code a} or one of its ancestors. */
    static UIActor owner(Actor a) {
        for (Actor p = a; p != null; p = p.getParent()) if (p instanceof UIActor) return (UIActor) p;
        return null;
    }

    public boolean isOff() {
        return off;
    }

    public void setTintColor(Color c) {
        tint = c == null ? null : new Color(c);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, off ? parentAlpha * 0.45f : parentAlpha);
    }

    /** Product of the tints of {@code a} and all its UIActor ancestors, written into {@code out}; false when there is none. */
    static boolean tintOf(Actor a, Color out) {
        boolean any = false;
        for (Actor p = a; p != null; p = p.getParent()) {
            if (p instanceof UIActor && ((UIActor) p).tint != null) {
                if (!any) out.set(((UIActor) p).tint);
                else out.mul(((UIActor) p).tint);
                any = true;
            }
        }
        return any;
    }
}
