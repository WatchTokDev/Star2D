package com.star4droid.template.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/**
 * One node of a .ui document (see docs/UI.md). Plain data + tree editing helpers: no libGDX, so it can be used by the
 * runtime, by the editor (UI Maker) and by offline tests.
 *
 * Geometry fields are flat on the node, type specific settings live in {@link #props}.
 * Coordinates everywhere: pixels, origin BOTTOM-LEFT of the parent, y up (same as the rest of the engine).
 */
public class UINode {
    public static final String PANEL = "Panel", STACK = "Stack", LINEAR = "Linear", GRID = "Grid", SCROLL = "ScrollView",
            LABEL = "Label", IMAGE = "Image", ICON = "Icon", BUTTON = "Button", PROGRESS = "Progress", SLIDER = "Slider",
            CHECKBOX = "Checkbox", RADIO = "Radio", SWITCH = "Switch", TEXTFIELD = "TextField";

    /** All node types, containers first. */
    public static final String[] TYPES = {PANEL, STACK, LINEAR, GRID, SCROLL, LABEL, IMAGE, ICON, BUTTON, PROGRESS, SLIDER, CHECKBOX, RADIO, SWITCH, TEXTFIELD};

    // ---- size spec kinds
    public static final int AUTO = 0, PX = 1, PCT = 2, WRAP = 3, FILL = 4;

    /** Parsed width / height value. */
    public static final class Size {
        public final int kind;
        public final float v;

        public Size(int kind, float v) {
            this.kind = kind;
            this.v = v;
        }

        @Override
        public String toString() {
            switch (kind) {
                case PX: return trim(v);
                case PCT: return trim(v) + "%";
                case WRAP: return "wrap";
                case FILL: return "fill";
                default: return "auto";
            }
        }
    }

    public static final Size SIZE_AUTO = new Size(AUTO, 0), SIZE_WRAP = new Size(WRAP, 0), SIZE_FILL = new Size(FILL, 0);

    // ---- horizontal / vertical anchor kinds
    public static final int LEFT = 0, CENTER = 1, RIGHT = 2, TOP = 0, MIDDLE = 1, BOTTOM = 2, STRETCH = 3;

    // ------------------------------------------------------------------ data
    public String id = "";
    public String type = PANEL;
    /** Where the node sticks inside its parent: topleft (default) top topright left center right bottomleft bottom bottomright stretch stretchx stretchy. */
    public String anchor = "topleft";
    /** Distances from the parent's edges: left, top, right, bottom (inward). */
    public float ml, mt, mr, mb;
    /** Extra offset after anchoring: x to the right, y UP. */
    public float x, y;
    /** "120", "50%", "wrap", "fill" or null (type default). */
    public String w, h;
    public boolean visible = true, enabled = true;
    /** Editor only: a locked node cannot be selected / moved / resized in the UI Maker preview. The game ignores it. */
    public boolean locked = false;
    public float alpha = 1f;
    /** Share of the free space in a Linear parent (0 = none). */
    public float weight = 0f;
    /** "true" / "false" or null = default for the type. */
    public String touchable;
    /** Type specific settings. */
    public JsonObject props = new JsonObject();
    public final ArrayList<UINode> children = new ArrayList<>();
    public UINode parent;

    // ---- resolved layout (filled by UILayout, never saved). Relative to the parent's content box, bottom-left origin.
    public float rx, ry, rw, rh;
    /** ScrollView only: size of the scrolled content. */
    public float contentW, contentH;

    public UINode() {}

    public UINode(String type, String id) {
        this.type = type;
        this.id = id;
    }

    // ------------------------------------------------------------------ creation with sensible defaults

    /** A new node of {@code type} with its useful default settings (what the UI Maker adds when you pick it from the palette). */
    public static UINode create(String type, String id) {
        UINode n = new UINode(type, id);
        switch (type) {
            case PANEL: n.props.addProperty("radius", 16); break;
            case STACK: break;
            case LINEAR: n.props.addProperty("orientation", "vertical"); n.props.addProperty("spacing", 8); n.props.addProperty("padding", 8); break;
            case GRID: n.props.addProperty("columns", 3); n.props.addProperty("spacing", 8); n.props.addProperty("padding", 8); break;
            case SCROLL: n.props.addProperty("direction", "vertical"); break;
            case LABEL: n.props.addProperty("text", "Label"); break;
            case IMAGE: n.w = "96"; n.h = "96"; break;
            case ICON: n.w = "40"; n.h = "40"; break;
            case BUTTON: n.props.addProperty("text", "Button"); n.props.addProperty("style", "primary"); n.w = "180"; n.h = "64"; break;
            case PROGRESS: n.props.addProperty("value", 50); n.props.addProperty("max", 100); n.w = "240"; break;
            case SLIDER: n.props.addProperty("value", 0.5f); n.props.addProperty("min", 0); n.props.addProperty("max", 1); n.w = "240"; break;
            case CHECKBOX: n.props.addProperty("text", "Checkbox"); break;
            case RADIO: n.props.addProperty("text", "Radio"); n.props.addProperty("group", "group1"); break;
            case SWITCH: n.w = "72"; n.h = "40"; break;
            case TEXTFIELD: n.props.addProperty("hint", "Type here..."); n.w = "260"; n.h = "56"; break;
            default: break;
        }
        return n;
    }

    public static boolean isKnownType(String t) {
        for (String s : TYPES) if (s.equals(t)) return true;
        return false;
    }

    /** Type names are matched case-insensitively and a few aliases are accepted ("scroll", "textfield"...). Returns the canonical name or null. */
    public static String canonicalType(String t) {
        if (t == null) return null;
        String l = t.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        for (String s : TYPES) if (s.toLowerCase(Locale.ROOT).equals(l)) return s;
        switch (l) {
            case "scroll": case "scrollpane": case "scrollable": case "list": return SCROLL;
            case "text": case "textview": return LABEL;
            case "input": case "edittext": case "textinput": return TEXTFIELD;
            case "toggle": return SWITCH;
            case "bar": case "progressbar": return PROGRESS;
            case "container": case "group": case "box": return PANEL;
            case "vertical": case "horizontal": case "row": case "column": return LINEAR;
            case "img": case "picture": return IMAGE;
            case "btn": return BUTTON;
            default: return null;
        }
    }

    /** True for nodes that hold children. */
    public boolean isContainer() {
        return type.equals(PANEL) || type.equals(STACK) || type.equals(LINEAR) || type.equals(GRID) || type.equals(SCROLL);
    }

    // ------------------------------------------------------------------ props helpers

    public boolean has(String k) {
        return props.has(k) && !props.get(k).isJsonNull();
    }

    public String str(String k, String def) {
        if (!has(k)) return def;
        JsonElement e = props.get(k);
        if (e.isJsonPrimitive()) return e.getAsString();
        return e.toString();
    }

    public float num(String k, float def) {
        if (!has(k)) return def;
        try {
            JsonElement e = props.get(k);
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsFloat();
                if (p.isBoolean()) return p.getAsBoolean() ? 1f : 0f;
                return Float.parseFloat(p.getAsString().trim());
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    public int integer(String k, int def) {
        return Math.round(num(k, def));
    }

    public boolean bool(String k, boolean def) {
        if (!has(k)) return def;
        try {
            JsonElement e = props.get(k);
            if (e.isJsonPrimitive()) {
                JsonPrimitive p = e.getAsJsonPrimitive();
                if (p.isBoolean()) return p.getAsBoolean();
                String s = p.getAsString().trim().toLowerCase(Locale.ROOT);
                if (s.equals("true") || s.equals("1") || s.equals("yes")) return true;
                if (s.equals("false") || s.equals("0") || s.equals("no")) return false;
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    /** Sets (or with null removes) a setting. Accepts String, Number and Boolean. */
    public UINode set(String k, Object v) {
        if (v == null) props.remove(k);
        else if (v instanceof Number) props.addProperty(k, (Number) v);
        else if (v instanceof Boolean) props.addProperty(k, (Boolean) v);
        else props.addProperty(k, v.toString());
        return this;
    }

    // ------------------------------------------------------------------ anchors and sizes

    /** {horizontal, vertical}: horizontal LEFT/CENTER/RIGHT/STRETCH, vertical TOP/MIDDLE/BOTTOM/STRETCH. */
    public static int[] parseAnchor(String a) {
        int h = LEFT, v = TOP;
        if (a == null) return new int[]{h, v};
        String s = a.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        boolean sx = s.contains("stretchx") || s.contains("fillx") || s.contains("horizontalstretch");
        boolean sy = s.contains("stretchy") || s.contains("filly") || s.contains("verticalstretch");
        boolean both = (s.contains("stretch") || s.contains("fill")) && !sx && !sy;
        if (both) return new int[]{STRETCH, STRETCH};
        boolean left = s.contains("left") || s.contains("start"), right = s.contains("right") || s.contains("end");
        boolean top = s.contains("top"), bottom = s.contains("bottom");
        boolean center = s.contains("center") || s.contains("centre") || s.contains("middle");
        if (left) h = LEFT;
        else if (right) h = RIGHT;
        else if (center || top || bottom) h = CENTER;
        if (top) v = TOP;
        else if (bottom) v = BOTTOM;
        else if (center || left || right) v = MIDDLE;
        if (s.equals("center") || s.equals("middle")) { h = CENTER; v = MIDDLE; }
        if (sx) h = STRETCH;
        if (sy) v = STRETCH;
        return new int[]{h, v};
    }

    public int ax() {
        return parseAnchor(anchor)[0];
    }

    public int ay() {
        return parseAnchor(anchor)[1];
    }

    public static Size parseSize(String s) {
        if (s == null) return SIZE_AUTO;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("auto")) return SIZE_AUTO;
        if (t.equals("wrap") || t.equals("content") || t.equals("wrap_content")) return SIZE_WRAP;
        if (t.equals("fill") || t.equals("match") || t.equals("match_parent") || t.equals("max")) return SIZE_FILL;
        try {
            if (t.endsWith("%")) return new Size(PCT, Float.parseFloat(t.substring(0, t.length() - 1).trim()) / 100f);
            if (t.endsWith("px")) t = t.substring(0, t.length() - 2).trim();
            return new Size(PX, Float.parseFloat(t));
        } catch (Exception e) {
            return SIZE_AUTO;
        }
    }

    public Size width() {
        return parseSize(w);
    }

    public Size height() {
        return parseSize(h);
    }

    public UINode margin(float l, float t, float r, float b) {
        ml = l;
        mt = t;
        mr = r;
        mb = b;
        return this;
    }

    public UINode margin(float all) {
        return margin(all, all, all, all);
    }

    public UINode size(Object width, Object height) {
        this.w = width == null ? null : (width instanceof Number ? trim(((Number) width).floatValue()) : width.toString());
        this.h = height == null ? null : (height instanceof Number ? trim(((Number) height).floatValue()) : height.toString());
        return this;
    }

    // ------------------------------------------------------------------ tree

    public UINode addChild(UINode c) {
        return addChild(children.size(), c);
    }

    public UINode addChild(int index, UINode c) {
        if (c.parent != null) c.parent.children.remove(c);
        c.parent = this;
        if (index < 0 || index > children.size()) index = children.size();
        children.add(index, c);
        return this;
    }

    public void removeFromParent() {
        if (parent != null) {
            parent.children.remove(this);
            parent = null;
        }
    }

    public int indexInParent() {
        return parent == null ? -1 : parent.children.indexOf(this);
    }

    public boolean isDescendantOf(UINode other) {
        for (UINode p = parent; p != null; p = p.parent) if (p == other) return true;
        return false;
    }

    /** Moves this node under {@code newParent} at {@code index}. Refuses cycles and non-containers; returns an error message or null. */
    public String moveTo(UINode newParent, int index) {
        if (newParent == null) return "no parent";
        if (!newParent.isContainer()) return newParent.type + " cannot hold children";
        if (newParent == this || newParent.isDescendantOf(this)) return "cannot move a node into itself";
        if (parent == null) return "the root cannot be moved";
        int cur = indexInParent();
        if (parent == newParent && index > cur) index--; // removing first shifts the later slots
        newParent.addChild(index, this);
        return null;
    }

    public UINode find(String nodeId) {
        if (nodeId == null) return null;
        if (nodeId.equals(id)) return this;
        for (UINode c : children) {
            UINode f = c.find(nodeId);
            if (f != null) return f;
        }
        return null;
    }

    /** Visits this node and all descendants (parents first). */
    public void walk(Visitor v) {
        v.visit(this);
        for (UINode c : new ArrayList<>(children)) c.walk(v);
    }

    public interface Visitor {
        void visit(UINode n);
    }

    public int countNodes() {
        int n = 1;
        for (UINode c : children) n += c.countNodes();
        return n;
    }

    /** Deep copy (same ids: call {@link UIDocument#makeIdsUnique(UINode)} before inserting it in the same document). */
    public UINode copyDeep() {
        return fromJson(toJson());
    }

    // ------------------------------------------------------------------ JSON

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("type", type);
        if (anchor != null && !anchor.equals("topleft")) o.addProperty("anchor", anchor);
        if (ml != 0 || mt != 0 || mr != 0 || mb != 0) {
            if (ml == mt && mt == mr && mr == mb) o.addProperty("margin", ml);
            else {
                JsonArray a = new JsonArray();
                a.add(ml);
                a.add(mt);
                a.add(mr);
                a.add(mb);
                o.add("margin", a);
            }
        }
        if (x != 0) o.addProperty("x", x);
        if (y != 0) o.addProperty("y", y);
        if (w != null) o.add("w", sizeJson(w));
        if (h != null) o.add("h", sizeJson(h));
        if (!visible) o.addProperty("visible", false);
        if (!enabled) o.addProperty("enabled", false);
        if (locked) o.addProperty("locked", true);
        if (alpha != 1f) o.addProperty("alpha", alpha);
        if (weight != 0f) o.addProperty("weight", weight);
        if (touchable != null) o.addProperty("touchable", touchable);
        if (props.size() > 0) o.add("props", props.deepCopy());
        if (!children.isEmpty()) {
            JsonArray a = new JsonArray();
            for (UINode c : children) a.add(c.toJson());
            o.add("children", a);
        }
        return o;
    }

    private static JsonElement sizeJson(String s) {
        Size z = parseSize(s);
        if (z.kind == PX) return new JsonPrimitive(z.v);
        return new JsonPrimitive(s.trim());
    }

    public static UINode fromJson(JsonObject o) {
        UINode n = new UINode();
        n.id = str(o, "id", "");
        String t = canonicalType(str(o, "type", PANEL));
        n.type = t == null ? str(o, "type", PANEL) : t;
        n.anchor = str(o, "anchor", "topleft");
        readMargin(o.get("margin"), n);
        if (o.has("margins")) readMargin(o.get("margins"), n);
        n.x = number(o, "x", 0);
        n.y = number(o, "y", 0);
        if (o.has("w")) n.w = sizeStr(o.get("w"));
        else if (o.has("width")) n.w = sizeStr(o.get("width"));
        if (o.has("h")) n.h = sizeStr(o.get("h"));
        else if (o.has("height")) n.h = sizeStr(o.get("height"));
        n.visible = bool(o, "visible", true);
        n.enabled = bool(o, "enabled", true);
        n.locked = bool(o, "locked", false);
        n.alpha = number(o, "alpha", 1);
        n.weight = number(o, "weight", 0);
        if (o.has("touchable") && !o.get("touchable").isJsonNull()) n.touchable = o.get("touchable").getAsString();
        if (o.has("props") && o.get("props").isJsonObject()) n.props = o.getAsJsonObject("props").deepCopy();
        if (o.has("children") && o.get("children").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("children")) {
                if (e.isJsonObject()) n.addChild(fromJson(e.getAsJsonObject()));
            }
        }
        return n;
    }

    private static String sizeStr(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return trim(e.getAsFloat());
        return e.getAsString();
    }

    private static void readMargin(JsonElement e, UINode n) {
        if (e == null || e.isJsonNull()) return;
        try {
            if (e.isJsonPrimitive()) {
                float v = e.getAsFloat();
                n.margin(v);
            } else if (e.isJsonArray()) {
                JsonArray a = e.getAsJsonArray();
                if (a.size() == 1) n.margin(a.get(0).getAsFloat());
                else if (a.size() == 2) n.margin(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(0).getAsFloat(), a.get(1).getAsFloat());
                else if (a.size() >= 4) n.margin(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat());
            } else if (e.isJsonObject()) {
                JsonObject m = e.getAsJsonObject();
                n.margin(number(m, "left", number(m, "l", 0)), number(m, "top", number(m, "t", 0)),
                        number(m, "right", number(m, "r", 0)), number(m, "bottom", number(m, "b", 0)));
            }
        } catch (Exception ignored) {
        }
    }

    static String str(JsonObject o, String k, String def) {
        try {
            if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsString();
        } catch (Exception ignored) {
        }
        return def;
    }

    static float number(JsonObject o, String k, float def) {
        try {
            if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsFloat();
        } catch (Exception ignored) {
        }
        return def;
    }

    static boolean bool(JsonObject o, String k, boolean def) {
        try {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                JsonElement e = o.get(k);
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return Boolean.parseBoolean(e.getAsString());
                return e.getAsBoolean();
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    static String trim(float v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e7) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    @Override
    public String toString() {
        return type + "#" + id;
    }

    /** Copies every setting of {@code o} (props, geometry) but not the tree. */
    public void copyFrom(UINode o) {
        id = o.id;
        type = o.type;
        anchor = o.anchor;
        ml = o.ml;
        mt = o.mt;
        mr = o.mr;
        mb = o.mb;
        x = o.x;
        y = o.y;
        w = o.w;
        h = o.h;
        visible = o.visible;
        enabled = o.enabled;
        locked = o.locked;
        alpha = o.alpha;
        weight = o.weight;
        touchable = o.touchable;
        props = o.props.deepCopy();
    }

    /** All props as a plain map (for property editors). */
    public Map<String, JsonElement> propMap() {
        java.util.LinkedHashMap<String, JsonElement> m = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : props.entrySet()) m.put(e.getKey(), e.getValue());
        return m;
    }
}
