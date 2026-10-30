package com.star4droid.star2d.Helpers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

/**
 * Editor side of the game translations: a TEXT written as "@key" shows its translation in the language chosen for the editor
 * (strings.json "editor", by default the first language of the project). Re-reads the file when it changes.
 */
public final class EditorStrings {
    private EditorStrings() {}

    private static String path = "";
    private static long mtime = -1, checked;
    private static String lang = "";
    private static String def = "";
    private static Map<String, Map<String, String>> keys;

    @SuppressWarnings("unchecked")
    private static synchronized void load(String projectPath) {
        long now = System.currentTimeMillis();
        File f = new File(projectPath, "strings/strings.json");
        if (path.equals(projectPath) && now - checked < 400) return;
        checked = now;
        long m = f.isFile() ? f.lastModified() : 0;
        if (path.equals(projectPath) && m == mtime) return;
        path = projectPath;
        mtime = m;
        keys = null;
        lang = "";
        def = "";
        if (m == 0) return;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            in.read(b);
            Map<String, Object> o = new Gson().fromJson(new String(b, "UTF-8"), new TypeToken<Map<String, Object>>() {}.getType());
            List<Object> langs = o.get("languages") instanceof List ? (List<Object>) o.get("languages") : null;
            def = o.get("default") == null ? "" : String.valueOf(o.get("default"));
            String first = langs != null && !langs.isEmpty() ? String.valueOf(langs.get(0)) : def;
            lang = o.get("editor") == null ? first : String.valueOf(o.get("editor"));
            keys = (Map<String, Map<String, String>>) (Map<?, ?>) o.get("keys");
        } catch (Throwable t) {
            keys = null;
        }
    }

    /** The text to show in the editor: "@key" -> its translation (or the key when missing); other text unchanged. */
    public static String show(String projectPath, String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != '@') return text;
        load(projectPath);
        if (keys == null) return text;
        String k = text.substring(1);
        Map<String, String> t = keys.get(k);
        if (t == null) return text;
        String v = t.get(lang);
        if (v == null || v.isEmpty()) v = t.get(def);
        return v == null || v.isEmpty() ? text : v;
    }
}
