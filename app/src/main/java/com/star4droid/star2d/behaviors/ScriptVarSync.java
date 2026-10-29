package com.star4droid.star2d.behaviors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the stored variables of a script ({@code scriptVars} of an element, {@code scriptvars.json} of a scene) in step with the
 * fields written in its Java code. Pure Java (no Android), used by the editor when the code editor is left and by the tests.
 *
 * <pre>
 * {"speed":420, "enemies":["e1"], "$auto":{"speed":"float","enemies":"List&lt;PlayerItem&gt;","label":"String"}}
 * </pre>
 * Plain keys are the values the user set (overrides of the code's initializer). {@code "$auto"} lists the fields that were found in
 * the code the last time (name -> type): a field that is no longer there (deleted / renamed) loses its entry and its value, a value
 * that does not fit the new type is dropped, everything else is kept. Keys that never were in {@code $auto} (added by the user or the
 * AI) are never touched.
 */
public final class ScriptVarSync {
	private ScriptVarSync() {}

	public static final String AUTO = "$auto";

	/** @param json current stored map ("" = none) @param fields the fields found in the code (see {@link SourceScanner#scanScript}) @return the new stored map, "" when empty */
	public static String sync(String json, List<VarSpec> fields) {
		Map<String, Object> vars = BehaviorData.parseVars(json);
		Object old = vars.remove(AUTO);
		Map<String, VarSpec> now = new LinkedHashMap<String, VarSpec>();
		for (VarSpec f : fields) now.put(f.name, f);
		if (old instanceof Map)
			for (Object name : ((Map<?, ?>) old).keySet())
				if (!now.containsKey(String.valueOf(name))) vars.remove(String.valueOf(name));
		for (VarSpec f : fields) {
			Object v = vars.get(f.name);
			if (v != null && !fits(f, v)) vars.remove(f.name);
		}
		if (!now.isEmpty()) {
			Map<String, String> auto = new LinkedHashMap<String, String>();
			for (VarSpec f : fields) auto.put(f.name, f.signature());
			vars.put(AUTO, auto);
		}
		return BehaviorData.varsToJson(vars);
	}

	/** Same JSON as {@link BehaviorData#varsToJson} of the parsed text: what {@link #sync} compares against. */
	public static String canonical(String json) {
		return BehaviorData.varsToJson(BehaviorData.parseVars(json));
	}

	/** Is there any stored value (a key that is not a bookkeeping key)? */
	public static boolean hasValues(Map<String, Object> vars) {
		for (String k : vars.keySet()) if (!k.startsWith("$")) return true;
		return false;
	}

	/** The map without bookkeeping keys (what the AI tool shows). */
	public static Map<String, Object> visible(Map<String, Object> vars) {
		Map<String, Object> out = new LinkedHashMap<String, Object>();
		for (Map.Entry<String, Object> e : vars.entrySet()) if (!e.getKey().startsWith("$")) out.put(e.getKey(), e.getValue());
		return out;
	}

	/** Names of the fields recorded as found in code. */
	public static java.util.Set<String> autoNames(Map<String, Object> vars) {
		Object o = vars.get(AUTO);
		java.util.Set<String> out = new java.util.LinkedHashSet<String>();
		if (o instanceof Map) for (Object k : ((Map<?, ?>) o).keySet()) out.add(String.valueOf(k));
		return out;
	}

	/** Does a stored value still suit the variable (after the type or the enum constants changed)? */
	static boolean fits(VarSpec v, Object val) {
		if (v.list) {
			if (!(val instanceof List)) return false;
			for (Object o : (List<?>) val) if (!fitsScalar(v, o)) return false;
			return true;
		}
		return fitsScalar(v, val);
	}

	private static boolean fitsScalar(VarSpec v, Object o) {
		switch (v.kind) {
			case BOOL: return o instanceof Boolean;
			case STRING:
			case IMAGE:
			case SOUND:
			case REF: return o instanceof String;
			case COLOR: return o instanceof String && Colors.normalize((String) o) != null;
			case ENUM: return o instanceof String && v.options.contains(o);
			default: return o instanceof Number;
		}
	}
}
