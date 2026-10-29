package com.star4droid.star2d.behaviors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON stored with an element:
 * <pre>
 * behaviors  = [{"type":"Floating","params":{"amplitudeY":20},"enabled":true}, ...]
 * scriptVars = {"speed":300,"hpBar":"hp_bar","enemies":["e1","e2"],"tint":"#FF8800"}
 * </pre>
 * Only values the user changed are stored; a missing key means "the code's default".
 */
public final class BehaviorData {
	private BehaviorData() {}

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public static final class Entry {
		public String type = "";
		public Map<String, Object> params = new LinkedHashMap<String, Object>();
		public boolean enabled = true;

		public Entry() {}

		public Entry(String type) {
			this.type = type;
		}
	}

	public static List<Entry> parse(String json) {
		List<Entry> out = new ArrayList<Entry>();
		if (json == null || json.trim().length() == 0) return out;
		try {
			List<Entry> l = GSON.fromJson(json, new TypeToken<List<Entry>>() {}.getType());
			if (l != null)
				for (Entry e : l) {
					if (e == null || e.type == null || e.type.length() == 0) continue;
					if (e.params == null) e.params = new LinkedHashMap<String, Object>();
					out.add(e);
				}
		} catch (RuntimeException ex) {
			// broken JSON: treat as empty
		}
		return out;
	}

	public static String toJson(List<Entry> entries) {
		if (entries == null || entries.isEmpty()) return "";
		return GSON.toJson(entries);
	}

	public static Map<String, Object> parseVars(String json) {
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		if (json == null || json.trim().length() == 0) return out;
		try {
			Map<String, Object> m = GSON.fromJson(json, new TypeToken<LinkedHashMap<String, Object>>() {}.getType());
			if (m != null) out.putAll(m);
		} catch (RuntimeException ex) {
			// ignore
		}
		return out;
	}

	public static String varsToJson(Map<String, Object> vars) {
		if (vars == null || vars.isEmpty()) return "";
		return GSON.toJson(vars);
	}

	public static Entry find(List<Entry> list, String type) {
		for (Entry e : list) if (e.type.equals(type)) return e;
		return null;
	}

	/** One behavior type at most per element (its events live in a folder named after the type). */
	public static boolean has(String json, String type) {
		return find(parse(json), type) != null;
	}

	public static Gson gson() {
		return GSON;
	}
}
