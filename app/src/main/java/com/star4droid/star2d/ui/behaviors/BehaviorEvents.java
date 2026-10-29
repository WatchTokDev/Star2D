package com.star4droid.star2d.ui.behaviors;

import android.os.Handler;
import android.os.Looper;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.Adapters.VisualScriptingDialog;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Items.Editor;
import com.star4droid.star2d.behaviors.BehaviorCodeGen;
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

	/** One row of the Events panel: [behaviorId, behaviorTitle, event name, signature, hasCode]. GL thread. */
	public static List<String[]> listFor(String element, String behaviorsJson) {
		List<String[]> out = new ArrayList<>();
		Project p = Bridge.editor().getProject();
		String scene = Bridge.editor().getScene();
		for (com.star4droid.star2d.behaviors.BehaviorData.Entry e : com.star4droid.star2d.behaviors.BehaviorData.parse(behaviorsJson)) {
			BehaviorMeta m = com.star4droid.star2d.behaviors.BehaviorCatalog.find(e.type, p.getPath());
			if (m == null) continue;
			for (BehaviorMeta.EventSpec ev : m.events)
				out.add(new String[] {m.id, m.title(), ev.name, ev.signature(), hasCode(path(p, scene, element, m.id, ev.name)) ? "1" : ""});
		}
		return out;
	}
}
