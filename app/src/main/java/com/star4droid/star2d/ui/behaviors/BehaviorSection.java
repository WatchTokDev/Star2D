package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorData;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The bottom of the Properties panel for the selected element:
 * 1. "Script variables": public variables of its Body Script (inspector values override the code's defaults),
 * 2. "Additional behaviors": names with an info icon, an add button, and the parameters of each behavior.
 * Everything is stored in the element's properties: {@code behaviors} and {@code scriptVars} (JSON, see BehaviorData).
 */
public final class BehaviorSection implements VarEditors.Host {
	private static final HashSet<String> COLLAPSED = new HashSet<>();
	private static final HashSet<String> EXPANDED = new HashSet<>();

	private final EditorShell shell;
	private final Context ctx;
	private final LinearLayout container;
	private final String element;
	private String bjson, vjson;
	private BehaviorEnv env;

	public BehaviorSection(EditorShell shell, LinearLayout content, String element, String behaviorsJson, String varsJson) {
		this.shell = shell;
		this.ctx = content.getContext();
		this.element = element;
		this.bjson = behaviorsJson == null ? "" : behaviorsJson;
		this.vjson = varsJson == null ? "" : varsJson;
		container = Ui.column(ctx);
		content.addView(container, Ui.matchWrap());
		Bridge.query(() -> BehaviorEnv.collect(element), e -> {
			env = e;
			rebuild();
		});
	}

	@Override public Context ctx() { return ctx; }

	@Override public BehaviorEnv env() { return env; }

	/** The property changed behind our back (undo, AI...). */
	public void externalChange(String key, String value) {
		if (value == null) value = "";
		if (key.equals("behaviors") && !value.equals(bjson)) {
			bjson = value;
			rebuild();
		} else if (key.equals("scriptVars") && !value.equals(vjson)) {
			vjson = value;
			rebuild();
		}
	}

	// ------------------------------------------------------------------------------------------------ storage

	private void commit(String key, String json) {
		Bridge.query(() -> PropertyEditing.applyString(Bridge.app(), key, json), err -> {
			if (err != null) Ui.toast(ctx, err);
		});
	}

	private void saveBehaviors(List<BehaviorData.Entry> entries) {
		bjson = BehaviorData.toJson(entries);
		commit("behaviors", bjson);
		rebuild();
		shell.events.onSelectionChanged();
	}

	private void saveVars(Map<String, Object> vars) {
		vjson = BehaviorData.varsToJson(vars);
		commit("scriptVars", vjson);
		rebuild();
	}

	// ------------------------------------------------------------------------------------------------ cards

	private LinearLayout card(String title, String key, View... actions) {
		LinearLayout card = Ui.column(ctx);
		card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(ctx, 10), Ui.BORDER, 1));
		container.addView(card, Ui.margins(Ui.matchWrap(), ctx, 0, 6, 0, 4));
		LinearLayout head = Ui.row(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4));
		head.setBackground(Ui.ripple(0, Ui.dp(ctx, 10)));
		TextView t = Ui.text(ctx, title, 14, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		head.addView(t, Ui.weight(1));
		for (View a : actions) head.addView(a);
		ImageView arrow = Ui.smallIcon(ctx, COLLAPSED.contains(key) ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more, Ui.TEXT_2, 20);
		head.addView(arrow);
		card.addView(head);
		LinearLayout body = Ui.column(ctx);
		body.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), Ui.dp(ctx, 8));
		body.setVisibility(COLLAPSED.contains(key) ? View.GONE : View.VISIBLE);
		card.addView(body);
		head.setOnClickListener(x -> {
			boolean col = body.getVisibility() == View.VISIBLE;
			body.setVisibility(col ? View.GONE : View.VISIBLE);
			Ui.setDirIcon(arrow, col ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more);
			if (col) COLLAPSED.add(key); else COLLAPSED.remove(key);
		});
		return body;
	}

	private void rebuild() {
		if (env == null) return;
		container.removeAllViews();
		buildScriptVars();
		buildBehaviors();
	}

	// ------------------------------------------------------------------------------------------------ script variables

	private void buildScriptVars() {
		if (env.scriptFile == null) return;
		SourceScanner.Result r = SourceScanner.scan(BehaviorCatalog.readFile(new File(env.scriptFile)));
		if (r.vars.isEmpty()) return;
		Map<String, Object> ov = BehaviorData.parseVars(vjson);
		ImageView info = Ui.iconButton(ctx, R.drawable.ic_s2_info, Tr.t("bhv_vars_info", "About"), v ->
				Dialogs.longText(ctx, Tr.t("bhv_vars_title", "Script variables"), Tr.t("bhv_vars_help",
						"Every public variable of this element's Body Script is listed here. A value set here replaces the one written in the code (reset brings the code's value back). Elements can be chosen from the scene: declare e.g. public ProgressItem hpBar; in the script and pick it here.")));
		LinearLayout body = card(Tr.t("bhv_vars_title", "Script variables") + " · " + r.className, "vars", info);
		for (VarSpec v : r.vars) {
			body.addView(VarEditors.field(this, v, ov.get(v.name), val -> {
				Map<String, Object> m = BehaviorData.parseVars(vjson);
				if (val == null) m.remove(v.name); else m.put(v.name, val);
				saveVars(m);
			}));
		}
	}

	// ------------------------------------------------------------------------------------------------ behaviors

	private void buildBehaviors() {
		List<BehaviorData.Entry> entries = BehaviorData.parse(bjson);
		ImageView add = Ui.iconButton(ctx, R.drawable.ic_s2_add, Tr.t("bhv_add_title", "Add behavior"), v -> {
			HashSet<String> has = new HashSet<>();
			for (BehaviorData.Entry e : entries) has.add(e.type);
			BehaviorPickerDialog.show(ctx, env, has, this::add);
		});
		LinearLayout body = card(Tr.t("bhv_title", "Additional behaviors"), "behaviors", add);
		if (entries.isEmpty()) {
			TextView t = Ui.text(ctx, Tr.t("bhv_empty", "None yet. Tap + to add ready-made behaviors (movement, shooting, effects...)."), 12.5f, Ui.TEXT_3);
			t.setPadding(Ui.dp(ctx, 2), Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));
			body.addView(t);
			return;
		}
		for (int i = 0; i < entries.size(); i++) body.addView(row(entries, i), Ui.matchWrap());
	}

	/** Why the later of two behaviors that set the same physics value wins. */
	private String setsWarning(List<BehaviorData.Entry> entries, int idx, BehaviorMeta m) {
		if (m.sets.isEmpty() || !entries.get(idx).enabled) return null;
		for (int j = 0; j < idx; j++) {
			BehaviorData.Entry o = entries.get(j);
			if (!o.enabled) continue;
			BehaviorMeta om = BehaviorCatalog.find(o.type, env.projectPath);
			if (om == null) continue;
			for (Map.Entry<String, String> e : m.sets.entrySet()) {
				String ov = om.sets.get(e.getKey());
				if (ov != null && !ov.equals(e.getValue()))
					return om.title() + " " + Tr.t("bhv_sets_also", "also sets") + " " + e.getKey() + "=" + ov + " → " + Tr.t("bhv_sets_wins", "this one wins") + " (" + e.getKey() + "=" + e.getValue() + ")";
			}
		}
		return null;
	}

	private View row(List<BehaviorData.Entry> entries, int idx) {
		BehaviorData.Entry e = entries.get(idx);
		BehaviorMeta m = BehaviorCatalog.find(e.type, env.projectPath);
		LinearLayout box = Ui.column(ctx);
		box.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(ctx, 8)));
		box.setLayoutParams(Ui.margins(Ui.matchWrap(), ctx, 0, 3, 0, 3));
		String key = element + "|" + e.type;

		LinearLayout head = Ui.row(ctx);
		head.setGravity(Gravity.CENTER_VERTICAL);
		head.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 2), Ui.dp(ctx, 2));
		head.setBackground(Ui.ripple(0, Ui.dp(ctx, 8)));
		head.addView(m == null ? Ui.smallIcon(ctx, R.drawable.ic_s2_bug, Ui.DANGER, 26) : BehaviorIcons.view(ctx, m.icon, 26, e.enabled ? Ui.ACCENT : Ui.TEXT_3));
		TextView name = Ui.text(ctx, m == null ? e.type : m.title(), 14, e.enabled ? Ui.TEXT : Ui.TEXT_3);
		name.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6), 0);
		head.addView(name, Ui.weight(1));

		List<String> warns = new ArrayList<>();
		if (m == null) warns.add(Tr.t("bhv_missing", "This behavior no longer exists (its file was deleted). Remove it."));
		else {
			String misfit = m.misfit(env.bodyType);
			if (misfit != null) warns.add(misfit);
			String sw = setsWarning(entries, idx, m);
			if (sw != null) warns.add(sw);
		}
		if (!warns.isEmpty()) head.addView(Ui.smallIcon(ctx, R.drawable.ic_s2_bug, Ui.WARNING, 20));
		if (m != null) {
			SwitchCompat sw = new SwitchCompat(ctx);
			sw.setChecked(e.enabled);
			sw.setOnCheckedChangeListener((b, on) -> {
				e.enabled = on;
				saveBehaviors(entries);
			});
			head.addView(sw);
			head.addView(Ui.iconButton(ctx, R.drawable.ic_s2_info, Tr.t("bhv_info", "Info"), v -> info(m)));
		}
		head.setOnClickListener(v -> {
			if (EXPANDED.contains(key)) EXPANDED.remove(key); else EXPANDED.add(key);
			rebuild();
		});
		box.addView(head);

		for (String w : warns) {
			TextView t = Ui.text(ctx, "⚠ " + w, 11.5f, Ui.WARNING);
			t.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 8), Ui.dp(ctx, 4));
			box.addView(t);
		}
		if (m != null && !m.exclusive.isEmpty() && !e.enabled) {
			TextView t = Ui.text(ctx, Tr.t("bhv_off_group", "Off. Turn on from code with") + ": item.setBehaviorEnabled(\"" + m.id + "\", true)", 11, Ui.TEXT_3);
			t.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 8), Ui.dp(ctx, 4));
			box.addView(t);
		}

		if (EXPANDED.contains(key)) {
			LinearLayout inner = Ui.column(ctx);
			inner.setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10), Ui.dp(ctx, 8));
			box.addView(inner);
			if (m != null) {
				if (!m.requirementsText().isEmpty()) {
					TextView t = Ui.text(ctx, m.requirementsText(), 11.5f, Ui.TEXT_3);
					t.setPadding(0, 0, 0, Ui.dp(ctx, 4));
					inner.addView(t);
				}
				for (VarSpec v : m.vars) {
					inner.addView(VarEditors.field(this, v, e.params.get(v.name), val -> {
						if (val == null) e.params.remove(v.name); else e.params.put(v.name, val);
						saveBehaviors(entries);
					}));
				}
				if (m.vars.isEmpty()) inner.addView(Ui.text(ctx, Tr.t("bhv_no_params", "No parameters."), 12, Ui.TEXT_3));
			}
			LinearLayout tools = Ui.row(ctx);
			tools.setGravity(Gravity.CENTER_VERTICAL);
			if (idx > 0) tools.addView(Ui.iconButton(ctx, R.drawable.ic_s2_arrow_up, Tr.t("bhv_up", "Move up (runs earlier)"), v -> {
				entries.add(idx - 1, entries.remove(idx));
				saveBehaviors(entries);
			}));
			if (idx < entries.size() - 1) tools.addView(Ui.iconButton(ctx, R.drawable.ic_s2_arrow_down, Tr.t("bhv_down", "Move down (runs later)"), v -> {
				entries.add(idx + 1, entries.remove(idx));
				saveBehaviors(entries);
			}));
			if (m != null && !m.events.isEmpty())
				tools.addView(Ui.button(ctx, R.drawable.ic_s2_events, Tr.t("bhv_events", "Events"), false, v -> eventsDialog(m)));
			if (m != null && !m.sets.isEmpty() && !env.isUi())
				tools.addView(Ui.button(ctx, R.drawable.ic_s2_check, Tr.t("bhv_apply", "Apply to element"), false, v -> applySets(m)));
			if (m != null && !e.params.isEmpty())
				tools.addView(Ui.iconButton(ctx, R.drawable.ic_s2_refresh, Tr.t("bhv_reset_all", "Reset all parameters"), v -> {
					e.params.clear();
					saveBehaviors(entries);
				}));
			tools.addView(Ui.iconButton(ctx, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v ->
					Dialogs.confirm(ctx, Tr.t("delete", "Delete"), (m == null ? e.type : m.title()) + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"), () -> {
						entries.remove(idx);
						EXPANDED.remove(key);
						saveBehaviors(entries);
					})));
			inner.addView(tools, Ui.matchWrap());
		}
		return box;
	}

	// ------------------------------------------------------------------------------------------------ adding

	private void add(BehaviorMeta m) {
		List<BehaviorData.Entry> entries = BehaviorData.parse(bjson);
		if (BehaviorData.find(entries, m.id) != null) {
			Ui.toast(ctx, Tr.t("bhv_already", "Already added to this element"));
			return;
		}
		List<BehaviorData.Entry> clash = new ArrayList<>();
		if (!m.exclusive.isEmpty())
			for (BehaviorData.Entry e : entries) {
				BehaviorMeta o = BehaviorCatalog.find(e.type, env.projectPath);
				if (o != null && o.exclusive.equals(m.exclusive)) clash.add(e);
			}
		if (clash.isEmpty()) {
			entries.add(new BehaviorData.Entry(m.id));
			saveBehaviors(entries);
			EXPANDED.add(element + "|" + m.id);
			rebuild();
			return;
		}
		StringBuilder names = new StringBuilder();
		for (BehaviorData.Entry e : clash) {
			BehaviorMeta o = BehaviorCatalog.find(e.type, env.projectPath);
			names.append(names.length() > 0 ? ", " : "").append(o == null ? e.type : o.title());
		}
		String msg = names + " " + Tr.t("bhv_and", "and") + " " + m.title() + " " + Tr.t("bhv_both_control", "both control") + " \"" + m.exclusive + "\".\n\n"
				+ Tr.t("bhv_conflict_help", "Replace: the old one is removed. Keep both: the new one is added switched OFF; switch with item.setBehaviorEnabled(\"Name\", true) (it turns the others of the group off).");
		AlertDialog d = new AlertDialog.Builder(ctx)
				.setTitle(Tr.t("bhv_conflict", "Conflict"))
				.setMessage(msg)
				.setPositiveButton(Tr.t("bhv_replace", "Replace"), (x, w) -> {
					int at = entries.indexOf(clash.get(0));
					entries.removeAll(clash);
					entries.add(Math.min(at, entries.size()), new BehaviorData.Entry(m.id));
					saveBehaviors(entries);
					EXPANDED.add(element + "|" + m.id);
					rebuild();
				})
				.setNeutralButton(Tr.t("bhv_keep_both", "Keep both (only one enabled)"), (x, w) -> {
					BehaviorData.Entry n = new BehaviorData.Entry(m.id);
					n.enabled = false;
					entries.add(n);
					saveBehaviors(entries);
				})
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.create();
		d.show();
	}

	/** Bakes the physics settings of a behavior (@Sets) into the saved element properties. */
	private void applySets(BehaviorMeta m) {
		Bridge.query(() -> {
			PropertySet<String, Object> ps = PropertySet.getPropertySet(Bridge.editor().getSelectedActor());
			int n = 0;
			for (Map.Entry<String, String> e : m.sets.entrySet()) {
				String prop = BehaviorMeta.propertyOf(e.getKey());
				if (prop == null || ps == null || !ps.containsKey(prop)) continue;
				String v = e.getValue();
				if (e.getKey().equals("type")) PropertyEditing.applySpinner(Bridge.app(), prop, v.toUpperCase());
				else if (e.getKey().equals("gravityScale")) {
					try { PropertyEditing.applyFloat(Bridge.app(), prop, Float.parseFloat(v)); } catch (NumberFormatException ex) { continue; }
				} else PropertyEditing.applyBoolean(Bridge.app(), prop, String.valueOf(Boolean.parseBoolean(v)));
				n++;
			}
			return n;
		}, n -> Ui.toast(ctx, Tr.t("bhv_applied", "Properties updated") + ": " + n));
	}

	// ------------------------------------------------------------------------------------------------ dialogs

	private void eventsDialog(BehaviorMeta m) {
		LinearLayout col = Ui.column(ctx);
		col.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 4), Ui.dp(ctx, 12), 0);
		Bridge.query(() -> {
			List<String[]> l = BehaviorEvents.listFor(element, bjson);
			List<String[]> mine = new ArrayList<>();
			for (String[] r : l) if (r[0].equals(m.id)) mine.add(r);
			return mine;
		}, rows -> {
			AlertDialog[] dlg = new AlertDialog[1];
			for (String[] r : rows) {
				LinearLayout line = Ui.row(ctx);
				line.setGravity(Gravity.CENTER_VERTICAL);
				line.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8));
				line.setBackground(Ui.ripple(0, Ui.dp(ctx, 8)));
				TextView t = Ui.text(ctx, r[3], 14, Ui.TEXT);
				t.setTypeface(Typeface.MONOSPACE);
				line.addView(t, Ui.weight(1));
				if (!r[4].isEmpty()) line.addView(Ui.text(ctx, "●", 12, Ui.SUCCESS));
				line.setOnClickListener(v -> {
					if (dlg[0] != null) dlg[0].dismiss();
					shell.panels.hide();
					BehaviorEvents.open(element, m.id, r[2]);
				});
				col.addView(line, Ui.matchWrap());
			}
			dlg[0] = new AlertDialog.Builder(ctx).setTitle(m.title() + " · " + Tr.t("bhv_events", "Events")).setView(col)
					.setNegativeButton(Tr.t("close", "Close"), null).show();
		});
	}

	private void info(BehaviorMeta m) {
		StringBuilder sb = new StringBuilder();
		sb.append(m.desc).append("\n\n");
		sb.append(Tr.t("bhv_works_on", "Works on")).append(": ").append(m.targets.equals(BehaviorMeta.UI) ? "UI" : (m.targets.equals(BehaviorMeta.PHYSICS) ? Tr.t("bhv_f_physics", "Physical") : "UI + " + Tr.t("bhv_f_physics", "Physical"))).append('\n');
		if (!m.exclusive.isEmpty()) sb.append(Tr.t("bhv_group", "Exclusive group")).append(": ").append(m.exclusive).append('\n');
		if (!m.requirementsText().isEmpty()) sb.append(m.requirementsText()).append('\n');
		if (!m.events.isEmpty()) {
			sb.append('\n').append(Tr.t("bhv_events", "Events")).append(":\n");
			for (BehaviorMeta.EventSpec ev : m.events) sb.append("  ").append(ev.signature()).append('\n');
		}
		sb.append("\nv").append(m.version);
		if (!m.author.isEmpty()) sb.append("  ·  ").append(m.author);
		sb.append("\n").append(m.builtin ? Tr.t("bhv_builtin", "Built-in") : Tr.t("bhv_project", "Project") + ": " + m.fqn);
		AlertDialog.Builder b = new AlertDialog.Builder(ctx).setTitle(m.title()).setMessage(sb.toString()).setPositiveButton(Tr.t("close", "Close"), null);
		if (!m.builtin) {
			b.setNeutralButton(Tr.t("bhv_export", "Export .s2b"), (x, w) -> BehaviorShare.export(ctx, env.projectPath, m));
			b.setNegativeButton(Tr.t("bhv_edit_code", "Edit code"), (x, w) -> {
				shell.panels.hide();
				shell.host().openJava(m.file);
			});
		}
		b.show();
	}
}
