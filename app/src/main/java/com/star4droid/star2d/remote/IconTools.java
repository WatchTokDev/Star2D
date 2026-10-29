package com.star4droid.star2d.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.icons.IconCatalog;
import com.star4droid.star2d.icons.IconImport;
import com.star4droid.star2d.icons.IconRenderer;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The `icons` tool: search the built-in icon library and import icons into the project as PNG images. */
final class IconTools {
    private IconTools() {}

    static ToolResult icons(JsonObject a) throws Exception {
        String action = CoreTools.s(a, "action").toLowerCase(Locale.ROOT);
        if (action.isEmpty()) action = a.has("query") || a.has("q") ? "search" : a.has("id") || a.has("icons") ? "import" : "search";
        IconCatalog cat = IconCatalog.get();
        switch (action) {
            case "search": return search(cat, a);
            case "import": return importIcons(cat, a);
            case "list_categories":
            case "categories": return categories(cat);
            default:
                return ToolResult.error("Unknown action '" + action + "'. Use search {query} | import {id, name?, size?, color?} | list_categories.");
        }
    }

    private static ToolResult categories(IconCatalog cat) {
        JsonArray arr = new JsonArray();
        int[] ui = cat.counts(0), game = cat.counts(1);
        for (int i = 0; i < cat.categories().size(); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("category", cat.categories().get(i));
            if (ui[i] > 0) o.addProperty("ui", ui[i]);
            if (game[i] > 0) o.addProperty("game", game[i]);
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("total", cat.size());
        out.addProperty("sets", "ui = Material interface icons (" + cat.size(0) + "), game = game-icons.net swords, potions, monsters... (" + cat.size(1) + ")");
        out.add("categories", arr);
        out.addProperty("next", "icons {action:'search', query:'heart', category:'Game'}");
        return ToolResult.text(out.toString());
    }

    private static int categoryIndex(IconCatalog cat, String name) {
        if (name.isEmpty()) return -1;
        for (int i = 0; i < cat.categories().size(); i++) if (cat.categories().get(i).equalsIgnoreCase(name)) return i;
        throw new Gl.ToolError("Unknown category '" + name + "'. Categories: " + cat.categories());
    }

    private static ToolResult search(IconCatalog cat, JsonObject a) {
        String q = CoreTools.s(a, "query");
        if (q.isEmpty()) q = CoreTools.s(a, "q");
        int category = categoryIndex(cat, CoreTools.s(a, "category"));
        int set = IconCatalog.setIndex(CoreTools.s(a, "set"));
        if (set == -2) throw new Gl.ToolError("set must be ui, game or omitted (both).");
        int limit = Math.max(1, Math.min(30, CoreTools.i(a, "limit", 12)));
        if (q.isEmpty() && category < 0) throw new Gl.ToolError("query is required, e.g. icons {action:'search', query:'heart'}. Or pass category (see list_categories).");
        List<IconCatalog.Icon> all = cat.search(q, category, set, 0);
        JsonArray arr = new JsonArray();
        for (int i = 0; i < Math.min(limit, all.size()); i++) {
            IconCatalog.Icon ic = all.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("id", ic.id);
            if (ic.set == 1) o.addProperty("set", "game");
            String[] tags = ic.tags.isEmpty() ? new String[0] : ic.tags.split(" ");
            StringBuilder t = new StringBuilder();
            for (int k = 0; k < Math.min(6, tags.length); k++) t.append(k > 0 ? " " : "").append(tags[k]);
            if (t.length() > 0) o.addProperty("tags", t.toString());
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("matches", all.size());
        out.add("icons", arr);
        if (all.size() > limit) out.addProperty("more", all.size() - limit);
        if (all.isEmpty()) out.addProperty("hint", "No match: try a simpler or related word (e.g. 'sound' instead of 'audio file'), or list_categories.");
        else out.addProperty("next", "icons {action:'import', id:'<id>', size:64, color:'#FFFFFF'} then set the element image to use_as");
        return ToolResult.text(out.toString());
    }

    private static ToolResult importIcons(IconCatalog cat, JsonObject a) throws Exception {
        ArrayList<JsonObject> jobs = new ArrayList<>();
        if (a.has("icons") && a.get("icons").isJsonArray()) {
            for (JsonElement e : a.getAsJsonArray("icons")) {
                if (e.isJsonObject()) {
                    JsonObject j = e.getAsJsonObject().deepCopy();
                    // defaults given at the top level apply to every entry that does not set its own
                    for (String k : new String[]{"size", "color", "background", "background_color", "padding", "shadow"})
                        if (!j.has(k) && a.has(k)) j.add(k, a.get(k));
                    jobs.add(j);
                } else if (e.isJsonPrimitive()) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", e.getAsString());
                    for (String k : new String[]{"size", "color", "background", "background_color", "padding", "shadow"})
                        if (a.has(k)) j.add(k, a.get(k));
                    jobs.add(j);
                }
            }
        } else {
            jobs.add(a);
        }
        if (jobs.isEmpty()) throw new Gl.ToolError("id is required: icons {action:'import', id:'heart'}. Find ids with icons {action:'search', query:'...'}.");
        if (jobs.size() > 40) throw new Gl.ToolError("At most 40 icons per call.");

        final File projectDir = Gl.run(() -> new File(Proj.project().getPath()));
        JsonArray done = new JsonArray();
        JsonArray failed = new JsonArray();
        for (JsonObject j : jobs) {
            String id = CoreTools.s(j, "id");
            IconCatalog.Icon ic = cat.byId(id);
            if (ic == null) {
                JsonObject f = new JsonObject();
                f.addProperty("id", id);
                f.addProperty("error", "unknown icon id. Use icons {action:'search', query:'" + (id.isEmpty() ? "heart" : id.replace('-', ' ')) + "'}");
                List<IconCatalog.Icon> near = cat.search(id.replace('-', ' ').replace('_', ' '), -1, 5);
                if (!near.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (IconCatalog.Icon n : near) sb.append(sb.length() > 0 ? ", " : "").append(n.id);
                    f.addProperty("similar", sb.toString());
                }
                failed.add(f);
                continue;
            }
            IconRenderer.Options o = new IconRenderer.Options();
            o.size = IconRenderer.clampSize(CoreTools.i(j, "size", IconRenderer.DEFAULT_SIZE));
            try {
                o.color = IconRenderer.parseColor(CoreTools.s(j, "color"));
                String bg = CoreTools.s(j, "background").toLowerCase(Locale.ROOT);
                if (bg.equals("circle")) o.background = 1;
                else if (bg.equals("rounded") || bg.equals("square") || bg.equals("rounded_square")) o.background = 2;
                if (o.background != 0) o.backgroundColor = IconRenderer.parseColor(CoreTools.s(j, "background_color").isEmpty() ? "#2196F3" : CoreTools.s(j, "background_color"));
            } catch (IllegalArgumentException e) {
                throw new Gl.ToolError(e.getMessage());
            }
            if (j.has("padding")) {
                try {
                    o.padding = (float) j.get("padding").getAsDouble();
                } catch (Exception ignored) {
                }
            }
            o.shadow = CoreTools.b(j, "shadow", false);
            String name = CoreTools.s(j, "name");
            // our own default-named file is simply refreshed; a name the AI chose is protected unless replace:true
            boolean replace = j.has("replace") ? CoreTools.b(j, "replace", false) : name.isEmpty();
            JsonObject r = new JsonObject();
            try {
                IconImport.Result res = IconImport.importIcon(projectDir, ic, name, o, replace);
                r.addProperty("id", ic.id);
                r.addProperty("use_as", res.useAs);
                r.addProperty("size", o.size);
                if (res.replaced) r.addProperty("replaced", true);
                done.add(r);
            } catch (IllegalStateException e) {
                r.addProperty("id", ic.id);
                r.addProperty("error", e.getMessage());
                failed.add(r);
            }
        }
        if (done.size() > 0) {
            Gl.run(() -> Bridge.app().applyExternalChanges(Bridge.app().openSceneFingerprints()));
        }
        JsonObject out = new JsonObject();
        out.add("imported", done);
        if (failed.size() > 0) out.add("failed", failed);
        if (done.size() > 0)
            out.addProperty("note", "Set the element's image property to use_as. Icons are drawn in the colour you passed (white by default); a white icon can also be recoloured on any element with its Tint property.");
        ToolResult tr = ToolResult.text(out.toString());
        tr.isError = done.size() == 0 && failed.size() > 0;
        return tr;
    }
}
