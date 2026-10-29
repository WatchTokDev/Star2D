package com.star4droid.star2d.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

/** The list of templates, the short catalog and "build one". Pure Java (the harness uses it too). */
final class TemplateRegistry {
    private TemplateRegistry() {}

    static final List<TemplateLib.Tpl> ALL = Arrays.<TemplateLib.Tpl>asList(
            new TemplatePlatformer(),
            new TemplateTopDown(),
            new TemplateShooter(),
            new TemplateFlappy(),
            new TemplateBreakout(),
            new TemplateMainMenu());

    private static TemplateLib.Tpl exact(String id) {
        for (TemplateLib.Tpl x : ALL) if (x.id().equals(id)) return x;
        return null;
    }

    static TemplateLib.Tpl find(String id) {
        if (id == null) return null;
        String t = id.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        TemplateLib.Tpl x = exact(t);
        if (x != null) return x;
        // a few natural aliases
        if (t.equals("topdown") || t.equals("top_down_shooter") || t.equals("rpg")) return exact("top_down");
        if (t.equals("shooter") || t.equals("spaceshooter") || t.equals("space")) return exact("space_shooter");
        if (t.equals("flappy_bird") || t.equals("flappybird")) return exact("flappy");
        if (t.equals("arkanoid") || t.equals("bricks") || t.equals("brick_breaker")) return exact("breakout");
        if (t.equals("menu") || t.equals("mainmenu") || t.equals("title")) return exact("main_menu");
        if (t.equals("jump_and_run") || t.equals("platform") || t.equals("mario")) return exact("platformer");
        return null;
    }

    static String ids() {
        StringBuilder sb = new StringBuilder();
        for (TemplateLib.Tpl x : ALL) sb.append(sb.length() > 0 ? ", " : "").append(x.id());
        return sb.toString();
    }

    /** SHORT catalog: id, one line, params with their defaults. The details are in `templates {action:'info'}` / read_docs templates. */
    static JsonObject catalog() {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (TemplateLib.Tpl t : ALL) {
            JsonObject e = new JsonObject();
            e.addProperty("id", t.id());
            e.addProperty("about", t.about());
            JsonObject ps = new JsonObject();
            for (TemplateLib.P p : t.params()) ps.addProperty(p.key, p.def);
            e.add("params", ps);
            arr.add(e);
        }
        o.add("templates", arr);
        o.addProperty("use", "templates {action:'apply', id:'<id>', scene:'<name>' (optional), params:{...} (optional), orientation:'portrait|landscape' (optional)}. One call builds a complete, playable game; then run_game + screenshot. templates {action:'info', id} = what it creates and which variables to tweak.");
        return o;
    }

    static final class Built {
        final TemplateLib.Tpl tpl;
        final TemplateKit.Plan plan;
        final TemplateLib.Params params;

        Built(TemplateLib.Tpl tpl, TemplateKit.Plan plan, TemplateLib.Params params) {
            this.tpl = tpl;
            this.plan = plan;
            this.params = params;
        }
    }

    /** w x h = the REAL frame of the scene (portrait: w is the short side). */
    static Built build(String id, JsonObject params, int w, int h, String scene) {
        TemplateLib.Tpl t = find(id);
        if (t == null) throw new IllegalArgumentException("unknown template '" + id + "'. Available: " + ids());
        TemplateLib.Params p = new TemplateLib.Params(params);
        TemplateKit.Plan plan = t.build(p, w, h, scene);
        return new Built(t, plan, p);
    }
}
