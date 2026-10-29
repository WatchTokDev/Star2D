package com.star4droid.star2d.ui.behaviors;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorData;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * Public variables of the scene script (java/com/star4droid/Game/SceneScript/&lt;scene&gt;Script.java) in the Variables panel.
 * Values are saved in Events/&lt;scene&gt;/public/scriptvars.json and replace the defaults of the code.
 */
public final class SceneScriptVars implements VarEditors.Host {
	private final Context ctx;
	private final LinearLayout parent;
	private BehaviorEnv env;
	private String file;
	private Map<String, Object> values;

	private SceneScriptVars(LinearLayout parent) {
		this.parent = parent;
		this.ctx = parent.getContext();
	}

	/** Adds the card to the list when the scene has a script with public variables. */
	public static void add(LinearLayout list) {
		SceneScriptVars s = new SceneScriptVars(list);
		Bridge.query(() -> BehaviorEnv.collect(null), e -> {
			s.env = e;
			s.build();
		});
	}

	@Override public Context ctx() { return ctx; }

	@Override public BehaviorEnv env() { return env; }

	/** Where the overrides of a scene are stored. */
	public static File storage(String projectPath, String scene) {
		return new File(projectPath + "/Events/" + scene + "/public/scriptvars.json");
	}

	private void build() {
		if (env == null || env.sceneScriptFile == null) return;
		SourceScanner.Result r = SourceScanner.scan(BehaviorCatalog.readFile(new File(env.sceneScriptFile)));
		if (r.vars.isEmpty()) return;
		file = storage(env.projectPath, env.scene).getPath();
		values = BehaviorData.parseVars(BehaviorCatalog.readFile(new File(file)));
		LinearLayout card = Ui.column(ctx);
		card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(ctx, 10), Ui.BORDER, 1));
		card.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8));
		TextView t = Ui.text(ctx, Tr.t("bhv_scene_vars", "Scene script variables") + " · " + r.className, 14, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		card.addView(t);
		TextView h = Ui.text(ctx, Tr.t("bhv_scene_vars_hint", "Public variables of the Scene Script. A value set here replaces the one in the code."), 11.5f, Ui.TEXT_3);
		h.setPadding(0, Ui.dp(ctx, 2), 0, Ui.dp(ctx, 4));
		card.addView(h);
		for (VarSpec v : r.vars) {
			card.addView(VarEditors.field(this, v, values.get(v.name), val -> {
				if (val == null) values.remove(v.name); else values.put(v.name, val);
				save();
				parent.removeView(card);
				build();
			}));
		}
		parent.addView(card, Ui.margins(Ui.matchWrap(), ctx, 0, 10, 0, 4));
	}

	private void save() {
		try {
			File f = new File(file);
			f.getParentFile().mkdirs();
			String json = BehaviorData.varsToJson(values);
			if (json.isEmpty()) {
				f.delete();
				return;
			}
			try (FileOutputStream o = new FileOutputStream(f)) {
				o.write(json.getBytes("UTF-8"));
			}
		} catch (Exception e) {
			Ui.toast(ctx, e.toString());
		}
	}
}
