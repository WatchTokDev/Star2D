package com.star4droid.star2d.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * Big text editor for any text property or variable: a roomy multi-line field (any length, any number of lines, Arabic or English),
 * a counter and a Clear button. Used instead of a one line field everywhere a text is typed (properties, variables, UI Maker).
 */
public final class TextDialog {
    private TextDialog() {}

    public interface OnText { void on(String value); }

    /** Short one line preview of a text for a row ("first line…"), "" shows the placeholder. */
    public static String preview(String v, int max) {
        if (v == null) return "";
        String s = v.replace("\r", "").replace('\n', '⏎').trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    public static String javaLiteral(String text) {
        return com.star4droid.star2d.behaviors.ValueEmit.javaLiteral(text);
    }

    public static String fromJavaLiteral(String code) {
        return com.star4droid.star2d.behaviors.ValueEmit.fromJavaLiteral(code);
    }

    /**
     * A row that shows the text (first lines) and opens the big editor when tapped. {@code onChange} gets the new text; the row shows it at once.
     */
    public static View row(final Context c, final String title, String value, final String placeholder, final OnText onChange) {
        final String[] cur = {value == null ? "" : value};
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        r.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 7), Ui.BORDER, Ui.dp(c, 1)));
        final TextView t = Ui.text(c, "", 14, Ui.TEXT);
        t.setMaxLines(3);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        final Runnable paint = () -> {
            boolean empty = cur[0].isEmpty();
            t.setText(empty ? placeholder : cur[0].replace("\r", ""));
            t.setTextColor(empty ? Ui.TEXT_3 : Ui.TEXT);
        };
        paint.run();
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(Ui.smallIcon(c, com.star4droid.star2d.evo.R.drawable.ic_s2_edit, Ui.TEXT_3, 20));
        r.setOnClickListener(v -> show(c, title, "", cur[0], nv -> {
            if (nv.equals(cur[0])) return;
            cur[0] = nv;
            paint.run();
            onChange.on(nv);
        }));
        return r;
    }

    public static AlertDialog show(Context c, String title, String message, String initial, final OnText onOk) {
        final EditText e = Ui.input(c, initial == null ? "" : initial, "");
        e.setSingleLine(false);
        e.setMinLines(8);
        e.setMaxLines(14);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setHorizontallyScrolling(false);
        e.setVerticalScrollBarEnabled(true);
        e.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        e.setTextSize(15);
        e.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));

        LinearLayout box = Ui.column(c);
        int p = Ui.dp(c, 20);
        box.setPadding(p, Ui.dp(c, 6), p, 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout foot = Ui.row(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
        final TextView count = Ui.text(c, "", 12, Ui.TEXT_3);
        foot.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView clear = Ui.text(c, Tr.t("text_clear", "Clear"), 13, Ui.ACCENT);
        clear.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
        clear.setOnClickListener(v -> e.setText(""));
        foot.addView(clear);
        box.addView(foot, Ui.matchWrap());

        final Runnable upd = () -> {
            String t = e.getText().toString();
            int lines = t.isEmpty() ? 0 : t.split("\n", -1).length;
            count.setText(String.format(Tr.t("text_count", "%1$d characters · %2$d lines"), t.length(), lines));
        };
        upd.run();
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { upd.run(); }
        });

        AlertDialog.Builder b = new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton(Tr.t("ok", "OK"), (d, w) -> onOk.on(e.getText().toString()))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null);
        if (message != null && !message.isEmpty()) b.setMessage(message);
        AlertDialog d = b.create();
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        e.requestFocus();
        e.setSelection(e.getText().length());
        return d;
    }
}
