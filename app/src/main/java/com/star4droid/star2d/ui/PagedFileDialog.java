package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.evo.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Opens a file whose content would hang the editor (binary files like .ttf, or big text) as small pages: one reasonable page is
 * laid out at a time, with previous / next arrows. Text files are editable page by page; binary files are a read-only hex view.
 * A text view never gets more than a few KB, so opening, rotating or returning to the app can't freeze.
 */
public final class PagedFileDialog {
    private PagedFileDialog() {}

    /** Text above this is shown by pages. */
    public static final int TEXT_PAGE_LIMIT = 24 * 1024;
    /** Characters per text page. */
    static final int TEXT_PAGE = 12000;
    /** Bytes per hex page (16 per row). */
    static final int HEX_PAGE = 4096;
    /** Text files above this are read-only (the whole file is re-read to save a page). */
    static final long MAX_EDIT = 6L * 1024 * 1024;

    /** True when the file must not be put in one text view: binary content or a lot of text. */
    public static boolean needsPaging(File f) {
        if (f == null || !f.isFile()) return false;
        if (f.length() > TEXT_PAGE_LIMIT) return true;
        return looksBinary(f);
    }

    /** Looks at the first bytes: a NUL byte or many control bytes means binary. */
    public static boolean looksBinary(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[4096];
            int n = in.read(b);
            if (n <= 0) return false;
            int bad = 0;
            for (int i = 0; i < n; i++) {
                int c = b[i] & 0xFF;
                if (c == 0) return true;
                if (c < 9 || (c > 13 && c < 32)) bad++;
            }
            return bad * 100 / n > 5;
        } catch (Exception e) {
            return false;
        }
    }

    public static AlertDialog show(Context c, File f, Runnable onSaved) {
        return looksBinary(f) ? showHex(c, f) : showText(c, f, onSaved);
    }

    // ----------------------------------------------------------------------------------------------- text

    private static AlertDialog showText(Context c, File f, Runnable onSaved) {
        final java.util.ArrayList<String> pages = new java.util.ArrayList<>();
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), MAX_EDIT * 4)];
            int r = 0, k;
            while (r < b.length && (k = in.read(b, r, b.length - r)) > 0) r += k;
            String all = new String(b, 0, r, StandardCharsets.UTF_8);
            // cut at line ends when possible, so a page doesn't split a line
            int s = 0;
            while (s < all.length()) {
                int end = Math.min(all.length(), s + TEXT_PAGE);
                if (end < all.length()) {
                    int nl = all.lastIndexOf('\n', end - 1);
                    if (nl > s + TEXT_PAGE / 2) end = nl + 1;
                }
                pages.add(all.substring(s, end));
                s = end;
            }
            if (pages.isEmpty()) pages.add("");
        } catch (Throwable e) {
            Ui.toast(c, String.valueOf(e));
            return null;
        }
        final boolean editable = f.length() <= MAX_EDIT && f.canWrite();
        final int[] page = {0};
        final EditText e = Ui.input(c, "", "");
        e.setSingleLine(false);
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextSize(12f);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setHorizontallyScrolling(true);
        e.setMinLines(8);
        if (!editable) e.setKeyListener(null);
        final TextView info = Ui.text(c, "", 13, Ui.TEXT_2);
        info.setGravity(Gravity.CENTER);
        final Runnable[] go = new Runnable[1];
        final int[] pending = {0};
        final android.view.View prev = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("pg_prev", "Previous"), v -> { pending[0] = -1; go[0].run(); });
        final android.view.View next = Ui.iconButton(c, R.drawable.ic_s2_arrow_forward, Tr.t("pg_next", "Next"), v -> { pending[0] = 1; go[0].run(); });
        go[0] = () -> {
            if (editable) pages.set(page[0], e.getText().toString());
            page[0] = Math.max(0, Math.min(pages.size() - 1, page[0] + pending[0]));
            pending[0] = 0;
            e.setText(pages.get(page[0]));
            e.setSelection(0);
            info.setText(String.format(Locale.ENGLISH, "%d / %d", page[0] + 1, pages.size()));
            prev.setAlpha(page[0] > 0 ? 1f : 0.35f);
            next.setAlpha(page[0] < pages.size() - 1 ? 1f : 0.35f);
        };
        go[0].run();
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), 0);
        box.addView(pager(c, prev, info, next, f.length() > TEXT_PAGE ? Tr.t("pg_big_text", "Large file: shown in pages") : null));
        box.addView(e, new LinearLayout.LayoutParams(-1, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.5f)));
        AlertDialog.Builder b = new AlertDialog.Builder(c).setTitle(f.getName()).setView(box)
                .setNegativeButton(Tr.t("close", "Close"), null)
                .setNeutralButton(Tr.t("copy", "Copy"), (d, w) -> Dialogs.copy(c, e.getText().toString()));
        if (editable) b.setPositiveButton(Tr.t("save", "Save"), (d, w) -> {
            pages.set(page[0], e.getText().toString());
            StringBuilder sb = new StringBuilder();
            for (String p : pages) sb.append(p);
            try (OutputStream out = new FileOutputStream(f)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                Ui.toast(c, Tr.t("saved", "Saved"));
                if (onSaved != null) onSaved.run();
            } catch (Exception ex) {
                Ui.toast(c, ex.toString());
            }
        });
        return b.show();
    }

    // ----------------------------------------------------------------------------------------------- hex

    private static AlertDialog showHex(Context c, File f) {
        final long len = f.length();
        final int pageCount = (int) Math.max(1, (len + HEX_PAGE - 1) / HEX_PAGE);
        final int[] page = {0};
        final TextView t = Ui.text(c, "", 11, Ui.TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setHorizontallyScrolling(true);
        t.setTextDirection(View.TEXT_DIRECTION_LTR);
        t.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        final TextView info = Ui.text(c, "", 13, Ui.TEXT_2);
        info.setGravity(Gravity.CENTER);
        final Runnable[] go = new Runnable[1];
        final int[] pending = {0};
        final View prev = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("pg_prev", "Previous"), v -> { pending[0] = -1; go[0].run(); });
        final View next = Ui.iconButton(c, R.drawable.ic_s2_arrow_forward, Tr.t("pg_next", "Next"), v -> { pending[0] = 1; go[0].run(); });
        go[0] = () -> {
            page[0] = Math.max(0, Math.min(pageCount - 1, page[0] + pending[0]));
            pending[0] = 0;
            t.setText(hexPage(f, (long) page[0] * HEX_PAGE));
            info.setText(String.format(Locale.ENGLISH, "%d / %d", page[0] + 1, pageCount));
            prev.setAlpha(page[0] > 0 ? 1f : 0.35f);
            next.setAlpha(page[0] < pageCount - 1 ? 1f : 0.35f);
        };
        go[0].run();
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(c);
        h.addView(t);
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.addView(h);
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), 0);
        box.addView(pager(c, prev, info, next, Tr.t("pg_binary", "Binary file (read only): bytes shown in pages") + " · " + size(len)));
        box.addView(sv, new LinearLayout.LayoutParams(-1, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.5f)));
        return new AlertDialog.Builder(c).setTitle(f.getName()).setView(box)
                .setPositiveButton(Tr.t("close", "Close"), null)
                .setNeutralButton(Tr.t("copy", "Copy"), (d, w) -> Dialogs.copy(c, t.getText().toString()))
                .show();
    }

    private static String hexPage(File f, long start) {
        StringBuilder sb = new StringBuilder();
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(start);
            byte[] b = new byte[HEX_PAGE];
            int n = raf.read(b);
            for (int row = 0; row < n; row += 16) {
                sb.append(String.format(Locale.ENGLISH, "%08X  ", start + row));
                StringBuilder asc = new StringBuilder();
                for (int i = 0; i < 16; i++) {
                    if (row + i < n) {
                        int v = b[row + i] & 0xFF;
                        sb.append(String.format(Locale.ENGLISH, "%02X ", v));
                        asc.append(v >= 32 && v < 127 ? (char) v : '.');
                    } else sb.append("   ");
                }
                sb.append(' ').append(asc).append('\n');
            }
        } catch (Throwable e) {
            sb.append(e);
        }
        return sb.toString();
    }

    private static String size(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.ENGLISH, "%.1f KB", n / 1024.0);
        return String.format(Locale.ENGLISH, "%.1f MB", n / 1048576.0);
    }

    private static View pager(Context c, View prev, TextView info, View next, String note) {
        LinearLayout col = Ui.column(c);
        if (note != null) {
            TextView n = Ui.text(c, note, 12, Ui.TEXT_3);
            n.setPadding(0, 0, 0, Ui.dp(c, 4));
            col.addView(n);
        }
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(prev);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(next);
        col.addView(row, Ui.matchWrap());
        return col;
    }
}
