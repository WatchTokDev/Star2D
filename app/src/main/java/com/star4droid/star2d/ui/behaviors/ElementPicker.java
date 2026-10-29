package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.star4droid.star2d.behaviors.ElementTypes;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** "Which element?" list with search, filtered by the type the variable accepts (ProgressItem -> only PROGRESS elements...). */
final class ElementPicker {
	private ElementPicker() {}

	interface Pick {
		/** @param name chosen element, "" for "None", null when the dialog was cancelled */
		void on(String name);
	}

	static void show(VarEditors.Host h, VarSpec spec, String current, Pick pick) {
		Context c = h.ctx();
		Bridge.query(() -> {
			List<String[]> l = BehaviorEnv.elements();
			if (ElementTypes.UI_NODE.equals(spec.elemType)) l.addAll(BehaviorEnv.uiNodes()); // "holder/nodeId"
			return l;
		}, all -> {
			final List<String> names = new ArrayList<>();
			final List<String> labels = new ArrayList<>();
			for (String[] e : all)
				if (ElementTypes.accepts(spec.elemType, e[1])) {
					names.add(e[0]);
					labels.add(e[0] + "   · " + (e.length > 2 ? e[2] : e[1]));
				}
			LinearLayout col = Ui.column(c);
			col.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14), 0);
			EditText search = Ui.input(c, "", Tr.t("search", "Search"));
			col.addView(search, Ui.matchWrap());
			ListView lv = new ListView(c);
			col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.45f)));
			final List<String> shownNames = new ArrayList<>(), shownLabels = new ArrayList<>();
			ArrayAdapter<String> ad = new ArrayAdapter<String>(c, android.R.layout.simple_list_item_1, shownLabels) {
				@Override public View getView(int pos, View convert, ViewGroup parent) {
					TextView t = (TextView) super.getView(pos, convert, parent);
					t.setTextColor(shownNames.get(pos).equals(current) ? Ui.ACCENT : Ui.TEXT);
					t.setTextSize(14);
					return t;
				}
			};
			lv.setAdapter(ad);
			Runnable refill = () -> {
				String q = search.getText().toString().trim().toLowerCase();
				shownNames.clear();
				shownLabels.clear();
				shownNames.add("");
				shownLabels.add("— " + Tr.t("ui_none", "None"));
				for (int i = 0; i < names.size(); i++)
					if (q.isEmpty() || labels.get(i).toLowerCase().contains(q)) {
						shownNames.add(names.get(i));
						shownLabels.add(labels.get(i));
					}
				ad.notifyDataSetChanged();
			};
			refill.run();
			search.addTextChangedListener(new TextWatcher() {
				@Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
				@Override public void onTextChanged(CharSequence s, int a, int b, int d) { refill.run(); }
				@Override public void afterTextChanged(Editable s) {}
			});
			AlertDialog d = new AlertDialog.Builder(c)
					.setTitle(spec.displayLabel() + " · " + spec.elemType)
					.setView(col)
					.setNegativeButton(Tr.t("cancel", "Cancel"), (x, w) -> pick.on(null))
					.create();
			lv.setOnItemClickListener((p, v, pos, id) -> {
				d.dismiss();
				pick.on(shownNames.get(pos));
			});
			d.show();
		});
	}
}
