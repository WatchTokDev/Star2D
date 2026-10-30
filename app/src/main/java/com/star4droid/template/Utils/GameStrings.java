package com.star4droid.template.Utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.Project;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Game translations. The project file strings/strings.json:
 * { "default": "en", "languages": ["en","ar"], "keys": { "play": {"en":"Play","ar":"العب"}, ... } }
 * tr("play") gives the text in the current language (then the default language, then the key itself).
 * The player's choice is remembered between runs. A TEXT element whose text is "@play" shows the translation and follows language changes.
 */
public class GameStrings {
	public static volatile int version = 0;
	private static String current;
	private static String def = "en";
	private static final List<String> languages = new ArrayList<>();
	private static final Map<String, Map<String, String>> keys = new LinkedHashMap<>();
	private static String loadedFor;

	public static void init(Project project) {
		String key = project.getPath();
		if (key.equals(loadedFor)) return;
		loadedFor = key;
		languages.clear();
		keys.clear();
		try {
			File f = new File(project.get("strings") + "strings.json");
			if (f.isFile()) {
				Map<String, Object> m = new Gson().fromJson(Gdx.files.absolute(f.getAbsolutePath()).readString("UTF-8"), new TypeToken<Map<String, Object>>() {}.getType());
				if (m.get("default") != null) def = String.valueOf(m.get("default"));
				Object l = m.get("languages");
				if (l instanceof List) for (Object o : (List<?>) l) languages.add(String.valueOf(o));
				Object k = m.get("keys");
				if (k instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) k).entrySet()) {
					Map<String, String> t = new LinkedHashMap<>();
					if (e.getValue() instanceof Map) for (Map.Entry<?, ?> x : ((Map<?, ?>) e.getValue()).entrySet()) t.put(String.valueOf(x.getKey()), String.valueOf(x.getValue()));
					keys.put(String.valueOf(e.getKey()), t);
				}
			}
		} catch (Throwable ignored) {}
		if (languages.isEmpty()) languages.add(def);
		String saved = null;
		try { saved = prefs().getString("language", null); } catch (Throwable ignored) {}
		current = saved != null && languages.contains(saved) ? saved : (languages.contains(def) ? def : languages.get(0));
		version++;
	}

	private static Preferences prefs() { return Gdx.app.getPreferences("star2d_strings"); }

	public static String language() { return current == null ? def : current; }

	public static List<String> languages() { return languages; }

	public static void setLanguage(String code) {
		if (code == null || !languages.contains(code) || code.equals(current)) return;
		current = code;
		try { Preferences p = prefs(); p.putString("language", code); p.flush(); } catch (Throwable ignored) {}
		version++;
	}

	/** Next language in the list (for a language button). */
	public static void nextLanguage() {
		if (languages.size() < 2) return;
		setLanguage(languages.get((languages.indexOf(language()) + 1) % languages.size()));
	}

	public static String tr(Object keyObj) {
		String key = String.valueOf(keyObj);
		if (key.startsWith("@")) key = key.substring(1);
		Map<String, String> t = keys.get(key);
		if (t == null) return key;
		String v = t.get(language());
		if (v == null || v.isEmpty()) v = t.get(def);
		return v == null || v.isEmpty() ? key : v;
	}

	public static boolean has(String key) { return keys.containsKey(key.startsWith("@") ? key.substring(1) : key); }
}
