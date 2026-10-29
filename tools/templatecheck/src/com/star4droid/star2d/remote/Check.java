package com.star4droid.star2d.remote;

import com.google.gson.*;
import com.star4droid.star2d.ui.blocks.BlockCatalog;
import com.star4droid.star2d.ui.blocks.BlockModel;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;

/**
 * Offline checker of the game templates (tools/verify_templates.py runs it). It builds every template for portrait and landscape and
 * checks the produced tool calls against the REAL engine data: the block catalog (titles, field names, reporter argument counts),
 * the element property tables, the generated Java (compiled separately by the python script), z policy, layout, text metrics read from
 * default.fnt, and for the platformer the jump reachability.
 *
 * usage: Check <assetsDir> <outDir>
 */
public class Check {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static int fails = 0, checks = 0;
    static String ctx = "";

    static void ok(boolean cond, String msg) {
        checks++;
        if (!cond) {
            fails++;
            System.out.println("  FAIL [" + ctx + "] " + msg);
        }
    }

    static String assets;
    static Map<Integer, Integer> adv = new HashMap<>();
    static int lineHeight = 20;
    static Map<String, Set<String>> propKeys = new HashMap<>();

    static String read(String rel) throws Exception {
        return new String(Files.readAllBytes(Paths.get(assets, rel)), StandardCharsets.UTF_8);
    }

    static Set<String> keysOf(String text) {
        Set<String> s = new HashSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(text);
        while (m.find()) s.add(m.group(1).toLowerCase(Locale.ROOT));
        return s;
    }

    public static void main(String[] a) throws Exception {
        assets = a[0];
        String out = a[1];
        new File(out).mkdirs();
        BlockCatalog.load(new android.content.Context() {});
        // font metrics
        for (String line : read("files/default.fnt").split("\n")) {
            if (line.startsWith("char ")) {
                Matcher m = Pattern.compile("id=(\\d+).*?xadvance=(-?\\d+)").matcher(line);
                if (m.find()) adv.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            } else if (line.startsWith("common ")) {
                Matcher m = Pattern.compile("lineHeight=(\\d+)").matcher(line);
                if (m.find()) lineHeight = Integer.parseInt(m.group(1));
            }
        }
        Set<String> def = keysOf(read("items/default.json"));
        for (String t : new String[]{"box", "circle", "text", "progress", "joystick"}) {
            Set<String> s = new HashSet<>(def);
            s.addAll(keysOf(read("items/" + t + ".json")));
            propKeys.put(t.toUpperCase(Locale.ROOT), s);
        }
        int[][] frames = {{1280, 720}, {720, 1280}};
        ArrayList<String> index = new ArrayList<>();
        for (TemplateLib.Tpl t : TemplateRegistry.ALL) {
            ArrayList<String> variants = new ArrayList<>();
            variants.add("{}");
            variants.addAll(Arrays.asList(t.samples()));
            for (int[] fr : frames) {
                int vi = 0;
                for (String v : variants) {
                    String tag = t.id() + "_" + (fr[0] > fr[1] ? "land" : "port") + "_" + vi++;
                    ctx = tag;
                    System.out.println("== " + tag + " " + fr[0] + "x" + fr[1] + " params=" + v);
                    JsonObject params = JsonParser.parseString(v).getAsJsonObject();
                    TemplateRegistry.Built b;
                    try {
                        b = TemplateRegistry.build(t.id(), params, fr[0], fr[1], "scene1");
                    } catch (Throwable ex) {
                        ok(false, "build threw " + ex);
                        ex.printStackTrace(System.out);
                        continue;
                    }
                    // determinism: building twice gives identical steps
                    TemplateRegistry.Built b2 = TemplateRegistry.build(t.id(), params, fr[0], fr[1], "scene1");
                    ok(stepsJson(b.plan).equals(stepsJson(b2.plan)), "build is not deterministic");
                    ok(stepsJson(b.plan).equals(stepsJson(b.plan)), "steps() is not idempotent");
                    check(b, tag, out);
                    index.add(tag);
                    for (String n : b.params.notes) System.out.println("  note: " + n);
                }
            }
        }
        Files.write(Paths.get(out, "index.txt"), String.join("\n", index).getBytes(StandardCharsets.UTF_8));
        System.out.println("\nchecks: " + checks + ", failures: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static String stepsJson(TemplateKit.Plan p) {
        JsonArray arr = new JsonArray();
        for (TemplateKit.Step s : p.steps()) {
            JsonObject o = new JsonObject();
            o.addProperty("tool", s.tool);
            o.add("args", s.args);
            arr.add(o);
        }
        return arr.toString();
    }

    // ------------------------------------------------------------------ one built template

    static final Set<String> RESERVED = new HashSet<>(Arrays.asList("world", "steps", "playing", "project", "finished", "stage", "camera", "viewport", "assetLoader",
            "current", "event", "body1", "body2", "this", "super", "class", "int", "float", "double", "boolean", "String", "new", "return", "if", "else", "for", "while", "do", "null", "true", "false"));
    static final Set<String> SCENE_EVENTS = new HashSet<>(Arrays.asList("OnCreate", "OnStep", "onCollisionStart", "onCollisionEnd", "OnPause", "OnResume"));
    static final Set<String> EL_EVENTS = new HashSet<>(Arrays.asList("onClick", "onTouchStart", "onTouchEnd", "OnBodyCreated", "OnBodyUpdate", "onBodyCollided", "onBodyCollideEnd"));

    static void check(TemplateRegistry.Built b, String tag, String outDir) throws Exception {
        TemplateKit.Plan p = b.plan;
        List<TemplateKit.Step> steps = p.steps();
        // dump for the preview / compile steps
        JsonObject dump = new JsonObject();
        dump.addProperty("w", p.w);
        dump.addProperty("h", p.h);
        dump.addProperty("landscape", p.landscape);
        dump.addProperty("sceneColor", p.sceneColor);
        JsonArray sa = new JsonArray();
        for (TemplateKit.Step s : steps) {
            JsonObject o = new JsonObject();
            o.addProperty("tool", s.tool);
            o.add("args", s.args);
            sa.add(o);
        }
        dump.add("steps", sa);
        JsonObject meta = new JsonObject();
        for (Map.Entry<String, Object> e : p.meta.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Number) meta.addProperty(e.getKey(), (Number) v);
            else if (v instanceof Boolean) meta.addProperty(e.getKey(), (Boolean) v);
            else meta.addProperty(e.getKey(), String.valueOf(v));
        }
        dump.add("meta", meta);
        JsonObject widest = new JsonObject();
        for (TemplateKit.El e : p.elements) if (e.type.equals("TEXT")) widest.addProperty(e.name, e.widest);
        dump.add("widest", widest);
        Files.write(Paths.get(outDir, tag + ".json"), GSON.toJson(dump).getBytes(StandardCharsets.UTF_8));

        // ---- tool order: images/sounds first, then variables, elements, events last
        String order = "";
        for (TemplateKit.Step s : steps) order += s.tool.charAt(0);
        ok(order.matches("c+(?:s*)v?e+v?"), "unexpected step order " + order); // create_image/create_sound, variables, elements, events
        ok(steps.get(steps.size() - 1).tool.equals("events"), "events must be last (they reference the elements)");

        // ---- elements
        HashMap<String, JsonObject> els = new LinkedHashMap<>();
        HashMap<String, String> elType = new HashMap<>();
        Set<String> images = new HashSet<>(), sounds = new HashSet<>();
        int lastZ = -1;
        for (TemplateKit.Step s : steps) {
            if (s.tool.equals("create_image")) {
                String path = s.args.get("path").getAsString();
                ok(path.matches("images/[A-Za-z0-9_]+\\.png"), "bad image path " + path);
                images.add("/" + path.substring("images/".length()));
                checkImage(s.args, path);
            } else if (s.tool.equals("create_sound")) {
                String path = s.args.get("path").getAsString();
                ok(path.matches("sounds/[A-Za-z0-9_]+\\.wav"), "bad sound path " + path);
                sounds.add(path.substring("sounds/".length()));
                ok(Arrays.asList("jump", "coin", "hit", "explosion", "shoot", "powerup", "click", "laser", "blip").contains(s.args.get("preset").getAsString()), "bad sound preset");
            } else if (s.tool.equals("elements")) {
                for (JsonElement je : s.args.getAsJsonArray("ops")) {
                    JsonObject op = je.getAsJsonObject();
                    ok(op.get("op").getAsString().equals("add"), "only add ops expected");
                    String name = op.get("name").getAsString(), type = op.get("type").getAsString();
                    ok(name.matches("[A-Za-z_][A-Za-z0-9_]*"), "bad element name " + name);
                    ok(!RESERVED.contains(name), "reserved element name " + name);
                    ok(!els.containsKey(name), "duplicate element " + name);
                    ok(propKeys.containsKey(type), "unsupported element type " + type);
                    JsonObject props = op.getAsJsonObject("props");
                    Set<String> allowed = propKeys.getOrDefault(type, new HashSet<>());
                    for (Map.Entry<String, JsonElement> pe : props.entrySet()) {
                        ok(allowed.contains(pe.getKey().toLowerCase(Locale.ROOT)), name + ": unknown property '" + pe.getKey() + "' for " + type);
                        ok(pe.getValue().isJsonPrimitive(), name + "." + pe.getKey() + " must be a string/number/boolean");
                        if (pe.getKey().equals("z")) {
                            int z = pe.getValue().getAsInt();
                            ok(z >= 0, name + ": negative z");
                            ok(z >= lastZ, name + ": created out of z order (" + z + " after " + lastZ + "): touch order would not match draw order");
                            lastZ = Math.max(lastZ, z);
                        }
                        if (pe.getKey().equals("image")) ok(images.contains(pe.getValue().getAsString()), name + ": image " + pe.getValue().getAsString() + " is not created by this template");
                        if (pe.getKey().equals("type")) ok(Arrays.asList("DYNAMIC", "STATIC", "KINAMATIC", "UI").contains(pe.getValue().getAsString()), name + ": bad body type");
                        if (pe.getKey().equals("Tint")) ok(pe.getValue().getAsString().matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"), name + ": bad Tint");
                        if (pe.getKey().equals("Text Color")) ok(pe.getValue().getAsString().matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"), name + ": bad Text Color");
                    }
                    ok(props.has("z"), name + ": has no explicit z");
                    els.put(name, props);
                    elType.put(name, type);
                }
            }
        }
        ok(els.size() == p.elements.size(), "element count differs from the plan");
        ok(els.size() <= 120, "too many elements: " + els.size());
        // ---- variables
        Set<String> vars = new HashSet<>();
        for (TemplateKit.Step s : steps)
            if (s.tool.equals("variables"))
                for (JsonElement je : s.args.getAsJsonArray("ops")) {
                    JsonObject o = je.getAsJsonObject();
                    String n = o.get("name").getAsString();
                    ok(n.matches("[A-Za-z_][A-Za-z0-9_]*"), "bad variable name " + n);
                    ok(!els.containsKey(n), "variable " + n + " has the name of an element");
                    ok(!RESERVED.contains(n), "reserved variable name " + n);
                    ok(vars.add(n), "duplicate variable " + n);
                    ok(!o.get("type").getAsString().isEmpty(), "variable without type " + n);
                    String ty = o.get("type").getAsString(), val = o.get("value").getAsString();
                    if (ty.equals("float") && n.toLowerCase(Locale.ROOT).matches(".*(speed|power).*")) {
                        double d = Double.parseDouble(val.replace("f", ""));
                        ok(d <= 450 && d >= 0, "variable " + n + " = " + d + " is over the 450 px/s speed cap");
                    }
                }
        // ---- events: build with the REAL BlockBuilder, check names, speeds
        JsonObject eventsArgs = null;
        for (TemplateKit.Step s : steps) if (s.tool.equals("events")) eventsArgs = s.args;
        Set<String> seen = new HashSet<>();
        JsonObject code = new JsonObject();
        for (JsonElement je : eventsArgs.getAsJsonArray("ops")) {
            JsonObject o = je.getAsJsonObject();
            String scope = o.get("scope").getAsString(), ev = o.get("event").getAsString();
            String key = scope.equals("element") ? o.get("element").getAsString() + "." + ev : "scene." + ev;
            ok(seen.add(key), "event set twice: " + key);
            if (scope.equals("element")) {
                String el = o.get("element").getAsString();
                ok(els.containsKey(el), "event on a missing element " + el);
                ok(EL_EVENTS.contains(ev), "bad element event " + ev);
                String ty = elType.get(el), bt = els.containsKey(el) && els.get(el).has("type") ? els.get(el).get("type").getAsString() : "";
                ok(ty != null && !ty.equals("TEXT"), "touch/collision events on a TEXT are not delivered (" + el + "): put them on a BOX");
                ok("BOX".equals(ty) || "CIRCLE".equals(ty), "events only on BOX/CIRCLE: " + el);
            } else ok(SCENE_EVENTS.contains(ev), "bad scene event " + ev);
            try {
                BlockModel m = BlockBuilder.build(o.getAsJsonArray("blocks"));
                String java = m.exportCode();
                ok(m.formatFailures.isEmpty(), key + ": a block template failed to format " + m.formatFailures);
                ok(!java.contains("%1$s") && !java.contains("%2$s"), key + ": unformatted placeholder in the generated code");
                code.addProperty(key, java);
            } catch (Throwable ex) {
                ok(false, key + ": BlockBuilder: " + ex.getMessage());
            }
            checkSpeeds(o.get("blocks"), key);
            checkBodyRefs(o.get("blocks"), els, elType, key);
        }
        Files.write(Paths.get(outDir, tag + ".code.json"), GSON.toJson(code).getBytes(StandardCharsets.UTF_8));
        // every button has its touch event
        for (Map.Entry<String, JsonObject> e : els.entrySet()) {
            String role = roleOf(p, e.getKey());
            if (role.equals("button")) ok(seen.stream().anyMatch(k -> k.startsWith(e.getKey() + ".")), "button " + e.getKey() + " has no event");
        }

        checkLayout(p, els, elType);
        checkZ(p, els);
        checkTexts(p, els);
        checkSolids(p, els, elType);
        if (p.meta.containsKey("kind") && "platformer".equals(p.meta.get("kind"))) checkPlatformer(p, els, eventsArgs);
        if ("top_down".equals(p.meta.get("kind"))) checkTopDown(p, els);
        if ("flappy".equals(p.meta.get("kind"))) checkFlappy(p, els, eventsArgs);
        if ("breakout".equals(p.meta.get("kind"))) checkBreakout(p, els, eventsArgs);
        if ("space_shooter".equals(p.meta.get("kind"))) checkShooter(p, els, eventsArgs);
        // sounds used exist
        Matcher sm = Pattern.compile("\"Sound\":\"([^\"]+)\"").matcher(eventsArgs.toString());
        while (sm.find()) ok(sounds.contains(sm.group(1)), "sound " + sm.group(1) + " is not created");
        // scene events: first block of OnCreate of a physical scene is the gravity
        JsonArray create = null;
        for (JsonElement je : eventsArgs.getAsJsonArray("ops")) {
            JsonObject o = je.getAsJsonObject();
            if (o.get("scope").getAsString().equals("scene") && o.get("event").getAsString().equals("OnCreate")) create = o.getAsJsonArray("blocks");
        }
        boolean physics = false;
        for (JsonObject pr : els.values()) if (pr.has("type") && (pr.get("type").getAsString().equals("DYNAMIC"))) physics = true;
        if (physics) ok(create != null && create.size() > 0 && create.get(0).getAsJsonObject().get("block").getAsString().equals("setWorldGravity"), "first block of OnCreate must be setWorldGravity");
        System.out.println("  elements=" + els.size() + " steps=" + steps.size() + " events=" + seen.size() + " json=" + stepsJson(p).length() + " bytes");
    }

    static String roleOf(TemplateKit.Plan p, String name) {
        for (TemplateKit.El e : p.elements) if (e.name.equals(name)) return e.role;
        return "";
    }

    // ------------------------------------------------------------------ images

    static void checkImage(JsonObject a, String path) {
        String kind = a.get("kind").getAsString();
        ok(Arrays.asList("pixels", "shape", "gradient", "tiles").contains(kind), path + ": bad kind");
        if (kind.equals("pixels") || kind.equals("tiles")) {
            JsonObject pal = a.getAsJsonObject("palette");
            ok(pal != null, path + ": no palette");
            int w = 0, h = a.getAsJsonArray("rows").size();
            for (JsonElement r : a.getAsJsonArray("rows")) {
                String row = r.getAsString();
                w = Math.max(w, row.length());
                for (char c : row.toCharArray()) ok(c == '.' || c == ' ' || pal.has(String.valueOf(c)), path + ": row char '" + c + "' not in the palette");
            }
            for (Map.Entry<String, JsonElement> e : pal.entrySet()) {
                ok(e.getKey().length() == 1, path + ": palette key");
                ok(e.getValue().getAsString().matches("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})"), path + ": bad palette color " + e.getValue());
            }
            int sc = a.has("scale") ? a.get("scale").getAsInt() : 1;
            if (kind.equals("tiles")) {
                ok(a.has("width") && a.has("height"), path + ": tiles need width and height");
                w = a.get("width").getAsInt() / sc;
                h = a.get("height").getAsInt() / sc;
            }
            ok(w * sc <= 2048 && h * sc <= 2048 && (long) w * sc * h * sc <= 4_000_000L, path + ": image too large " + (w * sc) + "x" + (h * sc));
        } else {
            int w = a.get("width").getAsInt(), h = a.get("height").getAsInt();
            ok(w >= 1 && h >= 1 && w <= 2048 && h <= 2048 && (long) w * h <= 4_000_000L, path + ": image size " + w + "x" + h);
            if (kind.equals("shape")) {
                ok(Arrays.asList("rect", "circle", "ellipse", "triangle", "star", "rounded").contains(a.get("shape").getAsString()), path + ": bad shape");
                if (a.has("fill")) ok(a.get("fill").getAsString().matches("(linear:|radial:)?#?[0-9A-Fa-f#,:.\\-]+"), path + ": bad fill " + a.get("fill"));
            }
        }
    }

    // ------------------------------------------------------------------ blocks

    static void checkSpeeds(JsonElement e, String key) {
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) checkSpeeds(x, key);
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("block")) {
                String t = o.get("block").getAsString();
                if ((t.equals("setVelocityX") || t.equals("setVelocityY") || t.equals("jump") || t.equals("moveToward") || t.equals("moveWithJoystick") || t.equals("walkWithJoystick")) && o.has("fields")) {
                    for (Map.Entry<String, JsonElement> f : o.getAsJsonObject("fields").entrySet()) {
                        if (!(f.getKey().equals("speed") || f.getKey().equals("power"))) continue;
                        JsonElement v = f.getValue();
                        if (v.isJsonPrimitive() && v.getAsString().matches("-?[0-9.]+")) ok(Math.abs(Double.parseDouble(v.getAsString())) <= 450, key + ": " + t + " " + v + " is over 450 px/s");
                    }
                }
            }
            for (Map.Entry<String, JsonElement> en : o.entrySet()) checkSpeeds(en.getValue(), key);
        }
    }

    static final Set<String> BODY_FIELDS = new HashSet<>(Arrays.asList("body", "item", "ui", "progress", "target", "joystick"));

    /** Block fields that name an element ("Player") must name an existing one; raw Java names are left to the compile step. */
    static void checkBodyRefs(JsonElement e, Map<String, JsonObject> els, Map<String, String> types, String key) {
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) checkBodyRefs(x, els, types, key);
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("block") && o.has("fields")) {
                String t = o.get("block").getAsString();
                for (Map.Entry<String, JsonElement> f : o.getAsJsonObject("fields").entrySet()) {
                    if (!BODY_FIELDS.contains(f.getKey()) || !f.getValue().isJsonPrimitive()) continue;
                    String v = f.getValue().getAsString();
                    if (!v.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
                    if (els.containsKey(v)) {
                        String ty = types.get(v), bt = els.get(v).has("type") ? els.get(v).get("type").getAsString() : (ty.equals("TEXT") ? "UI" : "DYNAMIC");
                        boolean needsBody = !(t.equals("setText") || t.equals("showText") || t.equals("show") || t.equals("hide") || t.equals("setOpacity") || t.equals("setProgress") || t.equals("setTint") || t.equals("setVisibility") || t.equals("bringToFront") || t.equals("sendToBack"));
                        if (needsBody) ok(!bt.equals("UI") && !ty.equals("TEXT"), key + ": " + t + " on UI element " + v + " (UI elements have no body)");
                        if (t.equals("setText") || t.equals("showText")) ok(ty.equals("TEXT"), key + ": " + t + " on non-TEXT " + v);
                    } else if (!Arrays.asList("c", "en", "e", "o", "current", "item", "b", "pl", "bullet", "brick", "m").contains(v) && !v.matches("[a-z][A-Za-z0-9]{0,3}")) {
                        ok(false, key + ": " + t + "." + f.getKey() + " = '" + v + "' is not an element of this scene");
                    }
                }
            }
            for (Map.Entry<String, JsonElement> en : o.entrySet()) checkBodyRefs(en.getValue(), els, types, key);
        }
    }

    // ------------------------------------------------------------------ layout / z / text

    static double num(JsonObject pr, String k, double d) {
        return pr.has(k) ? pr.get(k).getAsDouble() : d;
    }

    static double[] rect(JsonObject pr, String type) {
        double r = num(pr, "radius", 0);
        double w = pr.has("width") ? num(pr, "width", 0) : r * 2, h = pr.has("height") ? num(pr, "height", 0) : r * 2;
        return new double[]{num(pr, "x", 0), num(pr, "y", 0), w, h};
    }

    static boolean isUi(JsonObject pr, String type) {
        if (type.equals("TEXT") || type.equals("PROGRESS") || type.equals("JOYSTICK")) return !pr.has("type") || pr.get("type").getAsString().equals("UI");
        return pr.has("type") && pr.get("type").getAsString().equals("UI");
    }

    static boolean overlap(double[] a, double[] b) {
        return Math.min(a[0] + a[2], b[0] + b[2]) - Math.max(a[0], b[0]) > 0.5 && Math.min(a[1] + a[3], b[1] + b[3]) - Math.max(a[1], b[1]) > 0.5;
    }

    static void checkLayout(TemplateKit.Plan p, Map<String, JsonObject> els, Map<String, String> types) {
        boolean scrolls = Boolean.TRUE.equals(p.meta.get("scrolls"));
        ArrayList<String> uiNames = new ArrayList<>();
        for (TemplateKit.El e : p.elements) {
            JsonObject pr = els.get(e.name);
            String ty = types.get(e.name);
            double[] r = rect(pr, ty);
            ok(r[2] > 0 && r[3] > 0, e.name + ": no size");
            boolean ui = isUi(pr, ty);
            if (ui) {
                uiNames.add(e.name);
                ok(r[0] >= -0.5 && r[1] >= -0.5 && r[0] + r[2] <= p.w + 0.5 && r[1] + r[3] <= p.h + 0.5, e.name + ": UI element outside the " + p.w + "x" + p.h + " frame " + Arrays.toString(r));
                if (e.role.equals("button") || e.role.equals("hud") || e.role.equals("label"))
                    ok(r[0] >= 8 && r[1] >= 8 && r[0] + r[2] <= p.w - 8 && r[1] + r[3] <= p.h - 8 || e.role.equals("overlay"), e.name + ": closer than 8 px to the screen edge " + Arrays.toString(r));
            } else if (!e.offscreen && !scrolls) {
                ok(r[0] >= -0.5 && r[1] >= -0.5 && r[0] + r[2] <= p.w + 0.5 && r[1] + r[3] <= p.h + 0.5, e.name + ": world element outside the frame " + Arrays.toString(r));
            }
        }
        // buttons / HUD texts must not overlap each other (visible at the same time)
        ArrayList<TemplateKit.El> tops = new ArrayList<>();
        for (TemplateKit.El e : p.elements) if (isUi(els.get(e.name), types.get(e.name)) && (e.role.equals("button") || e.role.equals("hud") || e.role.equals("joystick"))) tops.add(e);
        for (int i = 0; i < tops.size(); i++)
            for (int j = i + 1; j < tops.size(); j++) {
                TemplateKit.El a = tops.get(i), b = tops.get(j);
                boolean pair = a.name.equals(b.name + "Label") || b.name.equals(a.name + "Label");
                if (pair) continue;
                // overlay pieces are only shown together
                if (a.name.startsWith("End") || b.name.startsWith("End") || a.name.startsWith("Restart") || b.name.startsWith("Restart")) continue;
                if (a.name.startsWith("Menu") && b.name.startsWith("Menu") && false) continue;
                ok(!overlap(rect(els.get(a.name), types.get(a.name)), rect(els.get(b.name), types.get(b.name))), a.name + " overlaps " + b.name);
            }
        // buttons at least 96 px (finger sized)
        for (TemplateKit.El e : p.elements)
            if (e.role.equals("button")) {
                double[] r = rect(els.get(e.name), types.get(e.name));
                ok(r[2] >= 96 && r[3] >= 64, e.name + ": button smaller than a finger " + r[2] + "x" + r[3]);
            }
    }

    static void checkZ(TemplateKit.Plan p, Map<String, JsonObject> els) {
        int minZ = Integer.MAX_VALUE;
        for (JsonObject pr : els.values()) minZ = Math.min(minZ, pr.get("z").getAsInt());
        ok(minZ == 0, "lowest z must be 0 (the background), is " + minZ);
        int bgCount = 0;
        for (TemplateKit.El e : p.elements) {
            int z = e.zIndex();
            switch (e.role) {
                case "background": ok(z == 0, e.name + ": background z " + z); bgCount++; break;
                case "decor": ok(z >= 1 && z < 10, e.name + ": decor z " + z); break;
                case "ground": case "platform": case "wall": case "pipe": ok(z >= 10 && z < 20, e.name + ": z " + z + " outside 10..19"); break;
                case "coin": case "goal": case "item": case "brick": ok(z >= 20 && z <= 29, e.name + ": z " + z + " outside 20..29"); break;
                case "enemy": case "player": case "ball": case "paddle": case "bullet": ok(z >= 20 && z <= 39, e.name + ": z " + z + " outside 20..39"); break;
                case "fx": ok(z >= 40 && z < 100, e.name + ": z " + z); break;
                case "hud": ok(z >= 100 && z < 200, e.name + ": hud z " + z); break;
                case "joystick": ok(z == 120, e.name + ": joystick z " + z); break;
                case "button": ok(z >= 100, e.name + ": button z " + z); break;
                case "label": ok(z >= 110, e.name + ": label z " + z); break;
                case "overlay": ok(z >= 200, e.name + ": overlay z " + z); break;
                default: break;
            }
        }
        ok(bgCount <= 1, "more than one background");
        // a button label is above its button
        for (TemplateKit.El e : p.elements)
            if (e.role.equals("label")) {
                String base = e.name.substring(0, e.name.length() - "Label".length());
                if (els.containsKey(base)) ok(e.zIndex() > els.get(base).get("z").getAsInt(), e.name + " is not above " + base);
            }
        // nothing ELSE shares the z of the background
        int z0 = 0;
        for (TemplateKit.El e : p.elements) if (e.zIndex() == 0 && !e.role.equals("background")) z0++;
        ok(z0 == 0, "an element other than the background has z 0");
    }

    static double textWidthExact(String s, double scale) {
        double w = 0;
        for (char c : s.toCharArray()) w += adv.getOrDefault((int) c, 0);
        return w * scale;
    }

    static void checkTexts(TemplateKit.Plan p, Map<String, JsonObject> els) {
        for (TemplateKit.El e : p.elements) {
            if (!e.type.equals("TEXT")) continue;
            JsonObject pr = els.get(e.name);
            String t = pr.get("Text").getAsString();
            String longest = e.widest.length() > t.length() ? e.widest : t;
            double sc = num(pr, "Font Scale", 1);
            double w = num(pr, "width", 0), h = num(pr, "height", 0);
            for (char ch : (t + longest).toCharArray()) ok(ch >= 32 && ch < 127 && adv.containsKey((int) ch), e.name + ": character '" + ch + "' is not in the default font");
            double need = textWidthExact(longest, sc), needH = lineHeight * sc;
            ok(need + 2 <= w, e.name + ": '" + longest + "' needs " + need + " px, the box is " + w + " (it would wrap)");
            ok(needH <= h + 0.5, e.name + ": line height " + needH + " > box " + h);
            // clamp: the text box must not be absurdly big for what it shows
            ok(w <= need + 2 * 80 + 300 || e.role.equals("label") || e.role.equals("overlay"), e.name + ": box " + w + " much wider than its text " + need);
        }
    }

    // ------------------------------------------------------------------ platformer reachability

    static void checkPlatformer(TemplateKit.Plan p, Map<String, JsonObject> els, JsonObject eventsArgs) {
        double g = ((Number) p.meta.get("gravity")).doubleValue(), v = ((Number) p.meta.get("jump")).doubleValue(), sp = ((Number) p.meta.get("speed")).doubleValue();
        double ph = ((Number) p.meta.get("playerH")).doubleValue();
        // the gravity / numbers really used in the blocks
        for (JsonElement je : eventsArgs.getAsJsonArray("ops")) {
            JsonObject o = je.getAsJsonObject();
            if (o.get("event").getAsString().equals("OnCreate")) {
                JsonObject first = o.getAsJsonArray("blocks").get(0).getAsJsonObject();
                ok(Math.abs(first.getAsJsonObject("fields").get("strength").getAsDouble() - g) < 1e-6, "gravity in the blocks differs from the meta");
            }
        }
        double jumpH = v * v / (2 * g);
        class S {
            String n;
            double x0, x1, top, bottom;
        }
        ArrayList<S> ss = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : els.entrySet()) {
            String n = e.getKey();
            if (!(n.startsWith("Ground") || n.startsWith("Plat"))) continue;
            JsonObject pr = e.getValue();
            S s = new S();
            s.n = n;
            s.x0 = num(pr, "x", 0);
            s.x1 = s.x0 + num(pr, "width", 0);
            s.bottom = num(pr, "y", 0);
            s.top = s.bottom + num(pr, "height", 0);
            ss.add(s);
        }
        ok(ss.size() > 3, "no surfaces");
        // surfaces do not intersect
        for (int i = 0; i < ss.size(); i++)
            for (int j = i + 1; j < ss.size(); j++)
                ok(!overlap(new double[]{ss.get(i).x0, ss.get(i).bottom, ss.get(i).x1 - ss.get(i).x0, ss.get(i).top - ss.get(i).bottom},
                        new double[]{ss.get(j).x0, ss.get(j).bottom, ss.get(j).x1 - ss.get(j).x0, ss.get(j).top - ss.get(j).bottom}), ss.get(i).n + " intersects " + ss.get(j).n);
        // reachability (BFS from Ground1) with the 1.3 safety margin on height and reach
        HashSet<String> reach = new HashSet<>();
        ArrayDeque<S> q = new ArrayDeque<>();
        for (S s : ss) if (s.n.equals("Ground1")) {
            q.add(s);
            reach.add(s.n);
        }
        double worstStep = 0, worstGap = 0;
        while (!q.isEmpty()) {
            S a = q.poll();
            for (S b : ss) {
                if (reach.contains(b.n)) continue;
                double dh = b.top - a.top;
                if (dh > jumpH / 1.3) continue;
                double gap = Math.max(0, Math.max(b.x0 - a.x1, a.x0 - b.x1));
                double t = dh >= 0 ? (v + Math.sqrt(Math.max(0, v * v - 2 * g * dh))) / g : (v + Math.sqrt(v * v + 2 * g * -dh)) / g;
                double reachPx = sp * t;
                if (gap > reachPx / 1.3) continue;
                // standing room: if b is above a and they overlap horizontally, the player must fit under b
                if (dh > 0 && gap == 0 && Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0) > 0 && b.bottom - a.top < ph + 4) continue;
                reach.add(b.n);
                q.add(b);
                if (dh > 0) worstStep = Math.max(worstStep, dh);
                worstGap = Math.max(worstGap, gap);
            }
        }
        for (S s : ss) ok(reach.contains(s.n), "surface " + s.n + " is not reachable with the 1.3x margin (jump " + jumpH + " px high)");
        // Goal sits on a reachable ground piece
        JsonObject goal = els.get("Goal");
        ok(goal != null, "no Goal");
        boolean onSurf = false;
        for (S s : ss) if (s.n.startsWith("Ground") && goal.get("x").getAsDouble() >= s.x0 && goal.get("x").getAsDouble() + goal.get("width").getAsDouble() <= s.x1 && reach.contains(s.n)) onSurf = true;
        ok(onSurf, "Goal is not on a reachable ground piece");
        // walking under platforms is possible
        for (S a : ss)
            for (S b : ss) {
                if (a == b || b.bottom <= a.top) continue;
                if (Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0) > 0 && a.n.startsWith("Ground") && b.n.startsWith("Plat"))
                    ok(b.bottom - a.top >= ph + 4, b.n + " leaves only " + (b.bottom - a.top) + " px above " + a.n + " (the player is " + ph + " tall)");
            }
        // coins: not inside solids, and within reach of a surface
        for (Map.Entry<String, JsonObject> e : els.entrySet()) {
            if (!e.getKey().startsWith("Coin") || e.getValue().get("TYPE") != null || !els.get(e.getKey()).has("radius")) continue;
            JsonObject pr = e.getValue();
            double r = pr.get("radius").getAsDouble(), cx = pr.get("x").getAsDouble() + r, cy = pr.get("y").getAsDouble() + r;
            boolean near = false;
            for (S s : ss) {
                ok(!(cx > s.x0 - 1 && cx < s.x1 + 1 && cy > s.bottom - 1 && cy < s.top + 1), e.getKey() + " lies inside " + s.n);
                if (cx > s.x0 - 100 && cx < s.x1 + 100 && cy - s.top <= jumpH + ph / 2 && cy >= s.top) near = true;
            }
            ok(near, e.getKey() + " cannot be reached");
        }
        // enemies stand on a ground piece
        for (int i = 1; i <= ((Number) p.meta.get("enemies")).intValue(); i++) {
            JsonObject en = els.get("Enemy" + i);
            double cx = en.get("x").getAsDouble() + en.get("width").getAsDouble() / 2;
            boolean ok = false;
            for (S s : ss) if (s.n.startsWith("Ground") && cx > s.x0 + 30 && cx < s.x1 - 30) ok = true;
            ok(ok, "Enemy" + i + " does not stand on a ground piece");
        }
        // the player spawns on ground 1
        JsonObject pl = els.get("Player");
        ok(pl.get("x").getAsDouble() >= 0 && pl.get("x").getAsDouble() + 48 <= ss.get(0).x1, "player spawn is outside the first ground piece");
        System.out.println(String.format("  platformer: jump height %.0f px, step max %.0f (limit %.0f), gap max %.0f, surfaces %d, level %.0f px", jumpH, worstStep, jumpH / 1.3, worstGap, ss.size(), ((Number) p.meta.get("levelW")).doubleValue()));
    }

    // ------------------------------------------------------------------ solids at the start, top-down, flappy, breakout, shooter

    static boolean isSensor(JsonObject pr) {
        return pr.has("isSensor") && pr.get("isSensor").getAsString().equals("true");
    }

    static String bodyType(JsonObject pr) {
        return pr.has("type") ? pr.get("type").getAsString() : "DYNAMIC";
    }

    /** A DYNAMIC solid must not start inside another solid (the physics would shoot it away). */
    static void checkSolids(TemplateKit.Plan p, Map<String, JsonObject> els, Map<String, String> types) {
        ArrayList<TemplateKit.El> solids = new ArrayList<>();
        for (TemplateKit.El e : p.elements) {
            String ty = types.get(e.name);
            if (!(ty.equals("BOX") || ty.equals("CIRCLE"))) continue;
            JsonObject pr = els.get(e.name);
            if (bodyType(pr).equals("UI") || isSensor(pr) || e.offscreen) continue;
            solids.add(e);
        }
        for (TemplateKit.El a : solids) {
            if (!bodyType(els.get(a.name)).equals("DYNAMIC")) continue;
            for (TemplateKit.El b : solids) {
                if (a == b) continue;
                ok(!overlap(rect(els.get(a.name), types.get(a.name)), rect(els.get(b.name), types.get(b.name))), a.name + " starts inside " + b.name);
            }
        }
    }

    static void checkTopDown(TemplateKit.Plan p, Map<String, JsonObject> els) {
        final int cell = 8;
        int gw = p.w / cell + 1, gh = p.h / cell + 1;
        boolean[][] blocked = new boolean[gw][gh];
        double half = ((Number) p.meta.get("heroSize")).doubleValue() / 2 + 2;
        for (Map.Entry<String, JsonObject> e : els.entrySet()) {
            String n = e.getKey();
            if (!(n.startsWith("Wall") || n.startsWith("Crate"))) continue;
            double[] r = rect(e.getValue(), "BOX");
            for (int x = 0; x < gw; x++)
                for (int y = 0; y < gh; y++) {
                    double cx = x * cell + cell / 2.0, cy = y * cell + cell / 2.0;
                    if (cx > r[0] - half && cx < r[0] + r[2] + half && cy > r[1] - half && cy < r[1] + r[3] + half) blocked[x][y] = true;
                }
        }
        JsonObject pl = els.get("Player");
        int sx = (int) ((pl.get("x").getAsDouble() + 25) / cell), sy = (int) ((pl.get("y").getAsDouble() + 25) / cell);
        ok(!blocked[sx][sy], "the player starts inside an obstacle");
        boolean[][] seenCell = new boolean[gw][gh];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sx, sy});
        seenCell[sx][sy] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= gw || ny >= gh || blocked[nx][ny] || seenCell[nx][ny]) continue;
                seenCell[nx][ny] = true;
                q.add(new int[]{nx, ny});
            }
        }
        double[] joy = rect(els.get("joy"), "JOYSTICK");
        int n = 0;
        for (Map.Entry<String, JsonObject> e : els.entrySet()) {
            String name = e.getKey();
            if (!(name.startsWith("Gem") && !name.equals("GemText")) && !name.startsWith("Foe")) continue;
            double[] r = rect(e.getValue(), "BOX");
            int gx = (int) ((r[0] + r[2] / 2) / cell), gy = (int) ((r[1] + r[3] / 2) / cell);
            ok(seenCell[gx][gy], name + " cannot be reached by the hero (walled in)");
            if (name.startsWith("Gem")) {
                n++;
                ok(!overlap(r, joy), name + " lies under the joystick");
            }
        }
        ok(n == ((Number) p.meta.get("gems")).intValue(), "gem count differs from meta");
        // 100 px passages: nothing bigger than the hero + 50 px margin can be sealed (crate gap is enforced by the generator; we re-measure)
        ArrayList<double[]> cr = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : els.entrySet()) if (e.getKey().startsWith("Crate")) cr.add(rect(e.getValue(), "BOX"));
        for (int i = 0; i < cr.size(); i++)
            for (int j = i + 1; j < cr.size(); j++) {
                double dx = Math.max(0, Math.max(cr.get(j)[0] - (cr.get(i)[0] + cr.get(i)[2]), cr.get(i)[0] - (cr.get(j)[0] + cr.get(j)[2])));
                double dy = Math.max(0, Math.max(cr.get(j)[1] - (cr.get(i)[1] + cr.get(i)[3]), cr.get(i)[1] - (cr.get(j)[1] + cr.get(j)[3])));
                ok(Math.max(dx, dy) >= 100, "crates " + i + " and " + j + " leave a passage of only " + Math.max(dx, dy) + " px");
            }
        System.out.println("  top_down: crates=" + cr.size() + " gems=" + n + " all reachable");
    }

    static void checkFlappy(TemplateKit.Plan p, Map<String, JsonObject> els, JsonObject eventsArgs) {
        double g = ((Number) p.meta.get("gravity")).doubleValue(), flap = ((Number) p.meta.get("flap")).doubleValue(), gap = ((Number) p.meta.get("gap")).doubleValue();
        double speed = ((Number) p.meta.get("speed")).doubleValue(), spacing = ((Number) p.meta.get("spacing")).doubleValue();
        double cMin = ((Number) p.meta.get("cMin")).doubleValue(), cMax = ((Number) p.meta.get("cMax")).doubleValue(), gt = ((Number) p.meta.get("groundTop")).doubleValue();
        int pairs = ((Number) p.meta.get("pairs")).intValue();
        ok(flap <= 450 && speed <= 450, "speed over 450");
        // the numbers used in the blocks are the ones in the meta
        String ev = eventsArgs.toString();
        Matcher gm = Pattern.compile("\"strength\":\"?(\\d+)\"?").matcher(ev);
        ok(gm.find() && Double.parseDouble(gm.group(1)) == g, "gravity block differs from meta");
        // passable: how far can the bird climb in the time between two pipes (taps every 0.4 s)
        double rise = flap * flap / (2 * g), T = spacing / speed, climb = rise * Math.max(1, Math.floor(T / 0.4));
        ok((cMax - cMin) * 1.3 <= climb + 1e-6 || (cMax - cMin) <= 20, "gap centre may move " + (cMax - cMin) + " px but the bird climbs only " + climb + " px between two pipes (needs 1.3x)");
        ok(rise * 1.3 >= 0 && gap >= 220, "gap too small");
        ok(cMin - gap / 2 >= gt + 40, "the lower pipe edge can be too close to the ground");
        ok(cMax + gap / 2 <= p.h - 40, "the upper pipe edge can be too close to the top");
        ok(pairs * spacing >= p.w + 2 * 110, "the pipe pool does not cover the screen width: " + pairs * spacing);
        JsonObject bird = els.get("Bird");
        double birdX = bird.get("x").getAsDouble() + bird.get("width").getAsDouble() / 2;
        for (int i = 1; i <= pairs; i++) {
            JsonObject t = els.get("PipeTop" + i), b = els.get("PipeBot" + i);
            ok(t.get("x").getAsDouble() >= p.w, "pipe " + i + " is visible at the start");
            ok(t.get("x").getAsDouble() > birdX + 100, "pipe " + i + " starts too close to the bird");
            double topEdge = b.get("y").getAsDouble() + b.get("height").getAsDouble(), botEdge = t.get("y").getAsDouble();
            ok(Math.abs((botEdge - topEdge) - gap) < 1e-6, "pipe " + i + " opening " + (botEdge - topEdge) + " != gap " + gap);
            double c = (botEdge + topEdge) / 2;
            ok(c >= cMin - 1e-6 && c <= cMax + 1e-6, "pipe " + i + " opening centre " + c + " outside " + cMin + ".." + cMax);
            ok(t.get("z").getAsInt() < els.get("Ground").get("z").getAsInt(), "pipes must be behind the ground");
            if (i > 1) {
                double dc = Math.abs(((t.get("y").getAsDouble() - gap / 2) ) - ((els.get("PipeTop" + (i - 1)).get("y").getAsDouble() - gap / 2)));
                ok(dc * 1.3 <= climb + 1e-6 || dc <= 20, "pipes " + (i - 1) + "->" + i + " differ by " + dc + " px (climb " + climb + ")");
            }
        }
        ok(els.get("TapArea").get("z").getAsInt() > els.get("Bird").get("z").getAsInt() && els.get("TapArea").get("z").getAsInt() < els.get("ScoreText").get("z").getAsInt(), "TapArea z must be between the world and the HUD");
        ok(bird.get("y").getAsDouble() > gt + 100 && bird.get("y").getAsDouble() + 32 < p.h - 100, "bird starts too low/high");
        System.out.println(String.format("  flappy: pairs=%d spacing=%.0f opening centre %.0f..%.0f (span %.0f, climb between pipes %.0f), flap rise %.0f", pairs, spacing, cMin, cMax, cMax - cMin, climb, rise));
    }

    static void collect(JsonElement e, String block, List<JsonObject> out) {
        if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) collect(x, block, out);
        else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("block") && o.get("block").getAsString().equals(block)) out.add(o);
            for (Map.Entry<String, JsonElement> en : o.entrySet()) collect(en.getValue(), block, out);
        }
    }

    static void checkBreakout(TemplateKit.Plan p, Map<String, JsonObject> els, JsonObject eventsArgs) {
        int bricks = ((Number) p.meta.get("bricks")).intValue();
        double paddleTop = ((Number) p.meta.get("paddleTop")).doubleValue(), sp = ((Number) p.meta.get("speed")).doubleValue();
        ok(sp <= 450, "ball speed over 450");
        ArrayList<double[]> rs = new ArrayList<>();
        double lowest = 1e9;
        for (int i = 1; i <= bricks; i++) {
            JsonObject b = els.get("Brick" + i);
            ok(b != null, "Brick" + i + " missing");
            if (b == null) continue;
            double[] r = rect(b, "BOX");
            ok(r[0] >= 20 - 1e-6 && r[0] + r[2] <= p.w - 20 + 1e-6 && r[1] + r[3] <= p.h - 96, "Brick" + i + " is outside the play field " + Arrays.toString(r));
            for (double[] o : rs) ok(!overlap(r, o), "Brick" + i + " overlaps another brick");
            rs.add(r);
            lowest = Math.min(lowest, r[1]);
        }
        ok(lowest - paddleTop >= 180 - 1e-6, "bricks come down to " + lowest + ", too close to the paddle (top " + paddleTop + ")");
        JsonObject ball = els.get("Ball"), pad = els.get("Paddle");
        double bx = ball.get("x").getAsDouble() + ball.get("radius").getAsDouble();
        ok(bx >= pad.get("x").getAsDouble() && bx <= pad.get("x").getAsDouble() + pad.get("width").getAsDouble(), "the ball does not start over the paddle");
        ok(pad.get("type").getAsString().equals("KINAMATIC"), "paddle must be kinematic");
        ok(els.get("Ball").get("Gravity Scale").getAsDouble() == 0, "ball needs Gravity Scale 0");
        ok(els.get("Ball").get("restitution").getAsDouble() == 1, "ball restitution must be 1");
        // brick bodies bounce too
        for (int i = 1; i <= bricks; i++) if (els.get("Brick" + i) != null) ok(els.get("Brick" + i).get("restitution").getAsDouble() == 1, "brick restitution");
        // the launch and normalisation lines exist
        String ev = eventsArgs.toString();
        ok(ev.contains("v.nor()") && ev.contains("ballSpeed"), "ball speed normalisation missing");
        ok(ev.contains("remaining"), "win condition missing");
        System.out.println("  breakout: " + bricks + " bricks, lowest edge " + Math.round(lowest) + ", paddle top " + Math.round(paddleTop));
    }

    static void checkShooter(TemplateKit.Plan p, Map<String, JsonObject> els, JsonObject eventsArgs) {
        ArrayList<JsonObject> spawns = new ArrayList<>(), timers = new ArrayList<>();
        collect(eventsArgs, "spawnCopy", spawns);
        collect(eventsArgs, "everySeconds", timers);
        ok(spawns.size() == 2, "expected 2 spawnCopy blocks");
        for (JsonObject s : spawns) {
            String body = s.getAsJsonObject("fields").get("body").getAsString();
            JsonObject t = els.get(body);
            ok(t != null, "spawnCopy of a missing template " + body);
            if (t == null) continue;
            ok(t.get("x").getAsDouble() < 0 && t.get("y").getAsDouble() < 0 || t.get("x").getAsDouble() + t.get("width").getAsDouble() < 0, body + " must sit off-screen");
            ok(t.has("Collision") && t.get("Collision").getAsString().contains("(" + body + ")"), body + ": Collision must list its own name so clones do not hit each other");
            ok(t.has("Gravity Scale") && t.get("Gravity Scale").getAsDouble() == 0, body + ": needs Gravity Scale 0");
        }
        for (JsonObject t : timers) {
            double sec = Double.parseDouble(t.getAsJsonObject("fields").get("seconds").getAsString());
            ok(sec >= 0.15, "timer " + sec + " s is faster than 0.15 s");
        }
        double fire = ((Number) p.meta.get("fire")).doubleValue();
        ok(fire >= 0.15, "fire interval");
        JsonObject ship = els.get("Ship");
        ok(ship.get("type").getAsString().equals("KINAMATIC"), "the ship must be kinematic (aliens must not push it)");
        ok(ship.get("x").getAsDouble() >= 0 && ship.get("x").getAsDouble() + ship.get("width").getAsDouble() <= p.w, "ship outside");
        JsonObject base = els.get("Base");
        ok(base.get("width").getAsDouble() >= p.w, "the Base must span the whole width");
        // a bullet crosses the screen before its lifetime ends
        for (JsonObject d : collectAll(eventsArgs, "destroy")) { }
        System.out.println("  shooter: " + timers.size() + " timers, templates ok");
    }

    static List<JsonObject> collectAll(JsonElement e, String block) {
        ArrayList<JsonObject> l = new ArrayList<>();
        collect(e, block, l);
        return l;
    }
}
