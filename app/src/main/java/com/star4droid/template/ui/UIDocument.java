package com.star4droid.template.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * A whole .ui file: header, theme and the node tree. Pure Java (no libGDX, no android.*), see docs/UI.md.
 *
 * <pre>
 * {"version":1,"name":"hud","designWidth":1280,"designHeight":720,
 *  "theme":{"preset":"jelly"},
 *  "root":{"id":"root","type":"Panel","children":[...]}}
 * </pre>
 */
public class UIDocument {
    public static final int VERSION = 1;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String name = "ui";
    public float designWidth = 1280, designHeight = 720;
    public UITheme theme = new UITheme();
    public UINode root;
    /** Last problems found while reading (never null). */
    public final ArrayList<String> warnings = new ArrayList<>();

    public UIDocument() {
        root = UINode.create(UINode.PANEL, "root");
        root.anchor = "stretch";
        root.props.remove("radius");
    }

    /** A document with an empty transparent root. */
    public static UIDocument empty(String name, float w, float h) {
        UIDocument d = new UIDocument();
        d.name = name;
        d.designWidth = w;
        d.designHeight = h;
        return d;
    }

    // ------------------------------------------------------------------ read / write

    public static UIDocument parse(String json) {
        UIDocument d = new UIDocument();
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            d.name = UINode.str(o, "name", "ui");
            d.designWidth = UINode.number(o, "designWidth", 1280);
            d.designHeight = UINode.number(o, "designHeight", 720);
            if (d.designWidth <= 0) d.designWidth = 1280;
            if (d.designHeight <= 0) d.designHeight = 720;
            if (o.has("theme") && o.get("theme").isJsonObject()) d.theme = UITheme.fromJson(o.getAsJsonObject("theme"));
            else if (o.has("theme") && o.get("theme").isJsonPrimitive()) {
                d.theme = new UITheme();
                d.theme.preset = o.get("theme").getAsString();
            }
            if (o.has("root") && o.get("root").isJsonObject()) d.root = UINode.fromJson(o.getAsJsonObject("root"));
            else d.warnings.add("no root node");
            if (d.root == null) {
                d.root = new UINode(UINode.PANEL, "root");
                d.root.anchor = "stretch";
            }
            if (d.root.id == null || d.root.id.isEmpty()) d.root.id = "root";
            d.validate();
        } catch (Exception e) {
            d.warnings.add("cannot read ui: " + e.getMessage());
        }
        return d;
    }

    public static UIDocument read(File f) {
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            UIDocument d = parse(sb.toString());
            if (d.name == null || d.name.equals("ui")) {
                String fn = f.getName();
                d.name = fn.endsWith(".ui") ? fn.substring(0, fn.length() - 3) : fn;
            }
            return d;
        } catch (Exception e) {
            UIDocument d = new UIDocument();
            d.warnings.add("cannot read " + f + ": " + e.getMessage());
            return d;
        }
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("version", VERSION);
        o.addProperty("name", name);
        o.addProperty("designWidth", designWidth);
        o.addProperty("designHeight", designHeight);
        o.add("theme", theme.toJson());
        o.add("root", root.toJson());
        return o;
    }

    public String toJsonString() {
        return PRETTY.toJson(toJson());
    }

    public void write(File f) throws java.io.IOException {
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            w.write(toJsonString());
        }
        if (f.exists() && !f.delete()) throw new java.io.IOException("cannot replace " + f);
        if (!tmp.renameTo(f)) throw new java.io.IOException("cannot write " + f);
    }

    // ------------------------------------------------------------------ ids / lookup

    public UINode find(String id) {
        return root.find(id);
    }

    /** A free id starting with {@code base} ("button" -> "button", "button2", ...). */
    public String uniqueId(String base) {
        if (base == null || base.isEmpty()) base = "node";
        base = cleanId(base);
        HashSet<String> used = ids();
        if (!used.contains(base)) return base;
        int i = 2;
        while (used.contains(base + i)) i++;
        return base + i;
    }

    public HashSet<String> ids() {
        final HashSet<String> used = new HashSet<>();
        root.walk(new UINode.Visitor() {
            @Override
            public void visit(UINode n) {
                used.add(n.id);
            }
        });
        return used;
    }

    /** Ids are used in generated Java ("case \"id\"") and file names: letters, digits and _ only. */
    public static String cleanId(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) b.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        if (b.length() == 0) b.append("node");
        if (Character.isDigit(b.charAt(0))) b.insert(0, '_');
        return b.toString();
    }

    /** Renames the nodes of {@code sub} (and descendants) that clash with ids of this document. */
    public void makeIdsUnique(UINode sub) {
        final HashSet<String> used = ids();
        // ids of nodes already inside the document are fixed; sub may be part of it or not
        final boolean inside = isInside(sub);
        final HashSet<String> mine = new HashSet<>();
        sub.walk(new UINode.Visitor() {
            @Override
            public void visit(UINode n) {
                mine.add(n.id);
            }
        });
        if (inside) used.removeAll(mine);
        final HashSet<String> taken = used;
        sub.walk(new UINode.Visitor() {
            @Override
            public void visit(UINode n) {
                String base = cleanId(n.id);
                String id = base;
                int i = 2;
                while (taken.contains(id)) id = base + (i++);
                taken.add(id);
                n.id = id;
            }
        });
    }

    private boolean isInside(UINode n) {
        for (UINode p = n; p != null; p = p.parent) if (p == root) return true;
        return false;
    }

    // ------------------------------------------------------------------ editing helpers (used by the UI Maker and the AI tool)

    /** Creates a node with defaults and adds it to {@code parentId} (root when null). Returns the node or throws IllegalArgumentException. */
    public UINode add(String parentId, String type, String idBase, int index) {
        String t = UINode.canonicalType(type);
        if (t == null) throw new IllegalArgumentException("unknown type '" + type + "'. Types: " + String.join(", ", UINode.TYPES));
        UINode p = parentId == null || parentId.isEmpty() ? root : find(parentId);
        if (p == null) throw new IllegalArgumentException("no node '" + parentId + "'");
        if (!p.isContainer()) throw new IllegalArgumentException(p.type + " '" + p.id + "' cannot hold children");
        UINode n = UINode.create(t, uniqueId(idBase == null || idBase.isEmpty() ? t.toLowerCase() : idBase));
        p.addChild(index, n);
        return n;
    }

    /** Removes a node (never the root). Returns false when not found. */
    public boolean remove(String id) {
        UINode n = find(id);
        if (n == null || n == root) return false;
        n.removeFromParent();
        return true;
    }

    /** Copies a node next to the original; returns the copy. */
    public UINode duplicate(String id) {
        UINode n = find(id);
        if (n == null || n == root) return null;
        UINode c = n.copyDeep();
        UINode p = n.parent;
        int i = n.indexInParent() + 1;
        makeIdsUnique(c); // before adding: the copy is not part of the document yet, so every id of the document counts as taken
        p.addChild(i, c);
        return c;
    }

    // ------------------------------------------------------------------ validation

    /** Finds problems that make a UI misbehave; text is meant for a person/AI. Also fills {@link #warnings}. */
    public List<String> validate() {
        final ArrayList<String> out = new ArrayList<>();
        final HashSet<String> seen = new HashSet<>();
        final HashSet<String> groups = new HashSet<>();
        root.walk(new UINode.Visitor() {
            @Override
            public void visit(UINode n) {
                if (n.id == null || n.id.isEmpty()) {
                    n.id = uniqueIdAmong(seen, n.type.toLowerCase());
                    out.add("a " + n.type + " had no id, it is now '" + n.id + "'");
                } else if (!seen.add(n.id)) {
                    String old = n.id;
                    n.id = uniqueIdAmong(seen, old);
                    out.add("duplicate id '" + old + "' renamed to '" + n.id + "'");
                }
                if (!UINode.isKnownType(n.type)) out.add("node '" + n.id + "' has unknown type '" + n.type + "'");
                if (!n.isContainer() && !n.children.isEmpty()) out.add(n.type + " '" + n.id + "' cannot hold children, they are ignored");
                if (n.type.equals(UINode.RADIO)) groups.add(n.str("group", "group1"));
                if (n.type.equals(UINode.SCROLL) && n.children.size() > 1)
                    out.add("ScrollView '" + n.id + "' has " + n.children.size() + " children: put them in ONE Linear/Panel child (the scrolled content)");
                if (n.type.equals(UINode.SCROLL) && n.height().kind == UINode.WRAP && n.str("direction", "vertical").equals("vertical"))
                    out.add("ScrollView '" + n.id + "' has height wrap: a vertical ScrollView needs a fixed or fill height");
            }
        });
        warnings.addAll(out);
        return out;
    }

    private static String uniqueIdAmong(HashSet<String> seen, String base) {
        base = cleanId(base);
        String id = base;
        int i = 2;
        while (seen.contains(id)) id = base + (i++);
        seen.add(id);
        return id;
    }

    /** Ids of every node (parents first) - handy for pickers and the events list. */
    public List<UINode> allNodes() {
        final ArrayList<UINode> l = new ArrayList<>();
        root.walk(new UINode.Visitor() {
            @Override
            public void visit(UINode n) {
                l.add(n);
            }
        });
        return l;
    }
}
