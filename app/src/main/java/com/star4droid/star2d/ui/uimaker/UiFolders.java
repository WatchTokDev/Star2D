package com.star4droid.star2d.ui.uimaker;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.UiMakerLauncher;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Folders inside a project's {@code UI/} folder. A UI file in a folder is addressed as {@code folder/name} (no ".ui"),
 * everywhere: the UI Maker, the UIHOLDER's "ui" property, the AI tool.
 */
public final class UiFolders {
    private UiFolders() {}

    public interface OnName { void on(String name); }

    /** Validates a "folder/name" path: parts of letters, digits and _ separated by /. Returns an error text or null. */
    public static String checkPath(String v) {
        if (v == null || v.trim().isEmpty()) return Tr.t("ui_name_empty", "Enter a name");
        String[] parts = v.trim().split("/", -1);
        if (parts.length > 5) return Tr.t("ui_too_deep", "Too many folders");
        for (String part : parts) {
            if (part.isEmpty()) return Tr.t("ui_name_empty", "Enter a name");
            for (char ch : part.toCharArray())
                if (!(Character.isLetterOrDigit(ch) && ch < 128) && ch != '_') return Tr.t("ui_name_chars", "Use A-Z a-z 0-9 or _");
        }
        return null;
    }

    public static String folderOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    public static String baseOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    /** All folders under UI/ ("" = UI itself first), as relative paths. */
    public static ArrayList<String> folders(File uiDir) {
        ArrayList<String> out = new ArrayList<>();
        out.add("");
        walk(uiDir, "", out, 0);
        return out;
    }

    private static void walk(File dir, String prefix, ArrayList<String> out, int depth) {
        File[] fs = dir.listFiles();
        if (fs == null || depth > 5) return;
        Arrays.sort(fs);
        for (File f : fs)
            if (f.isDirectory()) {
                out.add(prefix + f.getName());
                walk(f, prefix + f.getName() + "/", out, depth + 1);
            }
    }

    // ------------------------------------------------------------------ pickers

    /** Lets the user choose one of the folders (or make a new one). */
    public static void pickFolder(Context c, String project, String current, OnName onPick) {
        final File uiDir = UiMakerLauncher.uiDir(project);
        final ArrayList<String> list = folders(uiDir);
        if (current != null && !current.isEmpty() && !list.contains(current)) list.add(current);
        LinearLayout box = Ui.column(c);
        ScrollView sv = new ScrollView(c);
        LinearLayout col = Ui.column(c);
        sv.addView(col);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 300)));
        final AlertDialog[] dlg = new AlertDialog[1];
        for (final String f : list) {
            int depth = f.isEmpty() ? 0 : f.split("/").length;
            LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 8 + depth * 18), 0, Ui.dp(c, 8), 0);
            row.setMinimumHeight(Ui.dp(c, 46));
            boolean on = f.equals(current == null ? "" : current);
            row.setBackgroundColor(on ? Ui.ACCENT_SOFT : 0);
            row.addView(Ui.smallIcon(c, on ? R.drawable.ic_s2_folder_open : R.drawable.ic_s2_folder, on ? Ui.ACCENT : Ui.TEXT_2, 22));
            TextView t = Ui.text(c, f.isEmpty() ? "UI /" : baseOf(f), 14, Ui.TEXT);
            t.setPadding(Ui.dp(c, 10), 0, 0, 0);
            row.addView(t);
            row.setOnClickListener(v -> {
                dlg[0].dismiss();
                onPick.on(f);
            });
            col.addView(row, Ui.matchWrap());
        }
        AlertDialog.Builder b = new AlertDialog.Builder(c)
                .setTitle(Tr.t("ui_choose_folder", "Choose a folder"))
                .setView(box)
                .setNeutralButton(Tr.t("ui_new_folder", "New folder"), null)
                .setNegativeButton(Tr.t("close", "Close"), null);
        dlg[0] = b.create();
        dlg[0].setOnShowListener(x -> dlg[0].getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                com.star4droid.star2d.ui.Dialogs.input(c, Tr.t("ui_new_folder", "New folder"), Tr.t("ui_new_folder_msg", "Folder name (A-Z a-z 0-9 _), it is made inside the chosen one"), "", n -> checkPath(n.trim()), n -> {
                    String base = current == null ? "" : current;
                    String path = (base.isEmpty() ? "" : base + "/") + n.trim();
                    File d = new File(uiDir, path);
                    if (!d.isDirectory() && !d.mkdirs()) {
                        Ui.toast(c, Tr.t("ui_folder_failed", "Could not create the folder"));
                        return;
                    }
                    dlg[0].dismiss();
                    onPick.on(path);
                })));
        dlg[0].show();
    }

    /** Browse UI/ folder by folder and pick a UI file. Result: "folder/name" without .ui. */
    public static void pickUi(Context c, String project, String current, OnName onPick) {
        final File uiDir = UiMakerLauncher.uiDir(project);
        final String[] at = {current == null ? "" : folderOf(current)};
        final AlertDialog[] dlg = new AlertDialog[1];
        final LinearLayout box = Ui.column(c);
        final TextView path = Ui.text(c, "", 12, Ui.TEXT_3);
        path.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
        box.addView(path);
        final ScrollView sv = new ScrollView(c);
        final LinearLayout col = Ui.column(c);
        sv.addView(col);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 320)));
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            col.removeAllViews();
            File dir = at[0].isEmpty() ? uiDir : new File(uiDir, at[0]);
            path.setText("UI/" + (at[0].isEmpty() ? "" : at[0] + "/"));
            if (!at[0].isEmpty()) {
                col.addView(row(c, R.drawable.ic_s2_arrow_up, "..", false, Ui.TEXT_2, v -> {
                    at[0] = folderOf(at[0]);
                    fill[0].run();
                }), Ui.matchWrap());
            }
            File[] fs = dir.listFiles();
            if (fs != null) Arrays.sort(fs);
            int count = 0;
            if (fs != null) {
                for (final File f : fs) {
                    if (!f.isDirectory()) continue;
                    count++;
                    col.addView(row(c, R.drawable.ic_s2_folder, f.getName(), false, Ui.ACCENT, v -> {
                        at[0] = (at[0].isEmpty() ? "" : at[0] + "/") + f.getName();
                        fill[0].run();
                    }), Ui.matchWrap());
                }
                for (final File f : fs) {
                    String n = f.getName();
                    if (!f.isFile() || !n.toLowerCase().endsWith(".ui")) continue;
                    count++;
                    final String full = (at[0].isEmpty() ? "" : at[0] + "/") + n.substring(0, n.length() - 3);
                    col.addView(row(c, R.drawable.ic_s2_widgets, n.substring(0, n.length() - 3), full.equals(current), Ui.TEXT, v -> {
                        dlg[0].dismiss();
                        onPick.on(full);
                    }), Ui.matchWrap());
                }
            }
            if (count == 0) {
                TextView e = Ui.text(c, Tr.t("ui_folder_empty", "Nothing here yet"), 13, Ui.TEXT_3);
                e.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), 0);
                col.addView(e);
            }
        };
        fill[0].run();
        dlg[0] = new AlertDialog.Builder(c).setTitle(Tr.t("ui_choose", "Choose UI")).setView(box)
                .setNegativeButton(Tr.t("close", "Close"), null).create();
        dlg[0].show();
    }

    private static View row(Context c, int icon, String text, boolean on, int color, View.OnClickListener l) {
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        row.setMinimumHeight(Ui.dp(c, 46));
        row.setBackgroundColor(on ? Ui.ACCENT_SOFT : 0);
        row.addView(Ui.smallIcon(c, icon, color, 22));
        TextView t = Ui.text(c, text, 14, on ? Ui.ACCENT : Ui.TEXT);
        t.setPadding(Ui.dp(c, 10), 0, 0, 0);
        if (on) t.setTypeface(null, Typeface.BOLD);
        row.addView(t);
        row.setOnClickListener(l);
        return row;
    }

    /**
     * "New UI" dialog: a name plus the folder it goes into. {@code checkTaken} says whether a path already exists.
     * Result: "folder/name".
     */
    public static void newUiDialog(Context c, String project, String title, String initialName, String initialFolder, OnName onOk) {
        final File uiDir = UiMakerLauncher.uiDir(project);
        final String[] folder = {initialFolder == null ? "" : initialFolder};
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 6), Ui.dp(c, 20), 0);
        final EditText name = Ui.input(c, initialName, Tr.t("name", "Name"));
        box.addView(name, Ui.matchWrap());
        final TextView folderText = Ui.text(c, "", 13, Ui.TEXT_2);
        final Runnable show = () -> folderText.setText(Tr.t("prefab_folder", "Folder") + ": UI/" + folder[0]);
        show.run();
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 10), 0, 0);
        row.addView(folderText, Ui.weight(1));
        row.addView(Ui.button(c, R.drawable.ic_s2_folder, Tr.t("prefab_choose_folder_short", "Choose"), false,
                v -> pickFolder(c, project, folder[0], f -> {
                    folder[0] = f;
                    show.run();
                })));
        box.addView(row, Ui.matchWrap());
        final AlertDialog d = new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton(Tr.t("ok", "OK"), null).setNegativeButton(Tr.t("cancel", "Cancel"), null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String err = checkPath(n);
            if (err == null && n.contains("/")) err = Tr.t("ui_name_chars", "Use A-Z a-z 0-9 or _");
            if (err != null) {
                name.setError(err);
                return;
            }
            String full = (folder[0].isEmpty() ? "" : folder[0] + "/") + n;
            if (new File(uiDir, full + ".ui").exists()) {
                name.setError(Tr.t("ui_name_used", "A UI with this name already exists"));
                return;
            }
            d.dismiss();
            onOk.on(full);
        }));
        d.show();
    }
}
