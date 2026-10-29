package com.star4droid.star2d.ui.behaviors;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.behaviors.Colors;
import com.star4droid.star2d.behaviors.ElementTypes;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.ColorPickerDialog;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.NumberPad;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspector editors for one {@link VarSpec}: numbers (number pad or keyboard), switches, text, colors, dropdowns, image / sound
 * pickers, element pickers and list editors (add / delete / reset), each with a reset-to-default button.
 * Values use the model described in {@link VarSpec}; {@code null} = "use the code's default".
 */
public final class VarEditors {
	private VarEditors() {}

	/** What the editors need from their surroundings. */
	public interface Host {
		Context ctx();

		BehaviorEnv env();
	}

	public interface Change {
		/** @param value the new override, or null to go back to the code's default */
		void on(Object value);
	}

	private interface Setter {
		void set(Object value);
	}

	/** A full field: optional header, label + unit, reset button, description and the editor. */
	public static View field(Host h, VarSpec s, Object override, Change change) {
		Context c = h.ctx();
		LinearLayout box = Ui.column(c);
		if (s.header != null && s.header.length() > 0) {
			TextView hd = Ui.sectionLabel(c, s.header);
			box.addView(hd);
		}
		LinearLayout head = Ui.row(c);
		head.setGravity(Gravity.CENTER_VERTICAL);
		String label = Tr.t("bhv_var_" + s.name, s.displayLabel()) + (s.unit != null && s.unit.length() > 0 ? "  (" + s.unit + ")" : "");
		TextView lt = Ui.text(c, label, 13, Ui.TEXT_2);
		lt.setPadding(Ui.dp(c, 2), Ui.dp(c, 8), 0, Ui.dp(c, 2));
		head.addView(lt, Ui.weight(1));
		boolean inlineBool = s.kind == VarSpec.Kind.BOOL && !s.list;
		Object effective = override != null ? override : s.def;
		if (inlineBool) {
			SwitchCompat sw = new SwitchCompat(c);
			sw.setChecked(Boolean.TRUE.equals(effective));
			sw.setOnCheckedChangeListener((b, on) -> change.on(on));
			head.addView(sw);
		}
		if (override != null) {
			ImageView reset = Ui.iconButton(c, R.drawable.ic_s2_refresh, Tr.t("bhv_reset", "Reset to default"), v -> change.on(null));
			head.addView(reset);
		}
		box.addView(head, Ui.matchWrap());
		if (s.desc != null && s.desc.length() > 0) {
			TextView d = Ui.text(c, s.desc, 11.5f, Ui.TEXT_3);
			d.setPadding(Ui.dp(c, 2), 0, 0, Ui.dp(c, 4));
			box.addView(d);
		}
		if (s.list) box.addView(listEditor(h, s, override, change), Ui.matchWrap());
		else if (!inlineBool) box.addView(scalar(h, s, effective, v -> change.on(v)), Ui.matchWrap());
		LinearLayout.LayoutParams lp = Ui.matchWrap();
		lp.bottomMargin = Ui.dp(c, 2);
		box.setLayoutParams(lp);
		return box;
	}

	// ------------------------------------------------------------------------------------------------ lists

	private static View listEditor(Host h, VarSpec s, Object override, Change change) {
		Context c = h.ctx();
		LinearLayout col = Ui.column(c);
		final List<Object> items = new ArrayList<>();
		Object eff = override != null ? override : s.def;
		if (eff instanceof List) items.addAll((List<?>) eff);
		for (int i = 0; i < items.size(); i++) {
			final int idx = i;
			LinearLayout row = Ui.row(c);
			row.setGravity(Gravity.CENTER_VERTICAL);
			TextView num = Ui.text(c, String.valueOf(i + 1), 11, Ui.TEXT_3);
			num.setMinWidth(Ui.dp(c, 18));
			row.addView(num);
			row.addView(scalar(h, s, items.get(i), v -> {
				List<Object> n = new ArrayList<>(items);
				n.set(idx, v);
				change.on(n);
			}), Ui.weight(1));
			row.addView(Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("bhv_remove_item", "Remove"), v -> {
				List<Object> n = new ArrayList<>(items);
				n.remove(idx);
				change.on(n);
			}));
			col.addView(row, Ui.matchWrap());
		}
		if (items.isEmpty()) {
			TextView e = Ui.text(c, Tr.t("bhv_list_empty", "Empty list"), 12, Ui.TEXT_3);
			e.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), 0, Ui.dp(c, 4));
			col.addView(e);
		}
		LinearLayout add = Ui.row(c);
		add.setGravity(Gravity.CENTER_VERTICAL);
		add.addView(Ui.button(c, R.drawable.ic_s2_add, Tr.t("bhv_add_item", "Add"), false, v -> {
			if (s.kind == VarSpec.Kind.REF) {
				ElementPicker.show(h, s, null, name -> {
					if (name == null) return;
					List<Object> n = new ArrayList<>(items);
					n.add(name);
					change.on(n);
				});
			} else {
				List<Object> n = new ArrayList<>(items);
				n.add(blank(s));
				change.on(n);
			}
		}));
		col.addView(add, Ui.matchWrap());
		return col;
	}

	/** A sensible new list item. */
	static Object blank(VarSpec s) {
		switch (s.kind) {
			case BOOL: return Boolean.FALSE;
			case STRING:
			case IMAGE:
			case SOUND:
			case REF: return "";
			case COLOR: return "#FFFFFF";
			case ENUM: return s.options.isEmpty() ? "" : s.options.get(0);
			default: return 0.0;
		}
	}

	// ------------------------------------------------------------------------------------------------ one value

	private static View scalar(Host h, VarSpec s, Object value, Setter set) {
		switch (s.kind) {
			case BOOL: {
				SwitchCompat sw = new SwitchCompat(h.ctx());
				sw.setChecked(Boolean.TRUE.equals(value));
				sw.setOnCheckedChangeListener((b, on) -> set.set(on));
				return sw;
			}
			case INT:
			case LONG:
			case SHORT:
			case BYTE:
			case FLOAT:
			case DOUBLE:
				return number(h, s, value, set);
			case COLOR:
				return color(h, s, value, set);
			case ENUM:
				return chooser(h, s.displayLabel(), value == null ? "" : String.valueOf(value), s.options, set);
			case IMAGE:
				return file(h, s, value, set, false);
			case SOUND:
				return file(h, s, value, set, true);
			case REF:
				return ref(h, s, value, set);
			default:
				return text(h, value, set);
		}
	}

	private static TextView chooserView(Context c, String text) {
		TextView t = Ui.text(c, text, 14, Ui.TEXT);
		t.setPadding(Ui.dp(c, 12), Ui.dp(c, 11), Ui.dp(c, 12), Ui.dp(c, 11));
		t.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 8)));
		return t;
	}

	private static View chooser(Host h, String title, String current, List<String> options, Setter set) {
		Context c = h.ctx();
		TextView t = chooserView(c, current.isEmpty() ? "—" : current);
		t.setOnClickListener(v -> Dialogs.choose(c, title, options.toArray(new String[0]), i -> set.set(options.get(i))));
		return t;
	}

	static String fmt(double d) {
		if (d == Math.rint(d) && Math.abs(d) < 1e12) return String.valueOf((long) d);
		String s = String.valueOf((float) d);
		return s;
	}

	private static View number(Host h, VarSpec s, Object value, Setter set) {
		Context c = h.ctx();
		String shown = value instanceof Number ? fmt(((Number) value).doubleValue()) : "";
		EditText e = Ui.input(c, shown, s.def instanceof Number ? fmt(((Number) s.def).doubleValue()) : "0");
		e.setFocusable(false);
		e.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_dialpad, 0);
		e.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3));
		e.setOnClickListener(v -> NumberPad.show(c, s.displayLabel(), e.getText().toString(), nv -> apply(c, s, nv, set),
				() -> Dialogs.input(c, s.displayLabel(), "", e.getText().toString(), val -> NumberPad.isNumber(val) ? null : Tr.t("ui_number", "Enter a number"), val -> apply(c, s, val, set))));
		return e;
	}

	private static void apply(Context c, VarSpec s, String text, Setter set) {
		try {
			double d = Double.parseDouble(text.trim());
			if (s.isInteger()) d = Math.rint(d);
			if (s.min > -1e8) d = Math.max(s.min, d);
			if (s.max < 1e8) d = Math.min(s.max, d);
			set.set(d);
		} catch (Exception ex) {
			Ui.toast(c, Tr.t("ui_number", "Enter a number"));
		}
	}

	private static View text(Host h, Object value, Setter set) {
		Context c = h.ctx();
		String start = value == null ? "" : String.valueOf(value);
		EditText e = Ui.input(c, start, "");
		e.setImeOptions(EditorInfo.IME_ACTION_DONE);
		e.setInputType(InputType.TYPE_CLASS_TEXT);
		final String[] last = {start};
		Runnable apply = () -> {
			String nv = e.getText().toString();
			if (nv.equals(last[0])) return;
			last[0] = nv;
			set.set(nv);
		};
		e.setOnEditorActionListener((tv, id, ev) -> {
			apply.run();
			e.clearFocus();
			return false;
		});
		e.setOnFocusChangeListener((v, has) -> {
			if (!has) apply.run();
		});
		return e;
	}

	private static View color(Host h, VarSpec s, Object value, Setter set) {
		Context c = h.ctx();
		String hex = Colors.normalize(value == null ? "" : String.valueOf(value));
		if (hex == null) hex = "#FFFFFF";
		LinearLayout row = Ui.row(c);
		row.setGravity(Gravity.CENTER_VERTICAL);
		row.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
		row.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 8)));
		View sw = new View(c);
		sw.setBackground(Ui.rounded(Colors.toArgb(hex, 0xFFFFFFFF), Ui.dp(c, 6), Ui.BORDER, 1));
		row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 26)));
		TextView t = Ui.text(c, hex, 13.5f, Ui.TEXT);
		t.setPadding(Ui.dp(c, 10), 0, 0, 0);
		t.setTypeface(Typeface.MONOSPACE);
		row.addView(t, Ui.weight(1));
		final String cur = hex;
		row.setOnClickListener(v -> ColorPickerDialog.show(c, s.displayLabel(), cur.substring(1) + (cur.length() == 7 ? "FF" : ""), x -> {}, done -> {
			if (done == null) return;
			String n = Colors.normalize(done);
			if (n != null) set.set(n);
		}));
		return row;
	}

	private static View file(Host h, VarSpec s, Object value, Setter set, boolean sound) {
		Context c = h.ctx();
		String cur = value == null ? "" : String.valueOf(value);
		LinearLayout r = Ui.row(c);
		r.setGravity(Gravity.CENTER_VERTICAL);
		r.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
		r.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
		ImageView iv = Ui.smallIcon(c, sound ? R.drawable.ic_s2_sound : R.drawable.ic_s2_image, Ui.TEXT_3, 36);
		r.addView(iv);
		TextView t = Ui.text(c, cur.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : cur, 13.5f, Ui.TEXT);
		t.setPadding(Ui.dp(c, 10), 0, 0, 0);
		t.setMaxLines(2);
		r.addView(t, Ui.weight(1));
		if (!sound && !cur.isEmpty()) {
			File img = new File(h.env().imagesPath + cur);
			if (img.isFile()) FilePickerDialog.thumb(iv, img, Ui.dp(c, 36));
		}
		r.setOnClickListener(v -> {
			String root = sound ? h.env().soundsPath : h.env().imagesPath;
			String[] ext = sound ? new String[] {"wav", "mp3", "ogg"} : new String[] {"jpg", "png", "jpeg"};
			FilePickerDialog.show(c, s.displayLabel(), root, ext, !sound, (file, rel) -> set.set(rel));
		});
		return r;
	}

	private static View ref(Host h, VarSpec s, Object value, Setter set) {
		Context c = h.ctx();
		String cur = value == null ? "" : String.valueOf(value);
		TextView t = chooserView(c, (cur.isEmpty() ? "— " + Tr.t("ui_none", "None") : cur) + "   · " + s.elemType);
		t.setOnClickListener(v -> ElementPicker.show(h, s, cur, name -> set.set(name == null ? "" : name)));
		return t;
	}
}
