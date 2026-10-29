package com.star4droid.star2d.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Which behaviors the project uses, to decide which behavior blocks the palette (and the AI) shows.
 *
 * Rule: a behavior's blocks are visible when ANY element of the scene being edited uses the behavior (property
 * {@code behaviors} of the element in {@code scenes/<scene>}), or, when the scene is unknown, any element of the project;
 * and also when the open event already contains one of its blocks, or adds the behavior with the addBehavior block
 * (so a behavior attached at runtime also shows its blocks).
 */
public final class SceneBehaviors {
	private SceneBehaviors() {}

	/** Behavior ids used by the elements of one scene file. */
	public static Set<String> inSceneFile(File sceneFile) {
		Set<String> out = new HashSet<String>();
		try {
			if (sceneFile == null || !sceneFile.isFile()) return out;
			JsonElement root = JsonParser.parseString(BehaviorBlocks.readFile(sceneFile));
			if (!root.isJsonArray()) return out;
			for (JsonElement e : root.getAsJsonArray()) {
				if (!e.isJsonObject()) continue;
				addFrom(out, e.getAsJsonObject().get("behaviors"));
			}
		} catch (Throwable ignored) {
		}
		return out;
	}

	/** Scene given: that scene; null / unknown: every scene of the project. */
	public static Set<String> inProject(String projectPath, String scene) {
		Set<String> out = new HashSet<String>();
		if (projectPath == null) return out;
		File dir = new File(projectPath, "scenes");
		if (scene != null && scene.length() > 0 && new File(dir, scene).isFile()) {
			out.addAll(inSceneFile(new File(dir, scene)));
			return out;
		}
		File[] all = dir.listFiles();
		if (all != null) for (File f : all) if (f.isFile()) out.addAll(inSceneFile(f));
		return out;
	}

	/** The property is a JSON text [{"type":"Shooter",...}] (or already an array). */
	private static void addFrom(Set<String> out, JsonElement prop) {
		if (prop == null || prop.isJsonNull()) return;
		try {
			JsonElement arr = prop;
			if (prop.isJsonPrimitive()) {
				String t = prop.getAsString().trim();
				if (t.length() == 0) return;
				arr = JsonParser.parseString(t);
			}
			if (!arr.isJsonArray()) return;
			JsonArray a = arr.getAsJsonArray();
			for (JsonElement x : a) {
				if (!x.isJsonObject()) continue;
				JsonElement t = x.getAsJsonObject().get("type");
				if (t != null && t.isJsonPrimitive() && t.getAsString().length() > 0) out.add(t.getAsString());
			}
		} catch (Throwable ignored) {
		}
	}

	/** Scene name from a .visual path ".../Events/<scene>/..." or null. */
	public static String sceneOfEventPath(String path) {
		if (path == null) return null;
		String p = path.replace('\\', '/');
		int i = p.indexOf("/Events/");
		if (i < 0) return null;
		String rest = p.substring(i + 8);
		int s = rest.indexOf('/');
		return s > 0 ? rest.substring(0, s) : null;
	}

	/** Project root from a .visual path (the part before "/Events/") or null. */
	public static String projectOfEventPath(String path) {
		if (path == null) return null;
		String p = path.replace('\\', '/');
		int i = p.indexOf("/Events/");
		return i > 0 ? p.substring(0, i) : null;
	}

	/**
	 * Behaviors the text of an event already uses: one of their blocks (title "Shooter.shoot") or an addBehavior block that
	 * names them.
	 */
	public static Set<String> mentionedIn(String eventText, Collection<String> knownIds) {
		Set<String> out = new HashSet<String>();
		if (eventText == null || eventText.length() == 0) return out;
		boolean adds = eventText.contains("addBehavior");
		for (String id : knownIds) {
			if (eventText.contains("\"" + id + ".") || (adds && eventText.contains(id))) out.add(id);
		}
		return out;
	}
}
