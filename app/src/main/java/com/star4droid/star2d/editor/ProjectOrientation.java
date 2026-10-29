package com.star4droid.star2d.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.editor.Project;

import java.io.File;
import java.util.Arrays;

/**
 * The orientation and logic size of a PROJECT, read straight from its configs/*.json files (never from an editor's flag,
 * which can be stale while a scene is loading), and a helper that gives a brand-new scene a config that matches it.
 * Convention (same as the runtime): "or" is "" / "landscape" or "portrait"; logicWidth = LONG side, logicHeight = SHORT side
 * in BOTH orientations (portrait swaps them when used).
 */
public final class ProjectOrientation {
    private ProjectOrientation() {}

    public static final class Info {
        public boolean landscape = true;
        /** 0 when the reference config has no (valid) size. */
        public float longSide = 0, shortSide = 0;
        /** name of the scene the values came from, "" when there is none */
        public String source = "";
        /** sceneColor of the reference scene, or "" */
        public String sceneColor = "";
    }

    /** The reference config of the project: scene1 first, else any other normal scene. {@code except} = the scene being created (ignored). */
    public static Info read(Project p, String except) {
        Info info = new Info();
        try {
            File one = new File(p.getConfig("scene1"));
            File dir = one.getParentFile();
            File pick = null;
            if (!"scene1".equals(except) && one.isFile() && one.length() > 2) pick = one;
            if (pick == null && dir != null) {
                File[] all = dir.listFiles();
                if (all != null) {
                    Arrays.sort(all);
                    for (File f : all) {
                        String n = f.getName();
                        if (!f.isFile() || !n.endsWith(".json") || n.startsWith("__") || f.length() <= 2) continue;
                        if (except != null && n.equals(except + ".json")) continue;
                        pick = f;
                        break;
                    }
                }
            }
            if (pick == null) return info;
            JsonObject o = JsonParser.parseString(readText(pick)).getAsJsonObject();
            String or = str(o, "or");
            info.landscape = or.isEmpty() || or.equals("landscape");
            float a = num(o, "logicWidth"), b = num(o, "logicHeight");
            if (a > 0 && b > 0) {
                info.longSide = Math.max(a, b);
                info.shortSide = Math.min(a, b);
            }
            info.sceneColor = str(o, "sceneColor");
            String n = pick.getName();
            info.source = n.endsWith(".json") ? n.substring(0, n.length() - 5) : n;
        } catch (Exception ignored) {
        }
        return info;
    }

    /**
     * Writes configs/&lt;scene&gt;.json for a NEW scene (does nothing when the file already has content):
     * the default scene.json + the orientation ({@code landscape} null = the project's) + the project's logic size,
     * so the new scene is exactly like the other scenes instead of "whatever the phone screen is".
     * Returns the info that was used.
     */
    public static Info seed(Project p, String scene, Boolean landscape) {
        Info info = read(p, scene);
        try {
            File f = new File(p.getConfig(scene));
            if (f.isFile() && f.length() > 2) return info;
            JsonObject o;
            try {
                o = JsonParser.parseString(Gdx.files.internal("scene.json").readString()).getAsJsonObject();
            } catch (Exception e) {
                o = new JsonObject();
            }
            boolean land = landscape != null ? landscape : info.landscape;
            o.addProperty("or", land ? "" : "portrait");
            if (info.longSide > 0) {
                o.addProperty("logicWidth", trim(info.longSide));
                o.addProperty("logicHeight", trim(info.shortSide));
            }
            FileHandle fh = Gdx.files.absolute(f.getAbsolutePath());
            fh.writeString(o.toString(), false);
            info.landscape = land;
        } catch (Exception ignored) {
        }
        return info;
    }

    /** What configs/&lt;scene&gt;.json says right now: "landscape", "portrait" or null when there is no readable config. */
    public static String stored(Project p, String scene) {
        try {
            File f = new File(p.getConfig(scene));
            if (!f.isFile() || f.length() <= 2) return null;
            JsonObject o = JsonParser.parseString(readText(f)).getAsJsonObject();
            String or = str(o, "or");
            return or.isEmpty() || or.equals("landscape") ? "landscape" : "portrait";
        } catch (Exception e) {
            return null;
        }
    }

    private static String readText(File f) throws java.io.IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private static String trim(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.valueOf(v);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static float num(JsonObject o, String k) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? 0 : Float.parseFloat(e.getAsString());
        } catch (Exception ex) {
            return 0;
        }
    }
}
