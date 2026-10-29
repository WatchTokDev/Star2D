package com.star4droid.star2d.ui.behaviors;

import android.os.Handler;
import android.os.Looper;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.Adapters.VisualScriptingDialog;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorCodeGen;
import com.star4droid.star2d.behaviors.BehaviorData;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Events a behavior raises (onDeath, onShoot...): files live in Events/&lt;scene&gt;/private/&lt;element&gt;/behaviors/&lt;Behavior&gt;/&lt;event&gt;.java (+ .visual). */
public final class BehaviorEvents {
	private BehaviorEvents() {}

	/** Base path (without extension) of one event's files. GL thread or any (pure string work). */
	public static String path(Project p, String scene, String element, String behaviorId, String event) {
		return p.getEventPath(scene, BehaviorCodeGen.eventBody(element, behaviorId), event);
	}

	/** True if the event has code or blocks. */
	public static boolean hasCode(String base) {
		File j = new File(base + ".java"), v = new File(base + ".visual");
		return (j.exists() && j.length() > 0) || (v.exists() && v.length() > 2);
	}

	/** Opens the code / blocks editor of one behavior event. Any thread. */
	public static void open(String element, String behaviorId, String event) {
		Bridge.gl(() -> {
			Project p = Bridge.editor().getProject();
			String scene = Bridge.editor().getScene();
			String base = path(p, scene, element, behaviorId, event);
			Gdx.files.absolute(base).parent().mkdirs();
			new Handler(Looper.getMainLooper()).post(() -> VisualScriptingDialog.visual(Editor.getCurrentEditor(), event, base, false));
		});
	}

	/**
	 * One row of the Events panel and of the Events dialog of a behavior:
	 * [behaviorId, behavior name (translated), event name (as in the code), event title (translated, with the argument names),
	 * hasCode ("1" or ""), signature]. GL thread.
	 */
	public static List<String[]> listFor(String element, String behaviorsJson) {
		List<String[]> out = new ArrayList<>();
		Project p = Bridge.editor().getProject();
		String scene = Bridge.editor().getScene();
		for (BehaviorData.Entry e : BehaviorData.parse(behaviorsJson)) {
			BehaviorMeta m = BehaviorCatalog.find(e.type, p.getPath());
			if (m == null) continue;
			for (BehaviorMeta.EventSpec ev : m.events)
				out.add(new String[] {m.id, BehaviorText.name(m), ev.name, BehaviorText.eventWithArgs(m, ev),
						hasCode(path(p, scene, element, m.id, ev.name)) ? "1" : "", ev.signature()});
		}
		return out;
	}

	/** Parts of a behavior event file: {behaviorId, event} for {@code .../private/<element>/behaviors/<Id>/<event>.visual|.java}, else null. */
	public static String[] parse(File file) {
		if (file == null) return null;
		String p = file.getAbsolutePath().replace('\\', '/');
		int i = p.lastIndexOf("/behaviors/");
		int pr = p.indexOf("/private/");
		if (i < 0 || pr < 0 || pr > i) return null;
		String[] rest = p.substring(i + "/behaviors/".length()).split("/");
		if (rest.length != 2) return null;
		String ev = rest[1];
		int dot = ev.lastIndexOf('.');
		if (dot > 0) ev = ev.substring(0, dot);
		return new String[] {rest[0], ev};
	}

	/** Project folder of an event file (everything before /Events/), "" if it is not under one. */
	private static String projectOf(File file) {
		String p = file.getAbsolutePath().replace('\\', '/');
		int i = p.indexOf("/Events/");
		return i > 0 ? p.substring(0, i) : "";
	}

	/**
	 * The title of the event in the language of the app ("When hit"), used for the first block of the block editor and for its
	 * title bar; null when the file is not an event of a behavior (those keep using the engine's event names).
	 */
	public static String displayName(File file) {
		String[] be = parse(file);
		if (be == null) return null;
		return BehaviorText.event(be[0], projectOf(file), be[1]);
	}

	/** Parameters of a behavior event as the block editor offers them ("current", then the event's arguments); null if it is not a behavior event. */
	public static String[] params(File file) {
		String[] be = parse(file);
		if (be == null) return null;
		List<String> out = new ArrayList<>();
		out.add("current");
		try {
			BehaviorMeta m = BehaviorCatalog.find(be[0], projectOf(file));
			BehaviorMeta.EventSpec spec = m == null ? null : BehaviorText.spec(m, be[1]);
			if (spec != null) for (String[] a : spec.args) out.add(a[1]);
		} catch (Throwable ignored) {
		}
		return out.toArray(new String[0]);
	}
}
