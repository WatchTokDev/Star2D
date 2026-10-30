package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.sheets.SheetSlicer;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cut frames out of a picture: a sprite sheet (grid / cell size / automatic) or just a part of a picture.
 * Boxes are tapped to keep or skip them; "Add" returns the kept boxes in reading order.
 */
public class SheetImportDialog {
    public interface OnDone { void on(ArrayList<int[]> regions); }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final int GRID = 0, SIZE = 1, AUTO = 2;

    private final Context c;
    private final File file;
    private final OnDone done;
    private Bitmap shown;          // what is drawn (maybe reduced)
    private int sample = 1;        // picture px = shown px * sample
    private int pw, ph;            // picture size
    private byte[] mask;           // opaque mask of the (reduced) picture, built once
    private int mode = GRID;
    private boolean selecting;
    private int[] area;            // x,y,w,h in picture px, null = everything
    private ArrayList<int[]> boxes = new ArrayList<>();
    private ArrayList<Boolean> keep = new ArrayList<>();
    private int gen;
    private SheetView view;
    private ProgressBar busy;
    private TextView info;
    private LinearLayout params;
    private final int[] v = {4, 1, 32, 32, 0, 0, 3, 2};   // cols, rows, cw, ch, margin, spacing, minSize, gap
    private boolean skipEmpty = true;
    private AlertDialog dialog;

    public static void show(Context c, File image, OnDone done) {
        new SheetImportDialog(c, image, done).open();
    }

    private SheetImportDialog(Context c, File f, OnDone d) { this.c = c; this.file = f; this.done = d; }

    private void open() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        pw = o.outWidth;
        ph = o.outHeight;
        if (pw <= 0 || ph <= 0) { Snack.show(Tr.t("sheet_bad", "Cannot read this image")); return; }
        v[2] = Math.max(1, Math.min(pw, 32));
        v[3] = Math.max(1, Math.min(ph, 32));

        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), 0);
        view = new SheetView(c);
        col.addView(view, new LinearLayout.LayoutParams(-1, (int) (c.getResources().getDisplayMetrics().heightPixels * (Ui.isLandscape(c) ? 0.42f : 0.38f))));

        LinearLayout modes = Ui.row(c);
        modes.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
        String[] names = {Tr.t("sheet_grid", "Grid"), Tr.t("sheet_size", "Cell size"), Tr.t("sheet_auto", "Auto")};
        final TextView[] tabs = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView t = Ui.text(c, names[i], 13.5f, Ui.TEXT);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            t.setOnClickListener(x -> { mode = m; paintTabs(tabs); buildParams(); recompute(); });
            tabs[i] = t;
            modes.addView(t, Ui.margins(new LinearLayout.LayoutParams(-2, -2), c, 0, 0, 6, 0));
        }
        TextView sel = Ui.text(c, "✂ " + Tr.t("sheet_part", "Part"), 13.5f, Ui.TEXT);
        sel.setGravity(Gravity.CENTER);
        sel.setSingleLine(true);
        sel.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        sel.setOnClickListener(x -> {
            selecting = !selecting;
            sel.setBackground(Ui.rounded(selecting ? Ui.WARNING : Ui.SURFACE_2, Ui.dp(c, 8)));
            sel.setTextColor(selecting ? 0xFF000000 : Ui.TEXT);
            if (!selecting) return;
            Snack.show(Tr.t("sheet_part_hint", "Drag on the picture to choose the part to cut. Tap ✂ again to go back to choosing frames."));
        });
        sel.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 8)));
        modes.addView(sel, Ui.margins(new LinearLayout.LayoutParams(-2, -2), c, 0, 0, 6, 0));
        TextView clr = Ui.text(c, "⟲", 15, Ui.TEXT);
        clr.setGravity(Gravity.CENTER);
        clr.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
        clr.setOnClickListener(x -> { area = null; view.invalidate(); recompute(); });
        modes.addView(clr);
        android.widget.HorizontalScrollView modesScroll = new android.widget.HorizontalScrollView(c);
        modesScroll.setHorizontalScrollBarEnabled(false);
        modesScroll.addView(modes);
        col.addView(modesScroll);
        paintTabs(tabs);

        params = Ui.column(c);
        col.addView(params);
        buildParams();

        LinearLayout st = Ui.row(c);
        st.setGravity(Gravity.CENTER_VERTICAL);
        st.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));
        busy = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        busy.setMax(100);
        busy.setVisibility(View.GONE);
        info = Ui.text(c, "", 12.5f, Ui.TEXT_2);
        st.addView(info, Ui.weight(1));
        st.addView(busy, new LinearLayout.LayoutParams(Ui.dp(c, 90), Ui.dp(c, 18)));
        col.addView(st);
        col.addView(Ui.text(c, Tr.t("sheet_tip", "Tap a box to skip it (red) or keep it (green). Frames are added in this order: rows from the top, left to right."), 11.5f, Ui.TEXT_3));

        dialog = new AlertDialog.Builder(c)
                .setTitle(Tr.t("sheet_title", "Cut a sprite sheet"))
                .setView(col)
                .setPositiveButton(Tr.t("sheet_add", "Add frames"), (d, w) -> {
                    ArrayList<int[]> out = new ArrayList<>();
                    for (int i = 0; i < boxes.size(); i++) if (keep.get(i)) out.add(boxes.get(i));
                    if (!out.isEmpty()) done.on(out);
                })
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        dialog.show();

        // decode once, in the background (reduced when the picture is huge), build the opaque mask
        busy.setVisibility(View.VISIBLE);
        busy.setIndeterminate(true);
        POOL.execute(() -> {
            try {
                int s = 1;
                while ((long) (pw / s) * (ph / s) > 8_000_000L) s *= 2;
                BitmapFactory.Options op = new BitmapFactory.Options();
                op.inSampleSize = s;
                Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath(), op);
                if (b == null) throw new RuntimeException("decode");
                int w = b.getWidth(), h = b.getHeight();
                byte[] m = new byte[w * h];
                int[] row = new int[w];
                for (int y = 0; y < h; y++) {
                    b.getPixels(row, 0, w, 0, y, w, 1);
                    for (int x = 0; x < w; x++) m[y * w + x] = (byte) (((row[x] >>> 24) > 8) ? 1 : 0);
                }
                final int fs = s;
                Ui.ui(() -> {
                    shown = b; sample = fs; mask = m;
                    busy.setIndeterminate(false);
                    busy.setVisibility(View.GONE);
                    view.invalidate();
                    recompute();
                });
            } catch (Throwable e) {
                Ui.ui(() -> { Snack.show(Tr.t("sheet_bad", "Cannot read this image")); dialog.dismiss(); });
            }
        });
    }

    private void paintTabs(TextView[] tabs) {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackground(Ui.rounded(i == mode ? Ui.ACCENT : Ui.SURFACE_2, Ui.dp(c, 8)));
            tabs[i].setTextColor(Ui.TEXT);
        }
    }

    private void buildParams() {
        params.removeAllViews();
        String[][] labels = {
                {Tr.t("sheet_cols", "Columns"), Tr.t("sheet_rows", "Rows")},
                {Tr.t("sheet_cw", "Cell width"), Tr.t("sheet_ch", "Cell height"), Tr.t("sheet_margin", "Margin"), Tr.t("sheet_spacing", "Spacing")},
                {Tr.t("sheet_min", "Smallest (px)"), Tr.t("sheet_gap", "Join gap (px)")}};
        int[][] idx = {{0, 1}, {2, 3, 4, 5}, {6, 7}};
        LinearLayout r = Ui.row(c);
        for (int k = 0; k < idx[mode].length; k++) {
            final int vi = idx[mode][k];
            LinearLayout cell = Ui.column(c);
            cell.setPadding(Ui.dp(c, 3), 0, Ui.dp(c, 3), 0);
            cell.addView(Ui.text(c, labels[mode][k], 11.5f, Ui.TEXT_3));
            EditText e = Ui.input(c, String.valueOf(v[vi]), "");
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                public void onTextChanged(CharSequence s, int a, int b, int d) {}
                public void afterTextChanged(Editable s) {
                    try { v[vi] = Math.max(vi == 2 || vi == 3 || vi == 0 || vi == 1 ? 1 : 0, Integer.parseInt(s.toString().trim())); } catch (Exception ex) { return; }
                    recompute();
                }
            });
            cell.addView(e);
            r.addView(cell, Ui.weight(1));
        }
        params.addView(r);
        if (mode != AUTO) {
            TextView t = Ui.text(c, (skipEmpty ? "☑ " : "☐ ") + Tr.t("sheet_skip_empty", "Skip empty cells"), 13, Ui.TEXT_2);
            t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 2));
            t.setOnClickListener(x -> { skipEmpty = !skipEmpty; buildParams(); recompute(); });
            params.addView(t);
        }
    }

    private int[] workArea() {
        return area == null ? new int[]{0, 0, pw, ph} : SheetSlicer.clampArea(area, pw, ph);
    }

    private void recompute() {
        if (mask == null) return;
        final int my = ++gen;
        final int[] a = workArea();
        if (mode == AUTO) {
            busy.setVisibility(View.VISIBLE);
            busy.setProgress(0);
            // work on the reduced mask: area and results are converted
            final int s = sample, mw = shown.getWidth(), mh = shown.getHeight();
            final int minS = Math.max(1, v[6] / s), gap = v[7] / s;
            final int[] ra = {a[0] / s, a[1] / s, Math.max(1, a[2] / s), Math.max(1, a[3] / s)};
            POOL.execute(() -> {
                ArrayList<int[]> r = SheetSlicer.auto(mask, mw, mh, ra, minS, gap, p -> {
                    if (my == gen) Ui.ui(() -> busy.setProgress((int) (p * 100)));
                });
                for (int[] b : r) { b[0] *= s; b[1] *= s; b[2] *= s; b[3] *= s; }
                if (my == gen) Ui.ui(() -> { busy.setVisibility(View.GONE); setBoxes(r); });
            });
        } else {
            ArrayList<int[]> r = mode == GRID ? SheetSlicer.grid(a, v[0], v[1]) : SheetSlicer.cells(a, v[2], v[3], v[4], v[5]);
            if (skipEmpty) {
                // test on the reduced mask
                ArrayList<int[]> kept = new ArrayList<>();
                int s = sample, mw = shown.getWidth();
                for (int[] b : r) {
                    int[] q = {b[0] / s, b[1] / s, Math.max(1, b[2] / s), Math.max(1, b[3] / s)};
                    if (!SheetSlicer.skipEmpty(single(q), mask, mw).isEmpty()) kept.add(b);
                }
                r = kept;
            }
            if (r.size() > 2000) r = new ArrayList<>(r.subList(0, 2000));
            setBoxes(r);
        }
    }

    private static ArrayList<int[]> single(int[] q) {
        ArrayList<int[]> l = new ArrayList<>();
        l.add(q);
        return l;
    }

    private void setBoxes(ArrayList<int[]> r) {
        boxes = r;
        keep = new ArrayList<>();
        for (int i = 0; i < r.size(); i++) keep.add(true);
        info.setText(Tr.t("sheet_found", "Frames found") + ": " + r.size() + "   (" + pw + "×" + ph + ")");
        view.invalidate();
    }

    private class SheetView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect src = new Rect();
        final RectF dst = new RectF();
        float scale = 1, ox, oy, startX, startY;

        SheetView(Context c) { super(c); }

        private void layout() {
            float w = getWidth(), h = getHeight();
            scale = Math.min(w / pw, h / ph);
            ox = (w - pw * scale) / 2;
            oy = (h - ph * scale) / 2;
        }

        @Override protected void onDraw(Canvas cv) {
            cv.drawColor(0xFF2B2E34);
            if (shown == null) return;
            layout();
            // checkerboard behind the picture
            p.setStyle(Paint.Style.FILL);
            float cs = Ui.dp(getContext(), 8);
            for (float y = oy; y < oy + ph * scale; y += cs)
                for (float x = ox; x < ox + pw * scale; x += cs) {
                    p.setColor((((int) ((x - ox) / cs) + (int) ((y - oy) / cs)) & 1) == 0 ? 0xFF3A3E45 : 0xFF30343A);
                    cv.drawRect(x, y, Math.min(x + cs, ox + pw * scale), Math.min(y + cs, oy + ph * scale), p);
                }
            src.set(0, 0, shown.getWidth(), shown.getHeight());
            dst.set(ox, oy, ox + pw * scale, oy + ph * scale);
            p.setColor(Color.WHITE);
            p.setFilterBitmap(false);
            cv.drawBitmap(shown, src, dst, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, Ui.dp(getContext(), 1)));
            p.setTextSize(Ui.dp(getContext(), 10));
            for (int i = 0; i < boxes.size(); i++) {
                int[] b = boxes.get(i);
                boolean k = keep.get(i);
                p.setStyle(Paint.Style.FILL);
                p.setColor(k ? 0x2200E676 : 0x55FF1744);
                float l = ox + b[0] * scale, t = oy + b[1] * scale, r = ox + (b[0] + b[2]) * scale, bt = oy + (b[1] + b[3]) * scale;
                cv.drawRect(l, t, r, bt, p);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(k ? 0xFF00E676 : 0xFFFF1744);
                cv.drawRect(l, t, r, bt, p);
                if (k && r - l > Ui.dp(getContext(), 14)) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFFFFFFFF);
                    cv.drawText(String.valueOf(countKept(i)), l + 2, t + p.getTextSize(), p);
                }
            }
            if (area != null) {
                int[] a = workArea();
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 2));
                p.setColor(0xFFFFB300);
                p.setPathEffect(new android.graphics.DashPathEffect(new float[]{14, 8}, 0));
                cv.drawRect(ox + a[0] * scale, oy + a[1] * scale, ox + (a[0] + a[2]) * scale, oy + (a[1] + a[3]) * scale, p);
                p.setPathEffect(null);
            }
        }

        private int countKept(int upTo) {
            int n = 0;
            for (int i = 0; i <= upTo; i++) if (keep.get(i)) n++;
            return n;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (shown == null) return true;
            layout();
            float px = (e.getX() - ox) / scale, py = (e.getY() - oy) / scale;
            if (selecting) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        getParent().requestDisallowInterceptTouchEvent(true);
                        startX = clamp(px, pw); startY = clamp(py, ph);
                        area = new int[]{(int) startX, (int) startY, 1, 1};
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        float x2 = clamp(px, pw), y2 = clamp(py, ph);
                        area = new int[]{(int) Math.min(startX, x2), (int) Math.min(startY, y2),
                                Math.max(1, (int) Math.abs(x2 - startX)), Math.max(1, (int) Math.abs(y2 - startY))};
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                        recompute();
                        break;
                }
                invalidate();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                for (int i = boxes.size() - 1; i >= 0; i--) {
                    int[] b = boxes.get(i);
                    if (px >= b[0] && px < b[0] + b[2] && py >= b[1] && py < b[1] + b[3]) {
                        keep.set(i, !keep.get(i));
                        invalidate();
                        break;
                    }
                }
            }
            return true;
        }

        private float clamp(float v, int max) { return Math.max(0, Math.min(v, max - 1)); }
    }
}
