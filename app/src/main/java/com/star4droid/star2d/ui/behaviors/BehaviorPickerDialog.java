package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.behaviors.BehaviorCatalog;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** "Add behavior" dialog: grid or list with icon + description, search, filter (All / UI / Physical / Project). */
public final class BehaviorPickerDialog {
	private BehaviorPickerDialog() {}

	public interface Pick {
		void on(BehaviorMeta meta);
	}

	private static boolean grid = true;

	public static void show(Context c, BehaviorEnv env, Set<String> already, Pick pick) {
		List<BehaviorMeta> all = BehaviorCatalog.all(env.projectPath);
		LinearLayout col = Ui.column(c);
		col.setPadding(Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10), 0);

		LinearLayout top = Ui.row(c);
		top.setGravity(Gravity.CENTER_VERTICAL);
		EditText search = Ui.input(c, "", Tr.t("search", "Search"));
		top.addView(search, Ui.weight(1));
		ImageView toggle = Ui.iconButton(c, grid ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid, Tr.t("bhv_toggle_view", "Grid / list"), null);
		top.addView(toggle);
		col.addView(top, Ui.matchWrap());

		final String[] filters = {"all", "ui", "physics", "project"};
		final String[] titles = {Tr.t("bhv_f_all", "All"), Tr.t("bhv_f_ui", "UI"), Tr.t("bhv_f_physics", "Physical"), Tr.t("bhv_f_project", "Project")};
		final int[] sel = {env.isUi() ? 1 : 0};
		HorizontalScrollView hs = new HorizontalScrollView(c);
		hs.setHorizontalScrollBarEnabled(false);
		LinearLayout chips = Ui.row(c);
		hs.addView(chips);
		col.addView(hs, Ui.matchWrap());

		ScrollView sv = new ScrollView(c);
		LinearLayout list = Ui.column(c);
		sv.addView(list);
		col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.55f)));

		AlertDialog d = new AlertDialog.Builder(c)
				.setTitle(Tr.t("bhv_add_title", "Add behavior"))
				.setView(col)
				.setNegativeButton(Tr.t("cancel", "Cancel"), null)
				.create();

		Runnable[] refill = new Runnable[1];
		Runnable buildChips = () -> {
			chips.removeAllViews();
			for (int i = 0; i < filters.length; i++) {
				final int idx = i;
				TextView t = Ui.text(c, titles[i], 13, sel[0] == i ? Ui.TEXT : Ui.TEXT_2);
				t.setPadding(Ui.dp(c, 14), Ui.dp(c, 7), Ui.dp(c, 14), Ui.dp(c, 7));
				t.setBackground(Ui.rounded(sel[0] == i ? Ui.ACCENT_DARK : Ui.SURFACE_3, Ui.dp(c, 16)));
				LinearLayout.LayoutParams lp = Ui.matchWrap();
				lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
				lp.setMarginEnd(Ui.dp(c, 6));
				lp.topMargin = Ui.dp(c, 4);
				lp.bottomMargin = Ui.dp(c, 4);
				t.setOnClickListener(v -> {
					sel[0] = idx;
					refill[0].run();
				});
				chips.addView(t, lp);
			}
		};
		refill[0] = () -> {
			buildChips.run();
			String q = search.getText().toString().trim().toLowerCase();
			list.removeAllViews();
			String f = filters[sel[0]];
			ArrayList<BehaviorMeta> shown = new ArrayList<>();
			for (BehaviorMeta m : all) {
				if (f.equals("ui") && !(m.targets.equals(BehaviorMeta.UI) || m.targets.equals(BehaviorMeta.BOTH))) continue;
				if (f.equals("physics") && !(m.targets.equals(BehaviorMeta.PHYSICS) || m.targets.equals(BehaviorMeta.BOTH))) continue;
				if (f.equals("project") && m.builtin) continue;
				if (!q.isEmpty() && !(m.title().toLowerCase().contains(q) || m.desc.toLowerCase().contains(q) || m.category.toLowerCase().contains(q) || m.id.toLowerCase().contains(q))) continue;
				shown.add(m);
			}
			if (shown.isEmpty()) {
				TextView e = Ui.text(c, f.equals("project") ? Tr.t("bhv_no_project", "No project behaviors yet. Create one from the Files panel (New > Behavior).") : Tr.t("bhv_none_found", "Nothing found"), 13, Ui.TEXT_3);
				e.setPadding(Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 8), 0);
				list.addView(e);
				return;
			}
			String cat = null;
			LinearLayout gridRow = null;
			for (BehaviorMeta m : shown) {
				String group = m.builtin ? m.category : Tr.t("bhv_project", "Project");
				if (!group.equals(cat)) {
					cat = group;
					TextView h = Ui.sectionLabel(c, group);
					list.addView(h);
					gridRow = null;
				}
				View card = grid ? gridCard(c, env, m, already.contains(m.id), d, pick) : listRow(c, env, m, already.contains(m.id), d, pick);
				if (grid) {
					if (gridRow == null || gridRow.getChildCount() >= 2) {
						gridRow = Ui.row(c);
						list.addView(gridRow, Ui.matchWrap());
					}
					gridRow.addView(card, Ui.weight(1));
				} else list.addView(card, Ui.matchWrap());
			}
			if (grid && gridRow != null && gridRow.getChildCount() == 1) gridRow.addView(new View(c), Ui.weight(1));
		};
		toggle.setOnClickListener(v -> {
			grid = !grid;
			toggle.setImageResource(grid ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid);
			refill[0].run();
		});
		search.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int x) {}
			@Override public void onTextChanged(CharSequence s, int a, int b, int x) { refill[0].run(); }
			@Override public void afterTextChanged(Editable s) {}
		});
		refill[0].run();
		d.show();
	}

	private static View gridCard(Context c, BehaviorEnv env, BehaviorMeta m, boolean has, AlertDialog d, Pick pick) {
		LinearLayout card = Ui.column(c);
		card.setGravity(Gravity.CENTER_HORIZONTAL);
		card.setPadding(Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
		card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, 1));
		LinearLayout.LayoutParams lp = Ui.weight(1);
		lp.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
		card.setLayoutParams(lp);
		card.addView(BehaviorIcons.view(c, m.icon, 36, has ? Ui.TEXT_3 : Ui.ACCENT));
		TextView t = Ui.text(c, m.title(), 13.5f, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		t.setGravity(Gravity.CENTER);
		t.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
		card.addView(t);
		TextView ds = Ui.text(c, m.desc, 11, Ui.TEXT_3);
		ds.setGravity(Gravity.CENTER);
		ds.setMaxLines(3);
		ds.setEllipsize(android.text.TextUtils.TruncateAt.END);
		card.addView(ds);
		note(c, card, env, m, has);
		card.setOnClickListener(v -> {
			d.dismiss();
			pick.on(m);
		});
		return card;
	}

	private static View listRow(Context c, BehaviorEnv env, BehaviorMeta m, boolean has, AlertDialog d, Pick pick) {
		LinearLayout row = Ui.row(c);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
		row.setBackground(Ui.ripple(0, Ui.dp(c, 8)));
		row.addView(BehaviorIcons.view(c, m.icon, 34, has ? Ui.TEXT_3 : Ui.ACCENT));
		LinearLayout txt = Ui.column(c);
		txt.setPadding(Ui.dp(c, 12), 0, 0, 0);
		TextView t = Ui.text(c, m.title(), 14.5f, Ui.TEXT);
		t.setTypeface(Typeface.DEFAULT_BOLD);
		txt.addView(t);
		TextView ds = Ui.text(c, m.desc, 12, Ui.TEXT_3);
		ds.setMaxLines(3);
		txt.addView(ds);
		note(c, txt, env, m, has);
		row.addView(txt, Ui.weight(1));
		row.setOnClickListener(v -> {
			d.dismiss();
			pick.on(m);
		});
		return row;
	}

	/** Warning (does not fit this element) / "added" marker. */
	private static void note(Context c, LinearLayout parent, BehaviorEnv env, BehaviorMeta m, boolean has) {
		String why = m.misfit(env.bodyType);
		if (has) {
			TextView n = Ui.text(c, Tr.t("bhv_added", "Already added"), 11, Ui.TEXT_3);
			parent.addView(n);
		} else if (why != null) {
			TextView n = Ui.text(c, "⚠ " + why, 11, Ui.WARNING);
			parent.addView(n);
		}
	}
}
