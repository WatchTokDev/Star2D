package com.star4droid.star2d.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code templates} tool: ready-made games built by the SAME tools the AI uses (create_image, create_sound, variables, elements, events),
 * so everything stays editable (blocks, normal elements). The generators are pure Java in {@link TemplateRegistry}; this class only
 * picks / creates the scene, reads its real size and runs the generated steps.
 *
 * <p><b>Threading:</b> every method here must run on a WORKER thread, never on the GL thread: the tools it calls post work to the GL
 * thread and wait for it ({@code Gl.run}), which deadlocks when the caller IS the GL thread.
 */
public final class Templates {
    private Templates() {}

    // ------------------------------------------------------------------ API for the in-app assistant

    /** SHORT catalog as JSON text: id, one line, params with defaults. */
    public static String catalogJson() {
        return TemplateRegistry.catalog().toString();
    }

    /** What one template creates and which variables to tweak, as JSON text. */
    public static String infoJson(String id) {
        TemplateLib.Tpl t = TemplateRegistry.find(id);
        if (t == null) return error("unknown template '" + id + "'. Available: " + TemplateRegistry.ids()).toString();
        return infoOf(t).toString();
    }

    /**
     * Builds the template in {@code sceneOrNull} (null = the open scene when it is empty, else a new scene named after the template) and
     * returns the reply as JSON text ({"ok":true,...} or {"ok":false,"error":...}). Never throws. OFF the GL thread only.
     */
    public static String apply(String id, JsonObject params, String sceneOrNull) {
        return run(id, params, sceneOrNull, "", false, true).toString();
    }

    // ------------------------------------------------------------------ the tool (RemoteTools: case "templates")

    static ToolResult tool(JsonObject a) {
        String action = CoreTools.s(a, "action").trim().toLowerCase(Locale.ROOT);
        if (action.isEmpty()) action = a.has("id") ? "apply" : "list";
        switch (action) {
            case "list":
                return ToolResult.text(catalogJson());
            case "info": {
                String id = CoreTools.s(a, "id");
                TemplateLib.Tpl t = TemplateRegistry.find(id);
                if (t == null) return ToolResult.error("unknown template '" + id + "'. Available: " + TemplateRegistry.ids());
                return ToolResult.text(infoOf(t).toString());
            }
            case "apply": {
                JsonObject params = a.has("params") && a.get("params").isJsonObject() ? a.getAsJsonObject("params") : new JsonObject();
                JsonObject r = run(CoreTools.s(a, "id"), params, CoreTools.s(a, "scene"), CoreTools.s(a, "orientation"), CoreTools.b(a, "replace", false), false);
                boolean ok = r.has("ok") && r.get("ok").getAsBoolean();
                return ok ? ToolResult.text(r.toString()) : ToolResult.error(r.toString());
            }
            default:
                return ToolResult.error("Unknown action '" + action + "'. Use list | apply | info.");
        }
    }

    private static JsonObject infoOf(TemplateLib.Tpl t) {
        JsonObject o = new JsonObject();
        o.addProperty("id", t.id());
        o.addProperty("about", t.about());
        JsonArray ps = new JsonArray();
        for (TemplateLib.P p : t.params()) {
            JsonObject po = new JsonObject();
            po.addProperty("key", p.key);
            po.addProperty("default", p.def);
            po.addProperty("doc", p.doc);
            ps.add(po);
        }
        o.add("params", ps);
        o.addProperty("details", t.details());
        return o;
    }

    // ------------------------------------------------------------------ apply

    private static JsonObject error(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", msg);
        return o;
    }

    private static String text(ToolResult r) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : r.content) {
            JsonObject b = e.getAsJsonObject();
            if (b.has("text")) sb.append(b.get("text").getAsString()).append('\n');
        }
        return sb.toString().trim();
    }

    private static ToolResult call(boolean local, String tool, JsonObject args) {
        return local ? RemoteTools.runLocal(tool, args) : RemoteTools.run(tool, args);
    }

    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof JsonElement) o.add((String) kv[i], (JsonElement) v);
            else if (v instanceof Number) o.addProperty((String) kv[i], (Number) v);
            else if (v instanceof Boolean) o.addProperty((String) kv[i], (Boolean) v);
            else o.addProperty((String) kv[i], String.valueOf(v));
        }
        return o;
    }

    private static JsonObject parse(String s) {
        try {
            JsonElement e = JsonParser.parseString(s);
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normOrientation(String s) {
        s = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        if (s.equals("landscape") || s.equals("horizontal") || s.equals("wide")) return "landscape";
        if (s.equals("portrait") || s.equals("vertical") || s.equals("tall")) return "portrait";
        return "";
    }

    /** project_info as JSON (throws a readable error text via IllegalStateException). */
    private static JsonObject projectInfo(boolean local) {
        ToolResult r = call(local, "project_info", new JsonObject());
        JsonObject o = parse(text(r));
        if (r.isError || o == null) throw new IllegalStateException(text(r));
        return o;
    }

    private static JsonObject sceneEntry(JsonObject info, String name) {
        if (info.has("scenes") && info.get("scenes").isJsonArray())
            for (JsonElement e : info.getAsJsonArray("scenes")) {
                JsonObject s = e.getAsJsonObject();
                if (s.has("name") && s.get("name").getAsString().equals(name)) return s;
            }
        return null;
    }

    private static int count(JsonObject sceneEntry) {
        return sceneEntry != null && sceneEntry.has("elements") ? sceneEntry.get("elements").getAsInt() : 0;
    }

    /** Collects every {"ok":false,...} result of a tool answer (any nesting) as short lines. */
    private static void failures(JsonElement e, String label, List<String> out) {
        if (e == null) return;
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) failures(x, label, out);
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("ok") && o.get("ok").isJsonPrimitive() && !o.get("ok").getAsBoolean()) {
                String what = o.has("name") ? o.get("name").getAsString() : o.has("op") ? o.get("op").getAsString() : "";
                String why = o.has("error") ? o.get("error").getAsString() : o.has("info") ? o.get("info").getAsString() : "failed";
                out.add(label + (what.isEmpty() ? "" : " " + what) + ": " + clip(why, 300));
                return;
            }
            for (Map.Entry<String, JsonElement> en : o.entrySet()) if (en.getValue().isJsonArray() || en.getValue().isJsonObject()) failures(en.getValue(), label, out);
        }
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    /** "Brick1..Brick40 (40)" style summary of the created element names. */
    private static JsonArray names(TemplateKit.Plan p) {
        LinkedHashMap<String, int[]> groups = new LinkedHashMap<>(); // prefix -> {count, first, last}
        ArrayList<String> single = new ArrayList<>();
        for (TemplateKit.El e : p.elements) {
            String n = e.name;
            int i = n.length();
            while (i > 0 && Character.isDigit(n.charAt(i - 1))) i--;
            if (i == n.length() || i == 0) {
                single.add(n);
                continue;
            }
            int num = Integer.parseInt(n.substring(i).length() > 9 ? "0" : n.substring(i));
            int[] g = groups.get(n.substring(0, i));
            if (g == null) groups.put(n.substring(0, i), new int[]{1, num, num});
            else {
                g[0]++;
                g[1] = Math.min(g[1], num);
                g[2] = Math.max(g[2], num);
            }
        }
        JsonArray out = new JsonArray();
        for (String s : single) out.add(s);
        for (Map.Entry<String, int[]> g : groups.entrySet()) {
            int[] v = g.getValue();
            out.add(v[0] == 1 ? g.getKey() + v[1] : g.getKey() + v[1] + ".." + g.getKey() + v[2] + " (" + v[0] + ")");
        }
        return out;
    }

    private static JsonObject run(String id, JsonObject params, String sceneArg, String orientationArg, boolean replace, boolean local) {
        try {
            TemplateLib.Tpl tpl = TemplateRegistry.find(id);
            if (tpl == null) return error("unknown template '" + id + "'. Available: " + TemplateRegistry.ids());
            ArrayList<String> notes = new ArrayList<>();
            String wantOr = normOrientation(orientationArg);
            if (!orientationArg.isEmpty() && wantOr.isEmpty()) notes.add("orientation '" + orientationArg + "' is not portrait|landscape, ignored");
            String sceneName = sceneArg == null ? "" : sceneArg.trim();
            if (!sceneName.isEmpty() && !sceneName.matches("[A-Za-z_][A-Za-z0-9_]*")) return error("scene name '" + sceneName + "' must be letters, digits and _ only");

            // ---- which scene
            JsonObject info = projectInfo(local);
            String open = info.has("open_scene") ? info.get("open_scene").getAsString() : "";
            boolean created = false;
            if (sceneName.isEmpty()) {
                if (count(sceneEntry(info, open)) == 0 && !open.isEmpty()) sceneName = open;
                else {
                    String base = tpl.id();
                    sceneName = base;
                    for (int n = 2; sceneEntry(info, sceneName) != null; n++) sceneName = base + n;
                }
            }
            JsonObject existing = sceneEntry(info, sceneName);
            if (existing != null && count(existing) > 0) {
                if (!replace)
                    return error("scene '" + sceneName + "' already has " + count(existing) + " elements. Use replace:true to wipe it and build the template, or choose another scene name (or leave scene out to get a new one).");
                String err = wipe(sceneName, info, wantOr, local);
                if (err != null) return error(err);
                created = true;
            } else if (existing == null) {
                JsonObject a = obj("action", "create", "name", sceneName);
                if (!wantOr.isEmpty()) a.addProperty("orientation", wantOr);
                ToolResult r = call(local, "scenes", a);
                if (r.isError) return error("could not create scene '" + sceneName + "': " + text(r));
                created = true;
            } else {
                ToolResult r = call(local, "scenes", obj("action", "open", "name", sceneName));
                if (r.isError) return error("could not open scene '" + sceneName + "': " + text(r));
            }

            // ---- orientation + real frame
            info = projectInfo(local);
            String curOr = info.has("orientation") ? info.get("orientation").getAsString() : "landscape";
            if (!wantOr.isEmpty() && !wantOr.equals(curOr)) {
                call(local, "scenes", obj("action", "configure", "name", sceneName, "config", obj("orientation", wantOr)));
                info = projectInfo(local);
                curOr = info.has("orientation") ? info.get("orientation").getAsString() : curOr;
            }
            int w = 0, h = 0;
            JsonObject ls = info.has("logic_size") && info.get("logic_size").isJsonObject() ? info.getAsJsonObject("logic_size") : null;
            if (ls != null) {
                w = (int) Math.round(ls.get("width").getAsDouble());
                h = (int) Math.round(ls.get("height").getAsDouble());
            }
            boolean land = curOr.equals("landscape");
            if (w > 0 && h > 0 && (w > h) != land) {
                // the stored sizes disagree with the flag: swap them so the frame matches the orientation
                call(local, "scenes", obj("action", "configure", "name", sceneName, "config", obj("swap_size", true)));
                info = projectInfo(local);
                ls = info.getAsJsonObject("logic_size");
                w = (int) Math.round(ls.get("width").getAsDouble());
                h = (int) Math.round(ls.get("height").getAsDouble());
                notes.add("the stored scene size did not match its orientation, swapped it");
            }
            if (w < 200 || h < 200) return error("the scene frame is " + w + "x" + h + " (project_info logic_size), too small for a template. Set it with scenes configure {logicWidth, logicHeight} and retry.");

            // ---- generate
            TemplateRegistry.Built built;
            try {
                built = TemplateRegistry.build(tpl.id(), params, w, h, sceneName);
            } catch (RuntimeException ex) {
                return error("template '" + tpl.id() + "' could not be built for " + w + "x" + h + ": " + ex.getMessage());
            }
            TemplateKit.Plan plan = built.plan;
            call(local, "scenes", obj("action", "configure", "name", sceneName, "config", obj("sceneColor", plan.sceneColor)));

            // ---- run the steps (everything is best effort: collect the failures instead of stopping)
            ArrayList<String> failed = new ArrayList<>(), warnings = new ArrayList<>();
            for (TemplateKit.Step s : plan.steps()) {
                JsonObject args = s.args.deepCopy();
                if (s.tool.equals("elements") || s.tool.equals("events") || s.tool.equals("variables")) args.addProperty("scene", sceneName);
                ToolResult r = call(local, s.tool, args);
                String t = text(r);
                if (r.isError) {
                    failed.add(s.label + ": " + clip(t, 300));
                    continue;
                }
                try {
                    JsonElement parsed = JsonParser.parseString(t);
                    failures(parsed, s.label, failed);
                    if (parsed.isJsonObject() && parsed.getAsJsonObject().has("warnings"))
                        for (JsonElement w2 : parsed.getAsJsonObject().getAsJsonArray("warnings")) {
                            String ws = w2.getAsString();
                            String low = ws.toLowerCase(Locale.ROOT);
                            // the scrolling worlds and the off-screen clone templates are meant to leave the frame
                            if (low.contains("outside") || low.contains("sticks out")) continue;
                            if (!warnings.contains(ws)) warnings.add(ws);
                        }
                } catch (Exception notJson) {
                    // plain-text answer: nothing to collect
                }
            }

            // ---- reply
            JsonObject o = new JsonObject();
            o.addProperty("ok", failed.isEmpty());
            o.addProperty("template", tpl.id());
            o.addProperty("scene", sceneName + (created ? " (new)" : ""));
            o.addProperty("orientation", curOr);
            o.addProperty("size", w + "x" + h);
            o.add("created", names(plan));
            if (!plan.info.isEmpty()) o.add("what", toArray(plan.info));
            JsonObject used = new JsonObject();
            for (Map.Entry<String, String> e : built.params.used.entrySet()) used.addProperty(e.getKey(), e.getValue());
            o.add("params", used);
            ArrayList<String> allNotes = new ArrayList<>(built.params.notes);
            allNotes.addAll(notes);
            if (!allNotes.isEmpty()) o.add("notes", toArray(allNotes));
            if (!plan.tweaks.isEmpty()) o.add("tweak", toArray(plan.tweaks));
            if (!failed.isEmpty()) o.add("failed", toArray(failed));
            if (!warnings.isEmpty()) o.add("warnings", toArray(warnings.size() > 8 ? warnings.subList(0, 8) : warnings));
            if (tpl.id().equals("main_menu") && !sceneName.equals("scene1"))
                o.addProperty("warning", "the game starts at scene1; this menu is '" + sceneName + "'. Apply it to scene1 (or rename) so it is the first screen.");
            o.addProperty("next", failed.isEmpty() ? "run_game, then screenshot to check it" : "fix the failed parts (elements/events), then run_game and screenshot");
            return o;
        } catch (Throwable t) {
            return error("templates failed: " + t);
        }
    }

    private static JsonArray toArray(List<String> l) {
        JsonArray a = new JsonArray();
        for (String s : l) a.add(s);
        return a;
    }

    /**
     * replace: starts the scene again from nothing by deleting and re-creating it (the engine has no "clear scene" call). A project's only
     * scene cannot be deleted, so a temporary scene bridges that case. Returns an error text or null.
     */
    private static String wipe(String name, JsonObject info, String wantOr, boolean local) {
        int total = info.has("scenes") ? info.getAsJsonArray("scenes").size() : 0;
        String tmp = null;
        if (total <= 1) {
            tmp = "tpl_tmp";
            ToolResult r = call(local, "scenes", obj("action", "create", "name", tmp));
            if (r.isError) return "replace: could not create the temporary scene: " + text(r);
        }
        ToolResult d = call(local, "scenes", obj("action", "delete", "name", name));
        if (d.isError) return "replace: could not delete scene '" + name + "': " + text(d);
        JsonObject a = obj("action", "create", "name", name);
        if (!wantOr.isEmpty()) a.addProperty("orientation", wantOr);
        ToolResult c = call(local, "scenes", a);
        if (c.isError) return "replace: deleted '" + name + "' but could not re-create it: " + text(c);
        if (tmp != null) call(local, "scenes", obj("action", "delete", "name", tmp));
        call(local, "scenes", obj("action", "open", "name", name));
        return null;
    }
}
