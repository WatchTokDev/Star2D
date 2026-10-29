package com.star4droid.star2d.blocks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Block descriptions in several languages, as stored in a custom block: {@code "desc": {"en": "...", "ar": "..."}}.
 * A plain string is read as English; a block without "desc" has none (old files stay valid). No Android code.
 */
public final class BlockDesc {
	private BlockDesc() {}

	/** {lang: text} from the stored value (object, string or null); empty texts and blank languages are dropped. Never null. */
	public static Map<String, String> parse(JsonElement e) {
		Map<String, String> m = new LinkedHashMap<String, String>();
		try {
			if (e == null || e.isJsonNull()) return m;
			if (e.isJsonPrimitive()) {
				String t = e.getAsString();
				if (!t.trim().isEmpty()) m.put("en", t);
			} else if (e.isJsonObject()) {
				for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
					String lang = x.getKey().trim().toLowerCase(Locale.ROOT);
					if (lang.isEmpty() || !x.getValue().isJsonPrimitive()) continue;
					String t = x.getValue().getAsString();
					if (!t.trim().isEmpty()) m.put(lang, t);
				}
			}
		} catch (Exception ignored) {
		}
		return m;
	}

	/** The value to store ({lang: text}); an empty map stores nothing. */
	public static JsonObject toJson(Map<String, String> m) {
		JsonObject o = new JsonObject();
		if (m != null) for (Map.Entry<String, String> e : m.entrySet()) o.addProperty(e.getKey().trim().toLowerCase(Locale.ROOT), e.getValue());
		return o;
	}

	/** The text for a language: that language, else English, else the first one written; null when there is none. */
	public static String pick(Map<String, String> m, String lang) {
		if (m == null || m.isEmpty()) return null;
		String v = lang == null ? null : m.get(lang.toLowerCase(Locale.ROOT));
		if (v != null && !v.trim().isEmpty()) return v;
		v = m.get("en");
		if (v != null && !v.trim().isEmpty()) return v;
		for (String x : m.values()) if (x != null && !x.trim().isEmpty()) return x;
		return null;
	}
}
