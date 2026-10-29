package com.star4droid.template.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A .ui document shown on a stage. Add it to a stage (or to a UIHolder element does it for you), give it a size, done:
 * <pre>
 * UIView ui = UIView.load(projectPath, "hud");   // reads &lt;project&gt;/UI/hud.ui
 * ui.setSize(1280, 720);
 * ui.setEvents(new UIEventsAdapter(){ public void onClick(UIView ui, String id){ ... } });
 * stage.addActor(ui);
 * ui.setText("score", "10");   // change things by node id
 * </pre>
 * Size and fit: {@code stretch} (default) lays the document out at the real size of the view, so it adapts; {@code contain} /
 * {@code cover} lay it out at its design size and scale it uniformly (letterbox / crop).
 * Methods are meant for the GL thread (the game loop).
 */
public class UIView extends ClipGroup {
    private final String projectPath;
    private UIDocument doc;
    private UIEvents events;
    private UIListeners listeners = new UIListeners();
    private final Group content = new Group();
    private final LinkedHashMap<String, UIActor> actors = new LinkedHashMap<>();
    private String fit = "stretch";
    private boolean placeholders;
    private boolean built;
    private UILayout.Measurer measurer;
    private UIActor rootActor;

    public UIView(String projectPath, UIDocument doc) {
        this.projectPath = projectPath == null ? "" : projectPath;
        this.doc = doc;
        content.setTransform(true);
        content.setTouchable(Touchable.childrenOnly);
        addActor(content);
        setTouchable(Touchable.childrenOnly);
        setName(doc == null ? "ui" : doc.name);
        rebuild();
    }

    // ------------------------------------------------------------------ loading

    /** File of the document called {@code name} (with or without ".ui"): {@code <project>/UI/name.ui}. */
    public static File uiFile(String projectPath, String name) {
        String n = name == null ? "" : name.trim();
        if (!n.toLowerCase().endsWith(".ui")) n += ".ui";
        return new File(projectPath + "/UI/" + n);
    }

    /** Reads {@code <project>/UI/name.ui}. A missing / broken file gives an empty view (see {@link #document()}.warnings). */
    public static UIView load(String projectPath, String name) {
        File f = uiFile(projectPath, name);
        UIDocument d;
        if (name == null || name.trim().isEmpty()) {
            d = new UIDocument();
        } else if (f.exists()) {
            d = UIDocument.read(f);
        } else {
            d = new UIDocument();
            d.warnings.add("ui file not found: " + f.getPath());
        }
        return new UIView(projectPath, d);
    }

    // ------------------------------------------------------------------ settings

    public UIDocument document() {
        return doc;
    }

    /** Replaces the document and rebuilds (UI Maker preview). */
    public void setDocument(UIDocument d) {
        this.doc = d;
        rebuild();
    }

    public String projectPath() {
        return projectPath;
    }

    public UITheme theme() {
        return doc.theme;
    }

    /** Theme of one node: its own "theme" setting (or the nearest parent's), else the document theme. */
    public UITheme theme(UINode n) {
        return UITheme.forNode(n, doc.theme);
    }

    public UIView setEvents(UIEvents e) {
        this.events = e;
        return this;
    }

    /** Tells the events object that the view exists (calls onCreate). The holder element does it after setting the events. */
    public UIView created() {
        if (events != null && built) safe(new Runnable() {
            @Override
            public void run() {
                events.onCreate(UIView.this);
            }
        });
        return this;
    }

    public UIEvents getEvents() {
        return events;
    }

    /** Listeners registered from code per node id (onClick("btnPlay", r)...): they run after {@link UIEvents}. */
    public UIListeners listeners() {
        return listeners;
    }

    /** Uses another listener list (a UIHolder keeps its own so they survive loading another .ui). */
    public UIView setListeners(UIListeners l) {
        this.listeners = l == null ? new UIListeners() : l;
        return this;
    }

    /** "stretch" (default), "contain" or "cover". */
    public UIView setFit(String mode) {
        String m = mode == null ? "stretch" : mode.toLowerCase();
        if (!m.equals("contain") && !m.equals("cover")) m = "stretch";
        if (!m.equals(fit)) {
            fit = m;
            relayout();
        }
        return this;
    }

    /** Editor / preview: draw a faint box where an Image has no picture. */
    public UIView setPlaceholders(boolean on) {
        placeholders = on;
        return this;
    }

    boolean placeholders() {
        return placeholders;
    }

    // ------------------------------------------------------------------ building

    void register(UIActor a) {
        actors.put(a.node.id, a);
        if (a.node.str("tooltip", "").length() > 0) {
            final UIActor target = a;
            a.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener(20, 0.4f, 0.5f, Integer.MAX_VALUE) {
                @Override
                public boolean longPress(Actor actor, float x, float y) {
                    showTip(target);
                    return false;
                }
            });
        }
    }

    private com.badlogic.gdx.scenes.scene2d.ui.Label tip;

    /** Tooltip of a node (its {@code tooltip} property): a small label above it for 2 seconds. */
    void showTip(UIActor a) {
        String text = a.node.str("tooltip", "");
        if (text.isEmpty() || a.getStage() == null && getStage() == null) return;
        if (tip != null) tip.remove();
        com.badlogic.gdx.graphics.g2d.BitmapFont f = UIAssets.font(projectPath, doc.theme.font);
        com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle st = new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(f, UIParts.color(0xFFFFFFFF));
        st.background = UIAssets.solid(0xE0202020);
        tip = new com.badlogic.gdx.scenes.scene2d.ui.Label(text, st);
        tip.setFontScale(UIAssets.fontScale(f, 14f));
        tip.setAlignment(com.badlogic.gdx.utils.Align.center);
        tip.setTouchable(Touchable.disabled);
        tip.pack();
        tip.setSize(tip.getWidth() + 16, tip.getHeight() + 8);
        com.badlogic.gdx.math.Vector2 v = a.localToAscendantCoordinates(this, new com.badlogic.gdx.math.Vector2(a.getWidth() / 2f, a.getHeight()));
        float x = Math.max(0, Math.min(getWidth() - tip.getWidth(), v.x - tip.getWidth() / 2f));
        float y = Math.min(getHeight() - tip.getHeight(), v.y + 6);
        tip.setPosition(x, Math.max(0, y));
        addActor(tip);
        tip.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(2f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor()));
    }

    /** Throws the actors away and builds them again from the document. Values changed at run time are lost (they live in the actors). */
    public void rebuild() {
        for (UIActor a : actors.values()) if (a instanceof UIWidgets.FieldW) ((UIWidgets.FieldW) a).dispose();
        if (tip != null) { tip.remove(); tip = null; }
        content.clearChildren();
        actors.clear();
        built = false;
        if (doc == null || doc.root == null) return;
        measurer = UIAssets.measurer(projectPath, doc.theme);
        rootActor = UIBuilder.build(this, doc.root);
        content.addActor(rootActor);
        built = true;
        relayout();
        if (events != null) safe(new Runnable() {
            @Override
            public void run() {
                events.onCreate(UIView.this);
            }
        });
    }

    /** Computes the layout again (after sizes, texts or visibility changed) and moves every actor. */
    public void relayout() {
        if (!built) return;
        float w = getWidth(), h = getHeight();
        float dw = doc.designWidth, dh = doc.designHeight;
        if (w <= 0 || h <= 0) {
            // not sized yet: use the design size so the view is usable standalone
            w = dw;
            h = dh;
            super.setSize(w, h);
        }
        float lw = w, lh = h, scale = 1f, ox = 0, oy = 0;
        if (!fit.equals("stretch")) {
            lw = dw;
            lh = dh;
            scale = fit.equals("cover") ? Math.max(w / dw, h / dh) : Math.min(w / dw, h / dh);
            ox = (w - dw * scale) / 2f;
            oy = (h - dh * scale) / 2f;
        }
        UILayout.layout(doc.root, doc.theme, lw, lh, measurer);
        content.setBounds(ox, oy, lw, lh);
        content.setOrigin(0, 0);
        content.setScale(scale);
        setClip(fit.equals("cover"));
        applyAll(rootActor);
    }

    private void applyAll(UIActor a) {
        a.applyLayout();
        Group parent = a.childParent();
        for (Actor c : parent.getChildren()) if (c instanceof UIActor) applyAll((UIActor) c);
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        relayout();
    }

    // ------------------------------------------------------------------ lookup

    public UIActor find(String id) {
        return actors.get(id);
    }

    public UINode node(String id) {
        UIActor a = actors.get(id);
        return a == null ? null : a.node;
    }

    public boolean has(String id) {
        return actors.containsKey(id);
    }

    public List<String> ids() {
        return new ArrayList<>(actors.keySet());
    }

    private UIActor need(String id) {
        UIActor a = actors.get(id);
        if (a == null) Gdx.app.error("star2d_ui", "ui '" + doc.name + "' has no node '" + id + "'. Ids: " + actors.keySet());
        return a;
    }

    // ------------------------------------------------------------------ values (all by node id; unknown ids are logged and ignored)

    /** Label / Button / Checkbox / Radio text, TextField content. */
    public UIView setText(String id, String text) {
        UIActor a = need(id);
        if (a == null) return this;
        a.node.set("text", text == null ? "" : text);
        a.refresh();
        if (isWrap(a.node)) relayout();
        return this;
    }

    public String getText(String id) {
        UIActor a = need(id);
        if (a == null) return "";
        if (a instanceof UIWidgets.FieldW && ((UIWidgets.FieldW) a).field != null) return ((UIWidgets.FieldW) a).field.getText();
        return a.node.str("text", "");
    }

    /** Slider / Progress value (no onChange event). */
    public UIView setValue(String id, float v) {
        UIActor a = need(id);
        if (a instanceof UIWidgets.BarW) {
            ((UIWidgets.BarW) a).setValue(v, false);
            a.node.set("value", v);
        }
        return this;
    }

    public float getValue(String id) {
        UIActor a = need(id);
        if (a instanceof UIWidgets.BarW) return ((UIWidgets.BarW) a).value;
        if (a instanceof UIWidgets.CheckW) return ((UIWidgets.CheckW) a).checked ? 1f : 0f;
        if (a instanceof UIWidgets.SwitchW) return ((UIWidgets.SwitchW) a).on ? 1f : 0f;
        return 0f;
    }

    /** Slider / Progress range. */
    public UIView setRange(String id, float min, float max) {
        UIActor a = need(id);
        if (a != null) {
            a.node.set("min", min);
            a.node.set("max", max);
            a.applyLayout();
        }
        return this;
    }

    public UIView setChecked(String id, boolean on) {
        UIActor a = need(id);
        if (a instanceof UIWidgets.CheckW) ((UIWidgets.CheckW) a).set(on, false);
        else if (a instanceof UIWidgets.SwitchW) ((UIWidgets.SwitchW) a).set(on, false);
        return this;
    }

    public boolean isChecked(String id) {
        UIActor a = need(id);
        if (a instanceof UIWidgets.CheckW) return ((UIWidgets.CheckW) a).checked;
        if (a instanceof UIWidgets.SwitchW) return ((UIWidgets.SwitchW) a).on;
        return false;
    }

    /** Hidden nodes are not drawn, get no touch and leave their room to the others. */
    public UIView setVisible(String id, boolean v) {
        UIActor a = need(id);
        if (a != null && a.node.visible != v) {
            a.node.visible = v;
            relayout();
        }
        return this;
    }

    public boolean isVisible(String id) {
        UIActor a = actors.get(id);
        return a != null && a.node.visible;
    }

    /** Disabled nodes are dimmed and ignore touch. */
    public UIView setEnabled(String id, boolean on) {
        UIActor a = need(id);
        if (a != null) {
            a.node.enabled = on;
            a.applyTouch();
        }
        return this;
    }

    public boolean isEnabled(String id) {
        UIActor a = actors.get(id);
        return a != null && !a.isOff();
    }

    public UIView setAlpha(String id, float alpha) {
        UIActor a = need(id);
        if (a != null) {
            a.node.alpha = alpha;
            a.getColor().a = alpha;
        }
        return this;
    }

    /** Project image (relative to images/) of an Image, Icon, Button... "" removes it. */
    public UIView setImage(String id, String image) {
        UIActor a = need(id);
        if (a != null) {
            a.node.set("image", image == null || image.isEmpty() ? null : image);
            a.refresh();
            a.applyLayout();
        }
        return this;
    }

    /** Tints a node and everything inside it: "#rrggbb[aa]" or a theme role. null / "" removes the tint. */
    public UIView setColor(String id, String color) {
        UIActor a = need(id);
        if (a != null) {
            a.node.set("tint", color == null || color.isEmpty() ? null : color);
            a.applyLayout();
        }
        return this;
    }

    /** Sets any property by key (same keys and values as in the .ui file / the UI Maker). Returns an error text or null. */
    public String set(String id, String key, String value) {
        UIActor a = need(id);
        if (a == null) return "no node " + id;
        String err = UIWidgetRegistry.set(a.node, key, value);
        if (err == null) {
            a.refresh();
            relayout();
        }
        return err;
    }

    public String getTag(String id) {
        UINode n = node(id);
        return n == null ? "" : n.str("tag", "");
    }

    /** Moves a ScrollView (px from the top-left of its content). */
    public UIView scrollTo(String id, float x, float y) {
        UIActor a = need(id);
        if (a instanceof UIWidgets.ScrollW) ((UIWidgets.ScrollW) a).scrollTo(x, y);
        return this;
    }

    /** Does what a click does (fires onClick, toggles checkboxes...). */
    public UIView click(String id) {
        UIActor a = need(id);
        if (a == null || a.isOff()) return this;
        if (a instanceof UIWidgets.CheckW) {
            UIWidgets.CheckW c = (UIWidgets.CheckW) a;
            if (c.radio) c.set(true, true);
            else c.set(!c.checked, true);
        } else if (a instanceof UIWidgets.SwitchW) {
            ((UIWidgets.SwitchW) a).set(!((UIWidgets.SwitchW) a).on, true);
        }
        fireClick(a);
        return this;
    }

    private static boolean isWrap(UINode n) {
        UINode.Size w = n.width(), h = n.height();
        return w.kind == UINode.WRAP || h.kind == UINode.WRAP || w.kind == UINode.AUTO || h.kind == UINode.AUTO;
    }

    void uncheckOthers(UIWidgets.CheckW c) {
        String g = c.node.str("group", "group1");
        for (UIActor a : actors.values()) {
            if (a != c && a instanceof UIWidgets.CheckW) {
                UIWidgets.CheckW o = (UIWidgets.CheckW) a;
                if (o.radio && g.equals(o.node.str("group", "group1")) && o.checked) {
                    o.checked = false;
                    o.node.set("checked", false);
                    o.refresh();
                }
            }
        }
    }

    // ------------------------------------------------------------------ events out

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Gdx.app.error("star2d_ui", "error in ui event: " + t, t);
            try {
                com.star4droid.template.Utils.Utils.Log("ui event error", com.star4droid.template.Utils.Utils.getStackTraceString(t));
            } catch (Throwable ignored) {
            }
        }
    }

    void playClick(UINode n) {
        if (!n.has("clickSound")) return;
        Sound s = UIAssets.sound(projectPath, n.str("clickSound", ""));
        if (s != null) s.play(1f);
    }

    // ------------------------------------------------------------------ built-in click actions

    private final ArrayList<UIDocument> history = new ArrayList<>();

    /**
     * Runs the {@code action} prop of a clicked node, so menus, tabs and dialogs work without writing code. Several commands
     * are separated by ';':
     * <ul>
     * <li>{@code show:id}, {@code hide:id}, {@code toggle:id} - visibility (hidden nodes take no room)</li>
     * <li>{@code page:id} - shows {@code id} and hides its siblings (one visible page of a Stack: screens, wizard steps)</li>
     * <li>{@code tab:id} - like page, and this button becomes the selected tab (its sibling buttons get props.tabOff, it gets props.tabOn)</li>
     * <li>{@code select} - only marks this button as the selected one of its row (choice buttons)</li>
     * <li>{@code open:uiName} - shows another .ui in this view ({@code back} returns), {@code back}</li>
     * <li>{@code set:id.key=value} - sets any property (e.g. {@code set:title.text=Hello})</li>
     * </ul>
     * Unknown commands are ignored. The node's event code / listeners still run after the action.
     */
    public void runAction(String action, UINode source) {
        if (action == null) return;
        for (String part : action.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int c = p.indexOf(':');
            String cmd = (c < 0 ? p : p.substring(0, c)).trim().toLowerCase();
            String arg = c < 0 ? "" : p.substring(c + 1).trim();
            try {
                switch (cmd) {
                    case "show": setVisible(arg, true); break;
                    case "hide": setVisible(arg, false); break;
                    case "toggle": setVisible(arg, !isVisible(arg)); break;
                    case "page": showPage(arg); break;
                    case "tab":
                        showPage(arg);
                        if (source != null) selectTab(source);
                        break;
                    case "select":
                        if (source != null) selectTab(source);
                        break;
                    case "open":
                        if (!arg.isEmpty()) {
                            File f = uiFile(projectPath, arg);
                            if (f.exists()) {
                                history.add(doc);
                                setDocument(UIDocument.read(f));
                                created();
                                return; // the old nodes are gone
                            }
                        }
                        break;
                    case "back":
                        if (!history.isEmpty()) {
                            setDocument(history.remove(history.size() - 1));
                            created();
                            return;
                        }
                        break;
                    case "set": {
                        int dot = arg.indexOf('.'), eq = arg.indexOf('=');
                        if (dot > 0 && eq > dot) set(arg.substring(0, dot).trim(), arg.substring(dot + 1, eq).trim(), arg.substring(eq + 1).trim());
                        break;
                    }
                    default:
                        break;
                }
            } catch (Throwable t) {
                Gdx.app.error("star2d_ui", "ui action '" + p + "': " + t);
            }
        }
    }

    /** Shows the node and hides the other children of its parent. */
    public UIView showPage(String id) {
        UINode n = node(id);
        if (n == null || n.parent == null) return this;
        boolean changed = false;
        for (UINode s : n.parent.children) {
            boolean v = s == n;
            if (s.visible != v) {
                s.visible = v;
                changed = true;
            }
        }
        if (changed) relayout();
        return this;
    }

    /** Marks a tab button as selected: its sibling buttons get their tabOff style, it gets tabOn (defaults primary / ghost). */
    private void selectTab(UINode tab) {
        if (tab.parent == null) return;
        for (UINode s : tab.parent.children) {
            if (!UINode.BUTTON.equals(s.type)) continue;
            String want = s == tab ? s.str("tabOn", "primary") : s.str("tabOff", "ghost");
            if (!want.equals(s.str("style", ""))) {
                s.set("style", want);
                UIActor a = actors.get(s.id);
                if (a != null) a.refresh();
            }
        }
        relayout();
    }

    void fireClick(final UIActor a) {
        playClick(a.node);
        if (a.node.has("action")) runAction(a.node.str("action", ""), a.node);
        if (events != null) safe(new Runnable() {
            @Override
            public void run() {
                events.onClick(UIView.this, a.node.id);
            }
        });
        listeners.fire("onClick", a.node.id, null, null);
    }

    void fireTouchStart(final UIActor a) {
        if (events != null) safe(new Runnable() {
            @Override
            public void run() {
                events.onTouchStart(UIView.this, a.node.id);
            }
        });
        listeners.fire("onTouchStart", a.node.id, null, null);
    }

    void fireTouchEnd(final UIActor a) {
        if (events != null) safe(new Runnable() {
            @Override
            public void run() {
                events.onTouchEnd(UIView.this, a.node.id);
            }
        });
        listeners.fire("onTouchEnd", a.node.id, null, null);
    }

    void fireChange(final UIActor a, final Object v) {
        if (events != null) safe(new Runnable() {
            @Override
            public void run() {
                events.onChange(UIView.this, a.node.id, v instanceof Float || v instanceof Boolean || v instanceof String ? v : String.valueOf(v));
            }
        });
        listeners.fire("onChange", a.node.id, v instanceof Float || v instanceof Boolean || v instanceof String ? v : String.valueOf(v), null);
    }

    void fireSubmit(final UIActor a, final String text) {
        if (events != null) safe(new Runnable() {
            @Override
            public void run() {
                events.onSubmit(UIView.this, a.node.id, text);
            }
        });
        listeners.fire("onSubmit", a.node.id, text, null);
    }

    void fireScroll(final UIActor a, final float x, final float y) {
        if (events != null) safe(new Runnable() {
            @Override
            public void run() {
                events.onScroll(UIView.this, a.node.id, x, y);
            }
        });
        listeners.fire("onScroll", a.node.id, Float.valueOf(x), Float.valueOf(y));
    }

    /** Frees what this view owns (text field fonts). Call when the view is thrown away for good. */
    public void dispose() {
        for (UIActor a : actors.values()) if (a instanceof UIWidgets.FieldW) ((UIWidgets.FieldW) a).dispose();
        remove();
    }

    /** Debug helper: tint color of the whole view. */
    public void tintAll(Color c) {
        if (rootActor != null) rootActor.setTintColor(c);
    }
}
