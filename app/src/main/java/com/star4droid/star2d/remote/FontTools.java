package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.fonts.FontPresets;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The `fonts` tool: Star2D bitmap fonts (.s2df). list = presets + real parameters, create = write a .s2df (optionally set a TEXT element's font),
 * get = read one back, apply = set an existing font on a TEXT element, measure = how wide a text is in a font (so the TEXT does not wrap).
 * A .s2df holds no glyph data: the game builds the bitmap font from the TTF with FreeType when the TEXT loads it (see FontPresets).
 */
final class FontTools {
    private FontTools() {}

    /** Keys that belong to the tool, not to the font. */
    private static final String[] TOOL_KEYS = {"action", "name", "preset", "ttf", "rtl", "replace", "element", "text", "font_scale"};

    static ToolResult fonts(JsonObject a) throws Exception {
        String action = CoreTools.s(a, "action");
        if (action.isEmpty()) action = CoreTools.s(a, "name").isEmpty() ? "list" : "create";
        switch (action) {
            case "list": return list();
            case "create": return create(a);
            case "get": return get(a);
            case "apply": return apply(a);
            case "measure": return measure(a);
            default: return ToolResult.error("Unknown action '" + action + "'. Use list | create | get | apply | measure.");
        }
    }

    // ------------------------------------------------------------------ list

    private static ToolResult list() {
        JsonObject o = new JsonObject();
        JsonArray presets = new JsonArray();
        for (FontPresets.Preset p : FontPresets.all()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", p.id);
            x.addProperty("look", p.desc);
            if (p.rtl) x.addProperty("rtl", true);
            presets.add(x);
        }
        o.add("presets", presets);
        JsonArray params = new JsonArray();
        for (FontPresets.Spec s : FontPresets.specs()) params.add(s.describe());
        o.add("params", params);
        o.addProperty("how", "create {name, preset?, <any param>} writes fonts/<name>.s2df (+ copies the TTF next to it) and returns use_as = the value of a TEXT element's `font` property. "
                + "Pass only what you want to change; everything else keeps the preset. Add element:'Score' to set it on that TEXT right away. "
                + "No gradient/glow exists: looks = fill colour + ONE outline (borderWidth/borderColor) + ONE drop shadow. Keep `color` white to tint with the element's Text Color. "
                + "Arabic: preset arabic_clean/arabic_gold (rtl:true + a TTF that has Arabic letters). The TEXT must be wide enough: fonts {action:'measure', name, text} tells the width.");
        JsonArray ttf = new JsonArray();
        for (String t : FontPresets.BUNDLED_TTF) ttf.add(t);
        o.add("bundled_ttf", ttf);
        o.addProperty("ttf_note", "ttf: a bundled name above, or a project path of your own .ttf/.otf (e.g. '/fonts/My.ttf'). DroidSans/VisOpenSans have NO Arabic letters; Parvin-Regular has.");
        return ToolResult.text(o.toString());
    }

    // ------------------------------------------------------------------ create

    private static ToolResult create(JsonObject a) throws Exception {
        String name = CoreTools.s(a, "name");
        String nameErr = checkName(name);
        if (nameErr != null) return ToolResult.error(nameErr);

        FontPresets.Preset preset;
        String presetId = CoreTools.s(a, "preset");
        if (presetId.isEmpty()) preset = FontPresets.byId("arcade_outline");
        else {
            preset = FontPresets.byId(presetId);
            if (preset == null) return ToolResult.error("Unknown preset '" + presetId + "'. Call fonts {action:'list'}.");
        }
        ArrayList<String> warnings = new ArrayList<>();
        LinkedHashMap<String, String> params;
        try {
            params = FontPresets.merge(preset, a, warnings, TOOL_KEYS);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage() + ". Call fonts {action:'list'} for the parameters and ranges.");
        }

        boolean rtl = a.has("rtl") ? CoreTools.b(a, "rtl", false) : preset.rtl;
        String ttfArg = CoreTools.s(a, "ttf");
        String ttfWanted = ttfArg.isEmpty() ? preset.ttf : ttfArg;
        if (ttfArg.isEmpty() && rtl && !hasArabic(ttfWanted)) {
            ttfWanted = FontPresets.ARABIC_TTF;
            warnings.add("rtl:true needs a font with Arabic letters: using " + FontPresets.ARABIC_TTF + ".");
        } else if (rtl && !ttfArg.isEmpty() && isBundled(ttfWanted) && !hasArabic(ttfWanted)) {
            warnings.add(ttfWanted + " has no Arabic letters: Arabic text will show as boxes. Use " + FontPresets.ARABIC_TTF + " or your own Arabic TTF.");
        }
        final boolean wantRtl = rtl;
        final String ttfFinal = ttfWanted;
        final boolean replace = CoreTools.b(a, "replace", false);
        final String element = CoreTools.s(a, "element");
        final LinkedHashMap<String, String> pfinal = params;

        final Project project = Gl.run(Proj::project);
        final File projectDir = new File(project.getPath());

        // resolve the TTF and the folder the .s2df goes to (next to the TTF, like the editor's font maker does)
        final File[] ttfAndDir = new File[2];
        String err = Gl.run(() -> {
            String bundled = bundledName(ttfFinal);
            if (bundled != null) {
                File dir = new File(projectDir, "fonts");
                FileHandle h = FontPresets.ensureBundledTtf(Gdx.files.absolute(dir.getAbsolutePath()), bundled);
                ttfAndDir[0] = h.file();
                ttfAndDir[1] = dir;
                return null;
            }
            File f = Proj.safe(project, ttfFinal);
            String low = f.getName().toLowerCase(Locale.ROOT);
            if (!f.isFile() || !(low.endsWith(".ttf") || low.endsWith(".otf")))
                return "ttf '" + ttfFinal + "' not found in the project (or not a .ttf/.otf). Use a bundled name (" + String.join(", ", FontPresets.BUNDLED_TTF) + ") or a project path like '/fonts/My.ttf'.";
            ttfAndDir[0] = f;
            ttfAndDir[1] = f.getParentFile();
            return null;
        });
        if (err != null) return ToolResult.error(err);

        File s2df = new File(ttfAndDir[1], name + ".s2df");
        boolean existed = s2df.exists();
        if (existed && !replace)
            return ToolResult.error("Font '" + name + "' already exists (" + Proj.rel(project, s2df) + "). Pass replace:true to overwrite it, or choose another name.");
        ttfAndDir[1].mkdirs();
        String text = FontPresets.s2df(name, "/" + ttfAndDir[0].getName(), wantRtl, pfinal);
        Files.write(s2df.toPath(), text.getBytes(StandardCharsets.UTF_8));

        // prove it loads (TTF readable, parameters accepted) and make the editor forget an older version of this font
        final String abs = s2df.getAbsolutePath();
        final String useAs = "/" + Proj.rel(project, s2df).replace(File.separatorChar, '/');
        String loadError = Gl.run(() -> {
            try {
                BitmapFont f = FontPresets.generate(Gdx.files.absolute(ttfAndDir[0].getAbsolutePath()), pfinal, 1f, "AaBb");
                f.dispose();
            } catch (Throwable t) {
                return String.valueOf(t.getMessage() == null ? t : t.getMessage());
            }
            try {
                Bridge.app().updateFont(abs);
                Bridge.app().updateFont(useAs);
                Bridge.app().applyExternalChanges(Bridge.app().openSceneFingerprints());
                Bridge.editor().updateChilds();
            } catch (Throwable ignored) {
            }
            return null;
        });
        if (loadError != null) {
            s2df.delete();
            return ToolResult.error("The font could not be generated: " + loadError + ". Nothing was saved. Check the ttf and the parameter values.");
        }

        JsonObject o = new JsonObject();
        o.addProperty("use_as", useAs);
        o.addProperty("ttf", "/" + ttfAndDir[0].getName());
        o.addProperty("rtl", wantRtl);
        o.addProperty("preset", preset.id);
        o.addProperty("size", pfinal.containsKey("size") ? pfinal.get("size") : "16");
        o.addProperty("note", "Set a TEXT element's `font` to use_as (elements set {props:{font:use_as}}). The TEXT must be wide enough for its text: fonts {action:'measure', name:'" + name + "', text:'...'} gives the width; the layout warning does not measure custom fonts.");
        if (existed) o.addProperty("replaced", true);
        if (!element.isEmpty()) applyTo(element, useAs, o, warnings);
        if (!warnings.isEmpty()) {
            JsonArray w = new JsonArray();
            for (String x : warnings) w.add(x);
            o.add("warnings", w);
        }
        return ToolResult.text(o.toString());
    }

    // ------------------------------------------------------------------ get / apply / measure

    private static File findS2df(Project project, String name) {
        if (name.endsWith(".s2df")) {
            try {
                File f = Proj.safe(project, name);
                if (f.isFile()) return f;
            } catch (Exception ignored) {
            }
            name = new File(name).getName().replace(".s2df", "");
        }
        File[] roots = {new File(project.getPath(), "fonts"), new File(project.getPath())};
        for (File r : roots) {
            File f = new File(r, name + ".s2df");
            if (f.isFile()) return f;
        }
        File found = scan(new File(project.getPath()), name + ".s2df", 0);
        return found;
    }

    private static File scan(File dir, String fileName, int depth) {
        File[] list = dir.listFiles();
        if (list == null || depth > 4) return null;
        for (File f : list) if (f.isFile() && f.getName().equals(fileName)) return f;
        for (File f : list) {
            if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equals("build")) {
                File r = scan(f, fileName, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static ToolResult get(JsonObject a) throws Exception {
        String name = CoreTools.s(a, "name");
        if (name.isEmpty()) return ToolResult.error("name is required (the font name, e.g. title_font)");
        Project project = Gl.run(Proj::project);
        File f = findS2df(project, name);
        if (f == null) return ToolResult.error("No font '" + name + "' (.s2df) in the project.");
        Map<String, String> m = FontPresets.readS2df(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        if (m == null) return ToolResult.error(Proj.rel(project, f) + " is not a valid .s2df file.");
        JsonObject o = new JsonObject();
        o.addProperty("use_as", "/" + Proj.rel(project, f).replace(File.separatorChar, '/'));
        JsonObject s = new JsonObject();
        for (Map.Entry<String, String> e : m.entrySet()) s.addProperty(e.getKey(), e.getValue());
        o.add("settings", s);
        return ToolResult.text(o.toString());
    }

    private static ToolResult apply(JsonObject a) throws Exception {
        String name = CoreTools.s(a, "name"), element = CoreTools.s(a, "element");
        if (name.isEmpty() || element.isEmpty()) return ToolResult.error("name (the font) and element (a TEXT element) are required");
        Project project = Gl.run(Proj::project);
        File f = findS2df(project, name);
        if (f == null) return ToolResult.error("No font '" + name + "' (.s2df) in the project. Create it with fonts {action:'create'}.");
        String useAs = "/" + Proj.rel(project, f).replace(File.separatorChar, '/');
        JsonObject o = new JsonObject();
        ArrayList<String> warnings = new ArrayList<>();
        o.addProperty("use_as", useAs);
        applyTo(element, useAs, o, warnings);
        if (!warnings.isEmpty()) {
            JsonArray w = new JsonArray();
            for (String x : warnings) w.add(x);
            o.add("warnings", w);
        }
        return ToolResult.text(o.toString());
    }

    private static void applyTo(String element, String useAs, JsonObject out, ArrayList<String> warnings) {
        try {
            JsonObject op = new JsonObject();
            op.addProperty("op", "set");
            op.addProperty("name", element);
            JsonObject props = new JsonObject();
            props.addProperty("font", useAs);
            op.add("props", props);
            JsonArray ops = new JsonArray();
            ops.add(op);
            JsonObject args = new JsonObject();
            args.add("ops", ops);
            ToolResult r = EditTools.elements(args);
            String txt = r.toString();
            if (r.isError || txt.contains("\\\"ok\\\":false")) warnings.add("could not set font on '" + element + "': " + txt);
            else out.addProperty("applied_to", element);
        } catch (Exception e) {
            warnings.add("could not set font on '" + element + "': " + e.getMessage());
        }
    }

    private static ToolResult measure(JsonObject a) throws Exception {
        String name = CoreTools.s(a, "name"), text = CoreTools.s(a, "text");
        if (name.isEmpty() || text.isEmpty()) return ToolResult.error("name (the font) and text are required");
        final float scale = a.has("font_scale") ? Math.max(0.05f, (float) a.get("font_scale").getAsDouble()) : 1f;
        final Project project = Gl.run(Proj::project);
        File f = findS2df(project, name);
        if (f == null) return ToolResult.error("No font '" + name + "' (.s2df) in the project.");
        final Map<String, String> m = FontPresets.readS2df(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        if (m == null) return ToolResult.error("not a valid .s2df file");
        final File ttf = new File(f.getParentFile(), m.containsKey("font") ? m.get("font") : "");
        if (!ttf.isFile()) return ToolResult.error("The TTF '" + m.get("font") + "' next to the font is missing.");
        final String t = text;
        float[] r = Gl.run(() -> {
            LinkedHashMap<String, String> p = new LinkedHashMap<>();
            for (FontPresets.Spec s : FontPresets.specs()) if (m.containsKey(s.key)) p.put(s.key, m.get(s.key));
            BitmapFont font = FontPresets.generate(Gdx.files.absolute(ttf.getAbsolutePath()), p, 1f, t + " ");
            try {
                GlyphLayout gl = new GlyphLayout(font, t);
                return new float[]{gl.width, gl.height, font.getLineHeight(), t.split("\n", -1).length};
            } finally {
                font.dispose();
            }
        });
        JsonObject o = new JsonObject();
        int w = Math.round(r[0] * scale + 8), h = Math.round(Math.max(r[1], r[2] * r[3]) * scale + 4);
        o.addProperty("text_width_px", Math.round(r[0] * scale));
        o.addProperty("line_height_px", Math.round(r[2] * scale));
        o.addProperty("font_scale", scale);
        o.addProperty("advice", "give the TEXT width >= " + w + " and height >= " + h + " (editor px) so it does not wrap or get cut");
        if ("true".equals(m.get("rtl"))) o.addProperty("note", "right-to-left font: the width is approximate (letters are shaped at runtime), add 10-15% margin.");
        return ToolResult.text(o.toString());
    }

    // ------------------------------------------------------------------ helpers

    private static String checkName(String name) {
        if (name.isEmpty()) return "name is required (letters, digits, _), e.g. title_font";
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,39}")) return "name must be letters, digits and _ (max 40, not starting with a digit): '" + name + "'";
        return null;
    }

    private static String bundledName(String ttf) {
        String n = ttf.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        for (String b : FontPresets.BUNDLED_TTF) {
            if (b.equalsIgnoreCase(n)) {
                // a project path that merely ends with a bundled file name is the user's own copy: only plain names count
                return (ttf.equals(n) || ttf.equals("/" + n)) ? b : null;
            }
        }
        return null;
    }

    private static boolean isBundled(String ttf) {
        return bundledName(ttf) != null;
    }

    private static boolean hasArabic(String ttf) {
        String b = bundledName(ttf);
        return b == null || b.equals(FontPresets.ARABIC_TTF); // an unknown custom font is assumed to know what it does
    }
}
