package com.star4droid.star2d.ui.uimaker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UINode;
import com.star4droid.template.ui.UITheme;
import com.star4droid.template.ui.UIWidgetRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Editing operations on a document, shared by the UI Maker dialog and the `ui` AI tool so both validate the same way.
 * Pure Java. Every method returns an error text for the person / AI (null = done) instead of throwing.
 */
public final class UiOps {
    private UiOps() {}

    /** An empty document with a transparent Stack root (new UIDocument() would give a filled Panel root). */
    public static UIDocument newDocument(String name, float w, float h) {
        UIDocument d = UIDocument.empty(name, w, h);
        UINode r = new UINode(UINode.STACK, "root");
        r.anchor = "stretch";
        d.root = r;
        return d;
    }

    // ------------------------------------------------------------------ tree editing (Maker)

    /**
     * Adds a new node of {@code type}: inside {@code selected} when it is a container, otherwise right after it (same parent).
     * With no selection it goes into the root. Returns the new node, or null with {@code err[0]} set.
     */
    public static UINode addSmart(UIDocument doc, UINode selected, String type, String[] err) {
        try {
            UINode parent;
            int index = -1;
            if (selected == null) parent = doc.root;
            else if (selected.isContainer()) parent = selected;
            else {
                parent = selected.parent == null ? doc.root : selected.parent;
                index = selected.indexInParent() + 1;
            }
            String idBase = UINode.canonicalType(type);
            return doc.add(parent.id, type, idBase == null ? null : idBase.toLowerCase(), index);
        } catch (IllegalArgumentException e) {
            if (err != null && err.length > 0) err[0] = e.getMessage();
            return null;
        }
    }

    public static String moveUp(UINode n) {
        if (n.parent == null) return "the root cannot move";
        int i = n.indexInParent();
        if (i <= 0) return "already first";
        UINode p = n.parent;
        p.children.remove(i);
        p.children.add(i - 1, n);
        return null;
    }

    public static String moveDown(UINode n) {
        if (n.parent == null) return "the root cannot move";
        int i = n.indexInParent();
        if (i >= n.parent.children.size() - 1) return "already last";
        UINode p = n.parent;
        p.children.remove(i);
        p.children.add(i + 1, n);
        return null;
    }

    /** Makes the node the last child of its previous sibling (which must be a container). */
    public static String indent(UINode n) {
        if (n.parent == null) return "the root cannot move";
        int i = n.indexInParent();
        if (i <= 0) return "there is no node above to move into";
        UINode prev = n.parent.children.get(i - 1);
        if (!prev.isContainer()) return prev.type + " '" + prev.id + "' cannot hold children";
        return n.moveTo(prev, prev.children.size());
    }

    /** Moves the node out of its parent, right after it. */
    public static String outdent(UINode n) {
        UINode p = n.parent;
        if (p == null || p.parent == null) return "already at the top level";
        return n.moveTo(p.parent, p.indexInParent() + 1);
    }

    /** Puts the node into a new container of {@code type} that takes its place. Returns the container or null. */
    public static UINode wrap(UIDocument doc, UINode n, String type, String[] err) {
        if (n.parent == null) {
            if (err != null) err[0] = "the root cannot be wrapped";
            return null;
        }
        String t = UINode.canonicalType(type);
        if (t == null || !UINode.create(t, "x").isContainer()) {
            if (err != null) err[0] = "'" + type + "' is not a container";
            return null;
        }
        UINode parent = n.parent;
        int i = n.indexInParent();
        UINode box = UINode.create(t, doc.uniqueId(t.toLowerCase()));
        // the wrapper takes over the placement of the node; the node fills the wrapper
        box.anchor = n.anchor;
        box.ml = n.ml;
        box.mt = n.mt;
        box.mr = n.mr;
        box.mb = n.mb;
        box.x = n.x;
        box.y = n.y;
        box.w = n.w == null ? "wrap" : n.w;
        box.h = n.h == null ? "wrap" : n.h;
        box.weight = n.weight;
        n.anchor = "topleft";
        n.margin(0);
        n.x = 0;
        n.y = 0;
        n.weight = 0;
        parent.children.remove(i);
        n.parent = null;
        parent.addChild(i, box);
        box.addChild(n);
        return box;
    }

    /** Renames a node (unique, cleaned). Returns the final id. */
    public static String rename(UIDocument doc, UINode n, String wanted) {
        String base = UIDocument.cleanId(wanted == null ? "" : wanted.trim());
        if (base.equals(n.id)) return n.id;
        String id = base;
        int k = 2;
        java.util.HashSet<String> used = doc.ids();
        used.remove(n.id);
        while (used.contains(id)) id = base + (k++);
        n.id = id;
        return id;
    }

    // ------------------------------------------------------------------ property helpers

    /** Reset a property to its default (removes it). */
    public static void reset(UINode n, String key) {
        UIWidgetRegistry.set(n, key, "");
    }

    /** True when the property differs from "unset" (so the editor can show a reset button). */
    public static boolean isSet(UINode n, String key) {
        UIWidgetRegistry.Prop pr = UIWidgetRegistry.find(n.type, key);
        if (pr == null) return false;
        String v = UIWidgetRegistry.get(n, key);
        if (pr.where == UIWidgetRegistry.NODE) {
            switch (key) {
                case "id": return false;
                case "anchor": return !"topleft".equals(n.anchor);
                case "margin": return n.ml != 0 || n.mt != 0 || n.mr != 0 || n.mb != 0;
                case "x": return n.x != 0;
                case "y": return n.y != 0;
                case "w": return n.w != null && !n.w.isEmpty();
                case "h": return n.h != null && !n.h.isEmpty();
                case "weight": return n.weight != 0;
                case "visible": return !n.visible;
                case "enabled": return !n.enabled;
                case "alpha": return n.alpha != 1f;
                case "touchable": return n.touchable != null && !n.touchable.isEmpty();
                default: return !v.isEmpty();
            }
        }
        return n.has(key);
    }

    /** Value to show for a property: the stored one, otherwise its default text. */
    public static String shown(UINode n, UIWidgetRegistry.Prop pr) {
        String v = UIWidgetRegistry.get(n, pr.key);
        return v.isEmpty() ? pr.def : v;
    }

    // ------------------------------------------------------------------ checks of a whole node tree (AI input)

    /**
     * Normalises every property of the subtree with the registry (clamps ranges, canonical colors...) and returns the problems:
     * unknown properties are removed and reported, bad values are reported and removed.
     */
    public static List<String> sanitize(UINode root) {
        ArrayList<String> problems = new ArrayList<>();
        root.walk(n -> sanitizeNode(n, problems));
        return problems;
    }

    private static void sanitizeNode(UINode n, ArrayList<String> problems) {
        if (UINode.canonicalType(n.type) != null) n.type = UINode.canonicalType(n.type);
        ArrayList<String> keys = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : n.propMap().entrySet()) keys.add(e.getKey());
        for (String k : keys) {
            UIWidgetRegistry.Prop pr = UIWidgetRegistry.find(n.type, k);
            JsonElement e = n.props.get(k);
            if (pr == null) {
                // props that are NODE fields must not sit in props: move them
                n.props.remove(k);
                problems.add(n.type + " '" + n.id + "': unknown property '" + k + "' removed. Known: " + knownKeys(n.type));
                continue;
            }
            String text = e.isJsonPrimitive() ? e.getAsString() : e.toString();
            if (pr.where == UIWidgetRegistry.NODE) {
                n.props.remove(k);
                String err = UIWidgetRegistry.set(n, k, text);
                if (err != null) problems.add(n.type + " '" + n.id + "': " + err);
                continue;
            }
            String err = UIWidgetRegistry.set(n, k, text);
            if (err != null) {
                n.props.remove(k);
                problems.add(n.type + " '" + n.id + "': " + err);
            }
        }
    }

    private static String knownKeys(String type) {
        StringBuilder sb = new StringBuilder();
        for (UIWidgetRegistry.Prop p : UIWidgetRegistry.propsFor(type)) sb.append(sb.length() == 0 ? "" : ", ").append(p.key);
        return sb.toString();
    }

    // ------------------------------------------------------------------ operations of the AI tool

    /**
     * Applies one operation object: {op:'add', parent, type, id, index, props:{}, ...}, {op:'set', id, props:{}}, {op:'remove', id},
     * {op:'move', id, parent, index}, {op:'duplicate', id}, {op:'wrap', id, type}. Node level keys (anchor, margin, x, y, w, h, weight,
     * visible, enabled, alpha, touchable, id) may sit next to props or inside it.
     * @return null when applied, otherwise what is wrong
     */
    public static String apply(UIDocument doc, JsonObject op, List<String> notes) {
        String kind = str(op, "op");
        if (kind.isEmpty()) kind = str(op, "action");
        switch (kind) {
            case "add": return opAdd(doc, op, notes);
            case "set": return opSet(doc, op, notes);
            case "remove": case "delete": {
                String id = str(op, "id");
                UINode n = doc.find(id);
                if (n == null) return "no node '" + id + "'. Ids: " + idList(doc);
                if (n == doc.root) return "the root cannot be removed (remove its children instead)";
                n.removeFromParent();
                return null;
            }
            case "move": {
                String id = str(op, "id");
                UINode n = doc.find(id);
                if (n == null) return "no node '" + id + "'. Ids: " + idList(doc);
                String pid = str(op, "parent");
                UINode p = pid.isEmpty() ? n.parent : doc.find(pid);
                if (p == null) return "no parent '" + pid + "'. Ids: " + idList(doc);
                int idx = op.has("index") ? op.get("index").getAsInt() : p.children.size();
                return n.moveTo(p, idx);
            }
            case "duplicate": {
                String id = str(op, "id");
                UINode c = doc.duplicate(id);
                if (c == null) return "no node '" + id + "' (the root cannot be duplicated)";
                if (notes != null) notes.add("duplicate of '" + id + "' is '" + c.id + "'");
                return null;
            }
            case "wrap": {
                UINode n = doc.find(str(op, "id"));
                if (n == null) return "no node '" + str(op, "id") + "'. Ids: " + idList(doc);
                String[] err = new String[1];
                UINode box = wrap(doc, n, str(op, "type").isEmpty() ? "Panel" : str(op, "type"), err);
                if (box == null) return err[0];
                if (notes != null) notes.add("'" + n.id + "' is now inside '" + box.id + "'");
                return null;
            }
            default:
                return "unknown op '" + kind + "'. Use add | set | remove | move | duplicate | wrap.";
        }
    }

    private static String opAdd(UIDocument doc, JsonObject op, List<String> notes) {
        String type = str(op, "type");
        if (type.isEmpty()) return "add needs type. Types: " + String.join(", ", UINode.TYPES);
        String pid = str(op, "parent");
        UINode parent = pid.isEmpty() ? doc.root : doc.find(pid);
        if (parent == null) return "no parent '" + pid + "'. Ids: " + idList(doc);
        int index = op.has("index") && op.get("index").isJsonPrimitive() ? op.get("index").getAsInt() : -1;
        UINode n;
        try {
            n = doc.add(parent.id, type, str(op, "id"), index);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        ArrayList<String> problems = new ArrayList<>();
        applyKeys(doc, n, op, problems);
        if (op.has("children") && op.get("children").isJsonArray()) {
            JsonArray kids = op.getAsJsonArray("children");
            for (JsonElement ke : kids) {
                if (!ke.isJsonObject()) continue;
                JsonObject kid = ke.getAsJsonObject().deepCopy();
                kid.addProperty("parent", n.id);
                if (!kid.has("op")) kid.addProperty("op", "add");
                String e = opAdd(doc, kid, notes);
                if (e != null) problems.add(e);
            }
        }
        if (notes != null) {
            notes.add("added " + n.type + " '" + n.id + "' to '" + parent.id + "'");
            for (String p : problems) notes.add("warning: " + p);
        }
        return null;
    }

    private static String opSet(UIDocument doc, JsonObject op, List<String> notes) {
        String id = str(op, "id");
        UINode n = doc.find(id);
        if (n == null) return "no node '" + id + "'. Ids: " + idList(doc);
        ArrayList<String> problems = new ArrayList<>();
        applyKeys(doc, n, op, problems);
        if (!problems.isEmpty()) return String.join("; ", problems);
        return null;
    }

    private static final String[] RESERVED = {"op", "action", "parent", "index", "type", "children", "props", "values", "set"};

    private static boolean reserved(String k) {
        for (String r : RESERVED) if (r.equals(k)) return true;
        return false;
    }

    /** Applies props (object) and loose keys of the operation onto the node using the registry. "id" is the target, "rename" changes it. */
    private static void applyKeys(UIDocument doc, UINode n, JsonObject op, List<String> problems) {
        JsonObject all = new JsonObject();
        for (String k : new String[]{"props", "values", "set"})
            if (op.has(k) && op.get(k).isJsonObject()) for (Map.Entry<String, JsonElement> e : op.getAsJsonObject(k).entrySet()) all.add(e.getKey(), e.getValue());
        for (Map.Entry<String, JsonElement> e : op.entrySet())
            if (!reserved(e.getKey()) && !e.getKey().equals("id") && !e.getKey().equals("rename")) all.add(e.getKey(), e.getValue());
        if (op.has("rename")) all.addProperty("id", op.get("rename").getAsString());
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            String key = e.getKey();
            JsonElement v = e.getValue();
            String text = v.isJsonNull() ? "" : v.isJsonPrimitive() ? v.getAsString() : v.toString();
            if (key.equals("id")) {
                String fin = rename(doc, n, text);
                if (!fin.equals(UIDocument.cleanId(text)) && problems != null) problems.add("id '" + text + "' was changed to '" + fin + "'");
                continue;
            }
            String err = UIWidgetRegistry.set(n, key, text);
            if (err != null && problems != null) problems.add(err);
        }
    }

    static String str(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return "";
        JsonElement e = o.get(k);
        return e.isJsonPrimitive() ? e.getAsString().trim() : "";
    }

    static String idList(UIDocument d) {
        StringBuilder sb = new StringBuilder();
        for (UINode n : d.allNodes()) {
            if (sb.length() > 200) {
                sb.append(", ...");
                break;
            }
            sb.append(sb.length() == 0 ? "" : ", ").append(n.id);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ theme

    /** Applies {preset, overrides:{role:"#hex", number...}, font}; returns an error text or null. */
    public static String applyTheme(UIDocument doc, JsonObject t) {
        if (t == null) return null;
        String preset = str(t, "preset");
        if (!preset.isEmpty()) {
            boolean ok = false;
            for (String p : UITheme.PRESETS) if (p.equalsIgnoreCase(preset)) {
                doc.theme.preset = p;
                ok = true;
            }
            if (!ok) return "unknown theme preset '" + preset + "'. Presets: " + String.join(", ", UITheme.PRESETS);
        }
        JsonObject ov = t.has("overrides") && t.get("overrides").isJsonObject() ? t.getAsJsonObject("overrides") : null;
        if (ov == null) ov = t.has("colors") && t.get("colors").isJsonObject() ? t.getAsJsonObject("colors") : null;
        if (ov != null) {
            for (Map.Entry<String, JsonElement> e : ov.entrySet()) {
                JsonElement v = e.getValue();
                if (!v.isJsonPrimitive()) continue;
                JsonPrimitive p = v.getAsJsonPrimitive();
                String key = e.getKey();
                boolean role = false;
                for (String r : UITheme.ROLES) if (r.equals(key)) role = true;
                if (role) {
                    String hex = p.getAsString();
                    if (!hex.matches("#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return "theme color '" + key + "' must be #rrggbb or #rrggbbaa";
                    doc.theme.colors.put(key, hex.startsWith("#") ? hex : "#" + hex);
                } else if (p.isNumber()) doc.theme.numbers.put(key, p.getAsFloat());
                else return "unknown theme override '" + key + "'. Colors: " + String.join(", ", UITheme.ROLES) + "; numbers: radius, borderWidth, shadow, fontSize ...";
            }
        }
        if (t.has("font") && t.get("font").isJsonPrimitive()) doc.theme.font = t.get("font").getAsString();
        return null;
    }
}
