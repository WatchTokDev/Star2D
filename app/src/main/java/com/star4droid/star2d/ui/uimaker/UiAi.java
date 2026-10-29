package com.star4droid.star2d.ui.uimaker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UILayout;
import com.star4droid.template.ui.UINode;
import com.star4droid.template.ui.UITheme;
import com.star4droid.template.ui.UIWidgetRegistry;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The logic of the `ui` AI tool (connector and in-app assistant): list / describe / get / create / set / check / event on the
 * {@code UI/*.ui} files of a project. Pure Java (no libGDX, no android.*) so it can be tested offline; the remote layer only finds the project.
 *
 * Every property goes through {@link UIWidgetRegistry}, the same validation the UI Maker uses.
 */
public final class UiAi {
    private UiAi() {}

    /** A failure with a message meant for the AI. */
    public static final class Error extends RuntimeException {
        public Error(String m) {
            super(m);
        }
    }

    /** @param defaultOrientation "portrait" | "landscape" (project orientation) used when create gets no size */
    public static String run(String projectPath, JsonObject a, String defaultOrientation) {
        String action = s(a, "action");
        if (action.isEmpty()) action = s(a, "name").isEmpty() ? "list" : "get";
        switch (action) {
            case "list": return list(projectPath);
            case "describe": return describe(a);
            case "get": return get(projectPath, a);
            case "create": return create(projectPath, a, defaultOrientation);
            case "set": return set(projectPath, a);
            case "check": return check(projectPath, a);
            case "event": return event(projectPath, a);
            case "delete": return delete(projectPath, a);
            default: throw new Error("Unknown action '" + action + "'. Use list | describe | get | create | set | check | event | delete.");
        }
    }

    // ------------------------------------------------------------------ helpers

    static String s(JsonObject a, String k) {
        if (a == null || !a.has(k) || a.get(k).isJsonNull()) return "";
        JsonElement e = a.get(k);
        return e.isJsonPrimitive() ? e.getAsString().trim() : e.toString();
    }

    private static boolean bool(JsonObject a, String k, boolean def) {
        try {
            return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static String nameOf(JsonObject a) {
        String n = s(a, "name").replaceAll("(?i)\\.ui$", "");
        if (n.isEmpty()) throw new Error("name is required (the UI file name without .ui, letters/digits/_, e.g. hud)");
        for (char c : n.toCharArray())
            if (!(Character.isLetterOrDigit(c) && c < 128) && c != '_') throw new Error("name '" + n + "' must use letters, digits and _ only");
        return n;
    }

    private static File fileOf(String project, String name) {
        return new File(UiMakerLauncherPath.dir(project), name + ".ui");
    }

    /** Tiny stand-in so this class stays free of android classes (UiMakerLauncher imports android.content.Context). */
    static final class UiMakerLauncherPath {
        static File dir(String project) {
            return new File(project, "UI");
        }
    }

    private static UIDocument load(String project, String name) {
        File f = fileOf(project, name);
        if (!f.isFile()) {
            ArrayList<String> have = names(project);
            throw new Error("No UI named '" + name + "'. Existing: " + (have.isEmpty() ? "none (use ui create)" : have));
        }
        UIDocument d = UIDocument.read(f);
        d.name = name;
        return d;
    }

    private static void write(String project, String name, UIDocument d) {
        try {
            d.name = name;
            d.write(fileOf(project, name));
        } catch (Exception e) {
            throw new Error("Could not write UI/" + name + ".ui: " + e.getMessage());
        }
    }

    static ArrayList<String> names(String project) {
        ArrayList<String> out = new ArrayList<>();
        File[] fs = new File(project, "UI").listFiles();
        if (fs != null) for (File f : fs) if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".ui")) out.add(f.getName().substring(0, f.getName().length() - 3));
        java.util.Collections.sort(out);
        return out;
    }

    // ------------------------------------------------------------------ list / describe

    static String list(String project) {
        JsonObject o = new JsonObject();
        JsonArray screens = new JsonArray();
        for (String n : names(project)) {
            JsonObject sj = new JsonObject();
            sj.addProperty("name", n);
            try {
                UIDocument d = UIDocument.read(fileOf(project, n));
                sj.addProperty("nodes", d.root.countNodes());
                sj.addProperty("theme", d.theme.preset);
                sj.addProperty("design", (int) d.designWidth + "x" + (int) d.designHeight);
            } catch (Throwable t) {
                sj.addProperty("error", String.valueOf(t.getMessage()));
            }
            screens.add(sj);
        }
        o.add("screens", screens);
        JsonArray tpl = new JsonArray();
        for (String t : UiTemplates.names()) {
            JsonObject tj = new JsonObject();
            tj.addProperty("id", t);
            tj.addProperty("what", UiTemplates.INFO.get(t)[1]);
            tpl.add(tj);
        }
        o.add("templates", tpl);
        JsonArray types = new JsonArray();
        for (UIWidgetRegistry.Widget w : UIWidgetRegistry.all()) {
            types.add(w.type + (w.container ? " (container)" : "") + ": " + w.desc);
        }
        o.add("widgets", types);
        o.add("themes", toArray(UITheme.PRESETS));
        o.addProperty("how", "ui create {name, template?, theme?:{preset}, tree?} makes UI/<name>.ui; ui get {name} shows the tree; ui set {name, ops:[{op:'add',parent,type,id,props}|{op:'set',id,props}|{op:'remove',id}|{op:'move',id,parent,index}]}; "
                + "ui describe {type} lists the properties; ui check {name} finds overlaps / off-screen / small touch targets in portrait AND landscape. Show it in a scene with an UIHOLDER element (ui:'<name>'). read_docs ui.");
        return o.toString();
    }

    private static JsonArray toArray(String[] l) {
        JsonArray a = new JsonArray();
        for (String s : l) a.add(s);
        return a;
    }

    static String describe(JsonObject a) {
        String type = s(a, "type");
        if (type.isEmpty()) {
            JsonObject o = new JsonObject();
            JsonArray types = new JsonArray();
            for (UIWidgetRegistry.Widget w : UIWidgetRegistry.all()) {
                JsonObject t = new JsonObject();
                t.addProperty("type", w.type);
                t.addProperty("category", w.category);
                t.addProperty("container", w.container);
                t.addProperty("what", w.desc);
                t.add("events", toArray(w.events));
                if (w.styles.length > 0) t.add("styles", toArray(w.styles));
                StringBuilder k = new StringBuilder();
                for (UIWidgetRegistry.Prop p : w.props) k.append(k.length() == 0 ? "" : ",").append(p.key);
                t.addProperty("props", k.toString());
                types.add(t);
            }
            o.add("types", types);
            StringBuilder common = new StringBuilder();
            for (UIWidgetRegistry.Prop p : UIWidgetRegistry.commonProps()) common.append(common.length() == 0 ? "" : ",").append(p.key);
            o.addProperty("common_props", common.toString());
            o.addProperty("next", "ui describe {type:'Slider'} = every property with kind, default, range and options.");
            return o.toString();
        }
        UIWidgetRegistry.Widget w = UIWidgetRegistry.get(type);
        if (w == null) throw new Error("Unknown type '" + type + "'. Types: " + String.join(", ", UINode.TYPES));
        JsonObject o = new JsonObject();
        o.addProperty("type", w.type);
        o.addProperty("what", w.desc);
        o.addProperty("container", w.container);
        o.add("events", toArray(w.events));
        if (w.styles.length > 0) o.add("styles", toArray(w.styles));
        JsonArray props = new JsonArray();
        for (UIWidgetRegistry.Prop p : UIWidgetRegistry.propsFor(w.type)) {
            StringBuilder sb = new StringBuilder();
            sb.append(p.key).append(" [").append(p.kind.name().toLowerCase(Locale.ROOT)).append("]");
            if (!p.def.isEmpty()) sb.append(" default ").append(p.def);
            if (!Float.isNaN(p.min) || !Float.isNaN(p.max)) sb.append(" range ").append(Float.isNaN(p.min) ? "" : fmt(p.min)).append("..").append(Float.isNaN(p.max) ? "" : fmt(p.max));
            if (p.options.length > 0) sb.append(" one of ").append(String.join("|", p.options));
            if (p.desc != null && !p.desc.isEmpty()) sb.append(" - ").append(p.desc);
            props.add(sb.toString());
        }
        o.add("props", props);
        return o.toString();
    }

    private static String fmt(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.valueOf(v);
    }

    // ------------------------------------------------------------------ outline

    /** One line per node, indented: id Type, then only what differs from the defaults. */
    public static String outline(UIDocument d) {
        StringBuilder sb = new StringBuilder();
        outline(d.root, 0, sb);
        return sb.toString().trim();
    }

    private static void outline(UINode n, int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(n.id).append(' ').append(n.type);
        if (n.anchor != null && !n.anchor.equals("topleft")) sb.append(" anchor=").append(n.anchor);
        if (n.ml != 0 || n.mt != 0 || n.mr != 0 || n.mb != 0) sb.append(" margin=").append(UIWidgetRegistry.get(n, "margin"));
        if (n.x != 0) sb.append(" x=").append(fmt(n.x));
        if (n.y != 0) sb.append(" y=").append(fmt(n.y));
        if (n.w != null && !n.w.isEmpty()) sb.append(" w=").append(n.w);
        if (n.h != null && !n.h.isEmpty()) sb.append(" h=").append(n.h);
        if (n.weight != 0) sb.append(" weight=").append(fmt(n.weight));
        if (!n.visible) sb.append(" visible=false");
        if (!n.enabled) sb.append(" enabled=false");
        if (n.alpha != 1f) sb.append(" alpha=").append(fmt(n.alpha));
        if (n.touchable != null && !n.touchable.isEmpty()) sb.append(" touchable=").append(n.touchable);
        for (Map.Entry<String, JsonElement> e : n.propMap().entrySet()) {
            JsonElement v = e.getValue();
            String t = v.isJsonPrimitive() ? v.getAsString() : v.toString();
            if (t.length() > 40) t = t.substring(0, 39) + "...";
            boolean quote = v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && (e.getKey().equals("text") || e.getKey().equals("hint"));
            sb.append(' ').append(e.getKey()).append('=').append(quote ? "\"" + t.replace('\n', ' ') + "\"" : t);
        }
        sb.append('\n');
        for (UINode c : n.children) outline(c, depth + 1, sb);
    }

    /** id -> "x,y wxh" lines for the nodes at the given screen size (y down from the top, like the Maker shows it). */
    static JsonObject rectsJson(UIDocument d, float w, float h) {
        UiRects r = UiRects.compute(d, w, h, UILayout.APPROX);
        JsonObject o = new JsonObject();
        for (UiRects.R x : r.rects.values()) if (x.shown && x.node.parent != null) o.addProperty(x.node.id, UiLint.rectText(x));
        return o;
    }

    private static List<String> warnings(UIDocument d, float w, float h) {
        float lo = Math.min(w, h), hi = Math.max(w, h);
        ArrayList<String> out = new ArrayList<>(d.validate());
        out.addAll(UiLint.checkBoth(d, hi, lo, UILayout.APPROX));
        return out;
    }

    private static JsonArray strings(List<String> l) {
        JsonArray a = new JsonArray();
        int max = 12;
        for (String s : l) {
            if (a.size() >= max) {
                a.add("... " + (l.size() - max) + " more");
                break;
            }
            a.add(s);
        }
        return a;
    }

    // ------------------------------------------------------------------ get / check

    static String get(String project, JsonObject a) {
        String name = nameOf(a);
        UIDocument d = load(project, name);
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("design", (int) d.designWidth + "x" + (int) d.designHeight);
        o.add("theme", d.theme.toJson());
        o.addProperty("outline", outline(d));
        if (bool(a, "json", false)) o.add("json", d.toJson());
        if (a.has("size") && a.get("size").isJsonArray() && a.getAsJsonArray("size").size() >= 2) {
            JsonArray sz = a.getAsJsonArray("size");
            o.add("rects", rectsJson(d, sz.get(0).getAsFloat(), sz.get(1).getAsFloat()));
            o.addProperty("rects_note", "x,y wxh in px from the top-left of the " + sz.get(0).getAsInt() + "x" + sz.get(1).getAsInt() + " screen (text sizes approximate)");
        }
        List<String> w = warnings(d, d.designWidth, d.designHeight);
        if (!w.isEmpty()) o.add("warnings", strings(w));
        return o.toString();
    }

    static String check(String project, JsonObject a) {
        String name = nameOf(a);
        UIDocument d = load(project, name);
        float w = d.designWidth, h = d.designHeight;
        if (a.has("size") && a.get("size").isJsonArray() && a.getAsJsonArray("size").size() >= 2) {
            w = a.getAsJsonArray("size").get(0).getAsFloat();
            h = a.getAsJsonArray("size").get(1).getAsFloat();
        }
        List<String> ws = warnings(d, w, h);
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("ok", ws.isEmpty());
        o.addProperty("checked", "portrait and landscape of " + (int) Math.max(w, h) + "x" + (int) Math.min(w, h) + " (text widths approximate)");
        if (!ws.isEmpty()) o.add("warnings", strings(ws));
        return o.toString();
    }

    // ------------------------------------------------------------------ create

    static String create(String project, JsonObject a, String defaultOrientation) {
        String name = nameOf(a);
        File f = fileOf(project, name);
        if (f.exists() && !bool(a, "replace", false)) throw new Error("UI/" + name + ".ui already exists. Edit it with ui set, or pass replace:true to start again.");
        float w = 1280, h = 720;
        if ("portrait".equalsIgnoreCase(defaultOrientation)) {
            w = 720;
            h = 1280;
        }
        String ori = s(a, "orientation").toLowerCase(Locale.ROOT);
        if (ori.equals("portrait")) {
            w = 720;
            h = 1280;
        } else if (ori.equals("landscape")) {
            w = 1280;
            h = 720;
        }
        if (a.has("design") && a.get("design").isJsonArray() && a.getAsJsonArray("design").size() >= 2) {
            w = a.getAsJsonArray("design").get(0).getAsFloat();
            h = a.getAsJsonArray("design").get(1).getAsFloat();
        }
        if (w < 100 || h < 100) throw new Error("design size too small");
        UIDocument d = UiOps.newDocument(name, w, h);
        ArrayList<String> notes = new ArrayList<>();
        JsonObject theme = a.has("theme") && a.get("theme").isJsonObject() ? a.getAsJsonObject("theme") : null;
        if (theme == null && !s(a, "theme").isEmpty()) {
            theme = new JsonObject();
            theme.addProperty("preset", s(a, "theme"));
        }
        String err = UiOps.applyTheme(d, theme);
        if (err != null) throw new Error(err);

        // templates
        ArrayList<String> tpls = new ArrayList<>();
        if (!s(a, "template").isEmpty()) tpls.add(s(a, "template"));
        if (a.has("templates") && a.get("templates").isJsonArray()) for (JsonElement e : a.getAsJsonArray("templates")) tpls.add(e.getAsString());
        for (String t : tpls) {
            if (UiTemplates.insert(d, d.root, t) == null) throw new Error("Unknown template '" + t + "'. Templates: " + UiTemplates.names());
            notes.add("template " + t + " added");
        }
        // explicit tree
        if (a.has("tree") && !a.get("tree").isJsonNull()) {
            JsonElement t = a.get("tree");
            if (t.isJsonPrimitive()) {
                try {
                    t = JsonParser.parseString(t.getAsString());
                } catch (Exception e) {
                    throw new Error("tree is not valid JSON: " + e.getMessage());
                }
            }
            if (t.isJsonArray()) {
                for (JsonElement e : t.getAsJsonArray()) if (e.isJsonObject()) addTree(d, e.getAsJsonObject(), notes);
            } else if (t.isJsonObject()) {
                JsonObject to = t.getAsJsonObject();
                if (to.has("root") && to.get("root").isJsonObject()) to = to.getAsJsonObject("root");
                boolean isRoot = "root".equals(s(to, "id")) && (to.has("children") || !to.has("type"));
                if (isRoot) {
                    UINode r = UINode.fromJson(to);
                    for (UINode c : new ArrayList<>(r.children)) {
                        c.parent = null;
                        d.root.addChild(c);
                    }
                    notes.addAll(UiOps.sanitize(d.root));
                } else addTree(d, to, notes);
            }
        }
        if (tpls.isEmpty() && !a.has("tree")) notes.add("empty UI: add elements with ui set");
        notes.addAll(d.validate());
        write(project, name, d);
        return summary(project, name, d, notes, true);
    }

    private static void addTree(UIDocument d, JsonObject o, List<String> notes) {
        UINode n = UINode.fromJson(o);
        if (UINode.canonicalType(n.type) == null) throw new Error("tree node '" + n.id + "' has unknown type '" + n.type + "'. Types: " + String.join(", ", UINode.TYPES));
        d.makeIdsUnique(n);
        d.root.addChild(n);
        notes.addAll(UiOps.sanitize(n));
    }

    private static String summary(String project, String name, UIDocument d, List<String> notes, boolean withOutline) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("name", name);
        o.addProperty("file", "UI/" + name + ".ui");
        o.addProperty("nodes", d.root.countNodes());
        o.addProperty("design", (int) d.designWidth + "x" + (int) d.designHeight);
        if (withOutline) o.addProperty("outline", outline(d));
        if (!notes.isEmpty()) o.add("notes", strings(notes));
        List<String> w = warnings(d, d.designWidth, d.designHeight);
        if (!w.isEmpty()) o.add("warnings", strings(w));
        o.addProperty("next", "Show it: elements add {type:'UIHOLDER', name:'" + name + "_ui', ui:'" + name + "'} sized to the frame (x:0, y:0, width/height = status.frame), then run_game / screenshot. "
                + "Button events: ui event {scene, holder, node, event:'onClick', code}.");
        return o.toString();
    }

    // ------------------------------------------------------------------ set

    static String set(String project, JsonObject a) {
        String name = nameOf(a);
        UIDocument d = load(project, name);
        JsonArray ops = a.has("ops") && a.get("ops").isJsonArray() ? a.getAsJsonArray("ops") : null;
        JsonObject theme = a.has("theme") && a.get("theme").isJsonObject() ? a.getAsJsonObject("theme") : null;
        if (theme == null && !s(a, "theme").isEmpty()) {
            theme = new JsonObject();
            theme.addProperty("preset", s(a, "theme"));
        }
        if (ops == null && theme == null && !a.has("design")) throw new Error("ui set needs ops:[{op:'add'|'set'|'remove'|'move'|'duplicate'|'wrap', ...}] and/or theme:{preset, overrides}. Call ui describe {type} for the properties.");
        ArrayList<String> notes = new ArrayList<>();
        JsonArray errors = new JsonArray();
        int applied = 0;
        if (theme != null) {
            String err = UiOps.applyTheme(d, theme);
            if (err != null) errors.add("theme: " + err);
            else {
                applied++;
                notes.add("theme updated");
            }
        }
        if (a.has("design") && a.get("design").isJsonArray() && a.getAsJsonArray("design").size() >= 2) {
            d.designWidth = a.getAsJsonArray("design").get(0).getAsFloat();
            d.designHeight = a.getAsJsonArray("design").get(1).getAsFloat();
            applied++;
        }
        if (ops != null) {
            for (int i = 0; i < ops.size(); i++) {
                if (!ops.get(i).isJsonObject()) {
                    errors.add("op " + i + ": not an object");
                    continue;
                }
                String err;
                try {
                    err = UiOps.apply(d, ops.get(i).getAsJsonObject(), notes);
                } catch (RuntimeException e) {
                    err = String.valueOf(e.getMessage());
                }
                if (err != null) errors.add("op " + i + ": " + err);
                else applied++;
            }
        }
        List<String> v = d.validate();
        notes.addAll(v);
        if (applied > 0) write(project, name, d);
        JsonObject o = new JsonObject();
        o.addProperty("ok", errors.size() == 0);
        o.addProperty("name", name);
        o.addProperty("applied", applied);
        if (errors.size() > 0) {
            o.add("errors", errors);
            o.addProperty("note", "Operations without an error were applied and saved; fix and resend only the failed ones.");
        }
        if (!notes.isEmpty()) o.add("notes", strings(notes));
        o.addProperty("nodes", d.root.countNodes());
        List<String> w = warnings(d, d.designWidth, d.designHeight);
        if (!w.isEmpty()) o.add("warnings", strings(w));
        if (bool(a, "outline", false)) o.addProperty("outline", outline(d));
        return o.toString();
    }

    // ------------------------------------------------------------------ delete / event

    static String delete(String project, JsonObject a) {
        String name = nameOf(a);
        File f = fileOf(project, name);
        if (!f.isFile()) throw new Error("No UI named '" + name + "'.");
        if (!bool(a, "confirm", false)) throw new Error("Deleting UI/" + name + ".ui cannot be undone: pass confirm:true.");
        if (!f.delete()) throw new Error("Could not delete " + f);
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("deleted", "UI/" + name + ".ui");
        return o.toString();
    }

    /** Writes the Java code of an inner event: Events/&lt;scene&gt;/private/&lt;holder&gt;/ui/&lt;node&gt;/&lt;event&gt;.java */
    static String event(String project, JsonObject a) {
        String scene = s(a, "scene"), holder = s(a, "holder"), node = s(a, "node"), ev = s(a, "event");
        if (scene.isEmpty() || holder.isEmpty() || node.isEmpty() || ev.isEmpty())
            throw new Error("ui event needs scene (the scene that has the UIHOLDER), holder (the UIHOLDER element name), node (id inside the .ui) and event (onClick|onTouchStart|onTouchEnd|onChange|onSubmit|onScroll).");
        String[] events = {"onClick", "onTouchStart", "onTouchEnd", "onChange", "onSubmit", "onScroll"};
        if (!Arrays.asList(events).contains(ev)) throw new Error("Unknown event '" + ev + "'. Events: " + String.join(", ", events));
        String ui = s(a, "ui");
        if (!ui.isEmpty()) {
            UIDocument d = load(project, ui.replaceAll("(?i)\\.ui$", ""));
            UINode n = d.find(node);
            if (n == null) throw new Error("UI '" + ui + "' has no node '" + node + "'. Ids: " + UiOps.idList(d));
            if (!Arrays.asList(UIWidgetRegistry.eventsOf(n)).contains(ev))
                throw new Error(n.type + " '" + node + "' has no " + ev + ". Its events: " + String.join(", ", UIWidgetRegistry.eventsOf(n)));
        }
        for (String part : new String[]{scene, holder, node}) {
            for (char c : part.toCharArray()) if (!(Character.isLetterOrDigit(c) || c == '_')) throw new Error("'" + part + "': letters, digits and _ only");
        }
        File base = new File(project, "Events/" + scene + "/private/" + holder + "/ui/" + node);
        File java = new File(base, ev + ".java");
        if (bool(a, "read", false) || !a.has("code")) {
            JsonObject o = new JsonObject();
            o.addProperty("event", node + "." + ev);
            try {
                o.addProperty("java", java.isFile() ? new String(Files.readAllBytes(java.toPath()), StandardCharsets.UTF_8) : "");
            } catch (Exception e) {
                o.addProperty("java", "");
            }
            return o.toString();
        }
        String code = a.get("code").getAsString();
        try {
            base.mkdirs();
            Files.write(java.toPath(), code.getBytes(StandardCharsets.UTF_8));
            File vis = new File(base, ev + ".visual");
            boolean hadBlocks = vis.exists() && vis.length() > 2;
            if (vis.exists()) vis.delete();
            JsonObject o = new JsonObject();
            o.addProperty("ok", true);
            o.addProperty("written", "Events/" + scene + "/private/" + holder + "/ui/" + node + "/" + ev + ".java");
            if (hadBlocks) o.addProperty("note", "The block version of this event was replaced by the Java code.");
            o.addProperty("variables", "ui (UIView), id" + (ev.equals("onChange") ? ", value (Object: Float for Slider, Boolean for toggles, String for TextField)" : ev.equals("onSubmit") ? ", text" : ev.equals("onScroll") ? ", x, y" : "") + ". Plain statements, no method wrapper.");
            return o.toString();
        } catch (Exception e) {
            throw new Error("Could not write the event: " + e.getMessage());
        }
    }
}
