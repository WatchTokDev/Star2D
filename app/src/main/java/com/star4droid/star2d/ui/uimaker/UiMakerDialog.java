package com.star4droid.star2d.ui.uimaker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.ColorPickerDialog;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.NumberPad;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.UiMakerLauncher;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UILayout;
import com.star4droid.template.ui.UINode;
import com.star4droid.template.ui.UITheme;
import com.star4droid.template.ui.UIWidgetRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UI Maker: full-screen editor of a project's {@code UI/<name>.ui} file.
 *
 * Top: live preview in a device frame (real renderer, tap to select, drag to move, handles to resize, pinch to zoom, test mode).
 * Bottom: tabs Elements (palette + templates) / Tree / Properties / Theme. Toolbar: undo, redo, warnings, more, save.
 * See docs/UI.md for the file format.
 */
public final class UiMakerDialog implements UiPreviewView.Host, UiPropsView.Host, PreviewEngine.Listener {
    /** Entry used by {@link UiMakerLauncher}. */
    public static void open(Context c, String projectPath, String uiName, Runnable onSaved) {
        Activity a = unwrap(c);
        if (a == null) {
            Ui.toast(c, Tr.t("uim_need_activity", "Cannot open the UI Maker from here"));
            return;
        }
        new UiMakerDialog(a, projectPath, uiName == null ? "" : uiName.replaceAll("(?i)\\.ui$", "").trim(), onSaved).show();
    }

    private static Activity unwrap(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    // ------------------------------------------------------------------ state
    private static final int[][] DEVICES = {{360, 640}, {480, 854}, {720, 1280}, {1080, 1920}, {800, 1280}};
    private static final int[] GRIDS = {0, 8, 16, 32};
    private static final int[] BGS = {0xFF1C2230, 0xFF000000, 0xFFFFFFFF, 0xFF6C8FB8};

    private final Activity act;
    private final String project;
    private String name;
    private final Runnable onSaved;
    private final Handler main = new Handler(Looper.getMainLooper());
    private UIDocument doc;
    private UiHistory hist;
    private UINode selected;
    private boolean fileExists;
    private boolean testMode;
    private boolean landscape = true;
    private int device = 2;
    private int gridIdx = 0;
    private int bgIdx = 0;
    private int tab = 0;

    private Dialog dialog;
    private UiPreviewView preview;
    private PreviewEngine engine;
    private FrameLayout body;
    private TextView title, warnBadge, zoomText;
    private ImageView undoBtn, redoBtn, testBtn;
    private TextView orientChip, deviceChip, gridChip, bgChip, modeChip;
    private final TextView[] tabs = new TextView[4];
    private boolean historyPending;
    private final Runnable historyFlush = new Runnable() {
        @Override
        public void run() {
            flushHistory();
        }
    };
    private final Runnable testTick = new Runnable() {
        @Override
        public void run() {
            if (!testMode || dialog == null || !dialog.isShowing()) return;
            render(true, 0.04f);
            main.postDelayed(this, 40);
        }
    };
    private long lastFrameAt;
    private int lastPw = 1;
    private boolean warnedField;
    private boolean glOk = true;

    private UiMakerDialog(Activity a, String project, String name, Runnable onSaved) {
        this.act = a;
        this.project = project;
        this.name = name.isEmpty() ? "ui" : name;
        this.onSaved = onSaved;
        File f = UiMakerLauncher.uiDir(project);
        File file = new File(f, this.name + ".ui");
        fileExists = file.isFile();
        doc = fileExists ? UIDocument.read(file) : UiOps.newDocument(this.name, 1280, 720);
        if (doc.warnings.size() > 0 && fileExists) {
            // read problems are shown once, the document is still usable
            Ui.toast(a, doc.warnings.get(0));
        }
        doc.name = this.name;
        hist = new UiHistory(doc);
        landscape = doc.designWidth >= doc.designHeight;
        int best = 2;
        float bd = Float.MAX_VALUE;
        float dl = Math.max(doc.designWidth, doc.designHeight);
        for (int i = 0; i < DEVICES.length; i++) {
            float d = Math.abs(DEVICES[i][1] - dl);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        device = best;
        try {
            gridIdx = Math.min(GRIDS.length - 1, Ui.prefs(a).getInt("uim_grid", 0));
            bgIdx = Math.min(BGS.length - 1, Ui.prefs(a).getInt("uim_bg", 0));
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ window

    private void show() {
        engine = new PreviewEngine(project, this);
        dialog = new Dialog(act, android.R.style.Theme_Black_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(buildRoot());
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        }
        dialog.setOnKeyListener((d, code, ev) -> {
            if (code == KeyEvent.KEYCODE_BACK && ev.getAction() == KeyEvent.ACTION_UP) {
                onClose();
                return true;
            }
            return code == KeyEvent.KEYCODE_BACK;
        });
        dialog.setOnDismissListener(d -> {
            main.removeCallbacks(testTick);
            main.removeCallbacks(historyFlush);
            engine.dispose();
        });
        dialog.show();
        showTab(0);
        applyDevice();
        updateBars();
        if (!fileExists || doc.root.children.isEmpty()) main.post(this::askStart);
    }

    private void close() {
        if (dialog != null) dialog.dismiss();
    }

    private void onClose() {
        flushHistory();
        if (!hist.dirty()) {
            close();
            return;
        }
        new AlertDialog.Builder(act)
                .setTitle(Tr.t("uim_unsaved", "Unsaved changes"))
                .setMessage(Tr.t("uim_unsaved_msg", "Save the changes to this UI before closing?"))
                .setPositiveButton(Tr.t("save", "Save"), (d, i) -> {
                    if (save()) close();
                })
                .setNegativeButton(Tr.t("uim_discard", "Discard"), (d, i) -> close())
                .setNeutralButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    // ------------------------------------------------------------------ layout

    /** Portrait: preview on top, panel below. Landscape: side by side. */
    private final class Split extends LinearLayout {
        private int lastLand = -1;
        View a, b;

        Split(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            int land = w > h ? 1 : 0;
            if (land != lastLand && a != null && b != null) {
                lastLand = land;
                setOrientation(land == 1 ? HORIZONTAL : VERTICAL);
                LayoutParams la = new LayoutParams(land == 1 ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, land == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : 0, land == 1 ? 0.55f : 0.42f);
                LayoutParams lb = new LayoutParams(land == 1 ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, land == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : 0, land == 1 ? 0.45f : 0.58f);
                a.setLayoutParams(la);
                b.setLayoutParams(lb);
            }
            super.onMeasure(wSpec, hSpec);
        }
    }

    private View buildRoot() {
        LinearLayout root = Ui.column(act);
        root.setBackgroundColor(Ui.BG);
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 52)));
        root.addView(Ui.divider(act));
        Split split = new Split(act);
        FrameLayout pv = new FrameLayout(act);
        preview = new UiPreviewView(act, this);
        preview.setOnZoom(() -> {
            updateZoomText();
            render(false, 0f);
        });
        pv.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        preview.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) render(false, 0f);
        });
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        pv.addView(buildPreviewStrip(), sl);
        LinearLayout panel = Ui.column(act);
        panel.setBackgroundColor(Ui.SURFACE);
        panel.addView(buildTabBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 48)));
        panel.addView(Ui.divider(act));
        body = new FrameLayout(act);
        panel.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        split.a = pv;
        split.b = panel;
        split.addView(pv);
        split.addView(panel);
        root.addView(split, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private View buildToolbar() {
        LinearLayout bar = Ui.row(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(act, 4), 0, Ui.dp(act, 4), 0);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.addView(Ui.iconButton(act, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> onClose()));
        title = Ui.text(act, "", 15, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        title.setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 6), 0);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(v -> saveAs());
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        undoBtn = Ui.iconButton(act, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> undo());
        redoBtn = Ui.iconButton(act, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> redo());
        bar.addView(undoBtn);
        bar.addView(redoBtn);
        FrameLayout warn = new FrameLayout(act);
        ImageView wi = Ui.iconButton(act, R.drawable.ic_error_black, Tr.t("uim_warnings", "Layout warnings"), v -> showWarnings());
        wi.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.WARNING));
        warn.addView(wi);
        warnBadge = Ui.text(act, "", 10, 0xFF000000);
        warnBadge.setBackground(Ui.rounded(Ui.WARNING, Ui.dp(act, 8)));
        warnBadge.setGravity(Gravity.CENTER);
        warnBadge.setMinWidth(Ui.dp(act, 16));
        warnBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(act, 16), Gravity.TOP | Gravity.END);
        warn.addView(warnBadge, bl);
        bar.addView(warn);
        bar.addView(Ui.iconButton(act, R.drawable.ic_s2_more, Tr.t("more", "More"), v -> moreMenu()));
        TextView save = Ui.text(act, Tr.t("save", "Save"), 14, 0xFFFFFFFF);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setGravity(Gravity.CENTER);
        save.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
        save.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(act, 8)));
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(act, 38));
        sp.setMarginStart(Ui.dp(act, 4));
        bar.addView(save, sp);
        return bar;
    }

    private TextView stripChip(String t, View.OnClickListener l) {
        TextView v = Ui.text(act, t, 12, Ui.TEXT);
        v.setGravity(Gravity.CENTER);
        v.setPadding(Ui.dp(act, 10), Ui.dp(act, 6), Ui.dp(act, 10), Ui.dp(act, 6));
        v.setBackground(Ui.rounded(0xCC202328, Ui.dp(act, 14), Ui.BORDER, 1));
        v.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(act, 6));
        v.setLayoutParams(lp);
        return v;
    }

    private View buildPreviewStrip() {
        LinearLayout row = Ui.row(act);
        row.setPadding(Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 8));
        modeChip = stripChip("", v -> setTestMode(!testMode));
        orientChip = stripChip("", v -> {
            landscape = !landscape;
            applyDevice();
        });
        deviceChip = stripChip("", v -> {
            String[] items = new String[DEVICES.length];
            for (int i = 0; i < items.length; i++) items[i] = DEVICES[i][0] + " x " + DEVICES[i][1];
            Dialogs.choose(act, Tr.t("uim_device", "Screen size"), items, i -> {
                device = i;
                applyDevice();
            });
        });
        gridChip = stripChip("", v -> {
            gridIdx = (gridIdx + 1) % GRIDS.length;
            try {
                Ui.prefs(act).edit().putInt("uim_grid", gridIdx).apply();
            } catch (Throwable ignored) {
            }
            updateBars();
            preview.invalidate();
        });
        bgChip = stripChip("", v -> {
            bgIdx = (bgIdx + 1) % BGS.length;
            try {
                Ui.prefs(act).edit().putInt("uim_bg", bgIdx).apply();
            } catch (Throwable ignored) {
            }
            updateBars();
            render(false, 0f);
        });
        TextView zm = stripChip("−", v -> {
            preview.setZoom(preview.zoom() / 1.25f);
            updateZoomText();
            render(false, 0f);
        });
        zoomText = stripChip("100%", v -> {
            preview.fit();
            updateZoomText();
            render(false, 0f);
        });
        TextView zp = stripChip("+", v -> {
            preview.setZoom(preview.zoom() * 1.25f);
            updateZoomText();
            render(false, 0f);
        });
        row.addView(modeChip);
        row.addView(orientChip);
        row.addView(deviceChip);
        row.addView(gridChip);
        row.addView(bgChip);
        row.addView(zm);
        row.addView(zoomText);
        row.addView(zp);
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    private View buildTabBar() {
        LinearLayout bar = Ui.row(act);
        String[] names = {Tr.t("uim_tab_add", "Elements"), Tr.t("uim_tab_tree", "Tree"), Tr.t("uim_tab_props", "Properties"), Tr.t("uim_tab_theme", "Theme")};
        int[] icons = {R.drawable.ic_s2_add, R.drawable.ic_s2_tree, R.drawable.ic_s2_properties, R.drawable.ic_s2_palette};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = Ui.text(act, names[i], 12, Ui.TEXT_2);
            t.setGravity(Gravity.CENTER);
            t.setCompoundDrawablePadding(Ui.dp(act, 2));
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(act, icons[i]);
            if (d != null) {
                d = d.mutate();
                d.setBounds(0, 0, Ui.dp(act, 20), Ui.dp(act, 20));
                t.setCompoundDrawables(null, d, null, null);
            }
            t.setOnClickListener(v -> showTab(idx));
            tabs[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        return bar;
    }

    // ------------------------------------------------------------------ tabs

    private void showTab(int i) {
        tab = i;
        for (int k = 0; k < tabs.length; k++) {
            boolean on = k == i;
            tabs[k].setTextColor(on ? Ui.ACCENT : Ui.TEXT_2);
            android.graphics.drawable.Drawable[] ds = tabs[k].getCompoundDrawables();
            if (ds[1] != null) ds[1].setTint(on ? Ui.ACCENT : Ui.TEXT_2);
            tabs[k].setBackgroundColor(on ? Ui.ACCENT_SOFT : Color.TRANSPARENT);
        }
        body.removeAllViews();
        View v;
        switch (i) {
            case 0: v = buildPalette(); break;
            case 1: v = buildTree(); break;
            case 2: v = new UiPropsView(this).build(selected); break;
            default: v = buildTheme(); break;
        }
        if (i == 1) body.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        else {
            ScrollView sv = new ScrollView(act);
            sv.setFillViewport(false);
            sv.addView(v);
            body.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void refreshTab() {
        showTab(tab);
    }

    // ---- palette

    private View buildPalette() {
        LinearLayout root = Ui.column(act);
        root.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 24));
        root.addView(Ui.sectionLabel(act, Tr.t("uim_templates", "Ready-made screens")));
        LinearLayout tr = Ui.row(act);
        for (final String t : UiTemplates.names()) {
            String[] info = UiTemplates.INFO.get(t);
            LinearLayout card = Ui.column(act);
            card.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
            card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(act, 10), Ui.BORDER, 1));
            TextView n = Ui.text(act, Tr.t("uim_tpl_" + t, info[0]), 14, Ui.TEXT);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(n);
            TextView d = Ui.text(act, Tr.t("uim_tpl_" + t + "_d", info[1]), 11.5f, Ui.TEXT_3);
            d.setMaxLines(3);
            card.addView(d);
            card.setOnClickListener(v -> addTemplate(t));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(act, 170), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.dp(act, 8));
            tr.addView(card, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(tr);
        root.addView(hs, Ui.matchWrap());

        TextView hint = Ui.text(act, selected == null ? Tr.t("uim_add_hint_root", "New elements go into the root.")
                : selected.isContainer() ? String.format(Tr.t("uim_add_hint_in", "New elements go inside %s."), selected.id)
                : String.format(Tr.t("uim_add_hint_after", "New elements go after %s."), selected.id), 12, Ui.TEXT_3);
        hint.setPadding(0, Ui.dp(act, 12), 0, 0);
        root.addView(hint);
        for (String cat : UIWidgetRegistry.categories()) {
            root.addView(Ui.sectionLabel(act, categoryTitle(cat)));
            LinearLayout rowL = null;
            int k = 0;
            for (UIWidgetRegistry.Widget w : UIWidgetRegistry.all()) {
                if (!w.category.equals(cat)) continue;
                if (k % 2 == 0) {
                    rowL = Ui.row(act);
                    root.addView(rowL, Ui.matchWrap());
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                lp.setMargins(Ui.dp(act, 3), Ui.dp(act, 3), Ui.dp(act, 3), Ui.dp(act, 3));
                rowL.addView(widgetCard(w), lp);
                k++;
            }
            if (k % 2 == 1) {
                View pad = new View(act);
                rowL.addView(pad, new LinearLayout.LayoutParams(0, 1, 1));
            }
        }
        return root;
    }

    private String categoryTitle(String c) {
        switch (c) {
            case "Layout": return Tr.t("uim_cat_layout", "Layout");
            case "Display": return Tr.t("uim_cat_display", "Display");
            default: return Tr.t("uim_cat_input", "Input");
        }
    }

    private View widgetCard(final UIWidgetRegistry.Widget w) {
        LinearLayout card = Ui.row(act);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10));
        card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(act, 10), Ui.BORDER, 1));
        card.addView(Ui.smallIcon(act, UiIcons.forType(w.type), Ui.ACCENT, 26));
        LinearLayout col = Ui.column(act);
        col.setPadding(Ui.dp(act, 10), 0, 0, 0);
        TextView n = Ui.text(act, Tr.t("uim_w_" + w.type.toLowerCase(Locale.ROOT), w.name), 14, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(n);
        TextView d = Ui.text(act, Tr.t("uim_w_" + w.type.toLowerCase(Locale.ROOT) + "_d", w.desc), 11, Ui.TEXT_3);
        d.setMaxLines(3);
        col.addView(d);
        card.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.setOnClickListener(v -> addWidget(w.type));
        return card;
    }

    private void addWidget(String type) {
        flushHistory();
        String[] err = new String[1];
        UINode n = UiOps.addSmart(doc, selected, type, err);
        if (n == null) {
            Ui.toast(act, err[0]);
            return;
        }
        selected = n;
        structural();
        Ui.toast(act, String.format(Tr.t("uim_added", "Added %s"), n.id));
    }

    private void addTemplate(String t) {
        flushHistory();
        UINode parent = selected != null && selected.isContainer() ? selected : doc.root;
        UINode n = UiTemplates.insert(doc, parent, t);
        if (n == null) return;
        selected = n;
        structural();
        showTab(2 == tab ? 2 : 1);
    }

    // ---- tree

    private View buildTree() {
        LinearLayout root = Ui.column(act);
        // actions on the selected node
        LinearLayout acts = Ui.row(act);
        acts.setGravity(Gravity.CENTER_VERTICAL);
        acts.setPadding(Ui.dp(act, 4), Ui.dp(act, 2), Ui.dp(act, 4), Ui.dp(act, 2));
        int[] ic = {R.drawable.ic_s2_arrow_up, R.drawable.ic_s2_arrow_down, R.drawable.ic_s2_chevron_right, R.drawable.ic_s2_arrow_back, R.drawable.ic_s2_copy, R.drawable.ic_s2_group, R.drawable.ic_s2_delete};
        String[] tips = {Tr.t("uim_up", "Move up"), Tr.t("uim_down", "Move down"), Tr.t("uim_indent", "Move into the element above"), Tr.t("uim_outdent", "Move out of its parent"),
                Tr.t("uim_duplicate", "Duplicate"), Tr.t("uim_wrap", "Wrap in a container"), Tr.t("delete", "Delete")};
        for (int i = 0; i < ic.length; i++) {
            final int k = i;
            ImageView b = Ui.iconButton(act, ic[i], tips[i], v -> treeAction(k));
            if (selected == null || selected.parent == null) b.setAlpha(0.35f);
            acts.addView(b);
        }
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(acts);
        root.addView(hs, Ui.matchWrap());
        root.addView(Ui.divider(act));
        ScrollView sv = new ScrollView(act);
        LinearLayout list = Ui.column(act);
        list.setPadding(0, 0, 0, Ui.dp(act, 24));
        addTreeRows(list, doc.root, 0);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private void addTreeRows(LinearLayout list, final UINode n, int depth) {
        LinearLayout row = Ui.row(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(act, 42));
        row.setPadding(Ui.dp(act, 8 + depth * 16), 0, Ui.dp(act, 6), 0);
        boolean sel = n == selected;
        row.setBackgroundColor(sel ? Ui.ACCENT_SOFT : Color.TRANSPARENT);
        row.addView(Ui.smallIcon(act, UiIcons.forType(n.type), sel ? Ui.ACCENT : Ui.TEXT_2, 20));
        LinearLayout col = Ui.column(act);
        col.setPadding(Ui.dp(act, 10), 0, 0, 0);
        TextView id = Ui.text(act, n.id, 14, n.visible ? Ui.TEXT : Ui.TEXT_3);
        id.setMaxLines(1);
        col.addView(id);
        String extra = n.has("text") ? n.str("text", "") : n.type;
        if (extra.length() > 28) extra = extra.substring(0, 27) + "…";
        TextView sub = Ui.text(act, extra.replace('\n', ' '), 11, Ui.TEXT_3);
        sub.setMaxLines(1);
        col.addView(sub);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (n.parent != null) {
            ImageView eye = Ui.smallIcon(act, n.visible ? R.drawable.ic_s2_visible : R.drawable.ic_s2_visible_off, n.visible ? Ui.TEXT_2 : Ui.TEXT_3, 22);
            eye.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4));
            eye.setOnClickListener(v -> {
                flushHistory();
                n.visible = !n.visible;
                structural();
            });
            row.addView(eye, new LinearLayout.LayoutParams(Ui.dp(act, 36), Ui.dp(act, 36)));
        }
        row.setOnClickListener(v -> {
            selected = n;
            preview.invalidate();
            refreshTab();
        });
        list.addView(row, Ui.matchWrap());
        for (UINode c : n.children) addTreeRows(list, c, depth + 1);
    }

    private void treeAction(int k) {
        UINode n = selected;
        if (n == null) {
            Ui.toast(act, Tr.t("uim_nothing_selected_short", "Select an element first"));
            return;
        }
        flushHistory();
        String err = null;
        switch (k) {
            case 0: err = UiOps.moveUp(n); break;
            case 1: err = UiOps.moveDown(n); break;
            case 2: err = UiOps.indent(n); break;
            case 3: err = UiOps.outdent(n); break;
            case 4: {
                UINode c = doc.duplicate(n.id);
                if (c == null) err = Tr.t("uim_root_fixed", "The root cannot be duplicated");
                else selected = c;
                break;
            }
            case 5: {
                final String[] types = {UINode.PANEL, UINode.STACK, UINode.LINEAR, UINode.GRID, UINode.SCROLL};
                Dialogs.choose(act, Tr.t("uim_wrap", "Wrap in a container"), types, i -> {
                    String[] e = new String[1];
                    UINode box = UiOps.wrap(doc, n, types[i], e);
                    if (box == null) Ui.toast(act, e[0]);
                    else {
                        selected = box;
                        structural();
                    }
                });
                return;
            }
            default: {
                if (n.parent == null) {
                    err = Tr.t("uim_root_fixed", "The root cannot be deleted");
                    break;
                }
                final UINode victim = n;
                Dialogs.confirm(act, Tr.t("delete", "Delete"), String.format(Tr.t("uim_delete_q", "Delete %s and everything inside it?"), n.id), Tr.t("delete", "Delete"), () -> {
                    UINode p = victim.parent;
                    int idx = victim.indexInParent();
                    victim.removeFromParent();
                    selected = p == null ? null : p.children.isEmpty() ? p : p.children.get(Math.min(idx, p.children.size() - 1));
                    structural();
                });
                return;
            }
        }
        if (err != null) Ui.toast(act, err);
        else structural();
    }

    // ---- theme

    private View buildTheme() {
        LinearLayout root = Ui.column(act);
        root.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 24));
        root.addView(Ui.sectionLabel(act, Tr.t("uim_theme_preset", "Look of the whole UI")));
        LinearLayout chips = Ui.row(act);
        for (final String p : UITheme.PRESETS) {
            boolean on = p.equalsIgnoreCase(doc.theme.preset);
            LinearLayout chip = Ui.row(act);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 14), Ui.dp(act, 8));
            chip.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_3, Ui.dp(act, 18)));
            View dot = new View(act);
            UITheme.Look look = UITheme.look(p);
            dot.setBackground(Ui.rounded(look == null ? Color.GRAY : UiPropsView.toArgb(look.primary), Ui.dp(act, 8), 0xFFFFFFFF, Ui.dp(act, 1)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, 16));
            dl.setMarginEnd(Ui.dp(act, 8));
            chip.addView(dot, dl);
            chip.addView(Ui.text(act, p.replace('_', ' '), 13, on ? 0xFFFFFFFF : Ui.TEXT_2));
            chip.setOnClickListener(v -> {
                flushHistory();
                doc.theme.preset = p;
                edited();
                refreshTab();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.dp(act, 6));
            chips.addView(chip, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        root.addView(hs, Ui.matchWrap());

        root.addView(Ui.sectionLabel(act, Tr.t("uim_theme_colors", "Colors (tap to change, empty = preset)")));
        for (final String role : UITheme.ROLES) {
            LinearLayout r = Ui.row(act);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
            final String ov = doc.theme.colors.get(role);
            View sw = new View(act);
            sw.setBackground(Ui.rounded(UiPropsView.toArgb(doc.theme.color(role)), Ui.dp(act, 8), Ui.BORDER, Ui.dp(act, 1)));
            r.addView(sw, new LinearLayout.LayoutParams(Ui.dp(act, 44), Ui.dp(act, 34)));
            TextView t = Ui.text(act, role + (ov == null ? "" : "  " + ov), 14, ov == null ? Ui.TEXT_2 : Ui.TEXT);
            t.setPadding(Ui.dp(act, 10), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (ov != null) {
                ImageView reset = Ui.smallIcon(act, R.drawable.ic_s2_refresh, Ui.TEXT_3, 20);
                reset.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4));
                reset.setOnClickListener(v -> {
                    flushHistory();
                    doc.theme.colors.remove(role);
                    edited();
                    refreshTab();
                });
                r.addView(reset);
            }
            r.setOnClickListener(v -> ColorPickerDialog.show(act, role, ColorPickerDialog.toHex(UiPropsView.toArgb(doc.theme.color(role))), null, hex -> {
                if (hex == null) return;
                flushHistory();
                doc.theme.colors.put(role, "#" + hex.toLowerCase(Locale.ROOT));
                edited();
                refreshTab();
            }));
            root.addView(r, Ui.matchWrap());
        }

        root.addView(Ui.sectionLabel(act, Tr.t("uim_theme_numbers", "Shape")));
        String[][] nums = {{"radius", Tr.t("uim_n_radius", "Corner radius")}, {"borderWidth", Tr.t("uim_n_border", "Border width")}, {"shadow", Tr.t("uim_n_shadow", "Shadow")}, {"fontSize", Tr.t("uim_n_font", "Font size")}};
        for (final String[] nm : nums) {
            LinearLayout r = Ui.row(act);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
            r.addView(Ui.text(act, nm[1], 14, Ui.TEXT_2), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            boolean has = doc.theme.numbers.containsKey(nm[0]);
            TextView val = Ui.text(act, fmt(doc.theme.number(nm[0])) + (has ? "" : "  (" + Tr.t("uim_preset", "preset") + ")"), 14, has ? Ui.TEXT : Ui.TEXT_3);
            val.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
            val.setBackground(Ui.rounded(0xFF121417, Ui.dp(act, 8), Ui.BORDER, 1));
            val.setOnClickListener(v -> NumberPad.show(act, nm[1], fmt(doc.theme.number(nm[0])), value -> {
                try {
                    flushHistory();
                    doc.theme.numbers.put(nm[0], Float.parseFloat(value));
                    edited();
                    refreshTab();
                } catch (Exception ignored) {
                }
            }, null));
            r.addView(val);
            if (has) {
                ImageView reset = Ui.smallIcon(act, R.drawable.ic_s2_refresh, Ui.TEXT_3, 20);
                reset.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4));
                reset.setOnClickListener(v -> {
                    flushHistory();
                    doc.theme.numbers.remove(nm[0]);
                    edited();
                    refreshTab();
                });
                r.addView(reset);
            }
            root.addView(r, Ui.matchWrap());
        }

        root.addView(Ui.sectionLabel(act, Tr.t("uim_design_size", "Design size (used by fit contain / cover)")));
        LinearLayout ds = Ui.row(act);
        ds.setGravity(Gravity.CENTER_VERTICAL);
        TextView size = Ui.text(act, fmt(doc.designWidth) + " x " + fmt(doc.designHeight), 14, Ui.TEXT);
        ds.addView(size, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView use = Ui.text(act, Tr.t("uim_use_preview", "Use preview size"), 13, 0xFFFFFFFF);
        use.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
        use.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(act, 8)));
        use.setOnClickListener(v -> {
            flushHistory();
            doc.designWidth = lw();
            doc.designHeight = lh();
            edited();
            refreshTab();
        });
        ds.addView(use);
        root.addView(ds, Ui.matchWrap());
        return root;
    }

    private static String fmt(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------ device / strip state

    private int lw() {
        int s = DEVICES[device][0], l = DEVICES[device][1];
        return landscape ? l : s;
    }

    private int lh() {
        int s = DEVICES[device][0], l = DEVICES[device][1];
        return landscape ? s : l;
    }

    private void applyDevice() {
        preview.setLogicalSize(lw(), lh());
        preview.fit();
        updateBars();
        render(false, 0f);
    }

    private void updateZoomText() {
        zoomText.setText(Math.round(preview.zoom() * 100) + "%");
    }

    private void updateBars() {
        String n = name + ".ui" + (hist.dirty() || historyPending ? " *" : "");
        title.setText(n);
        undoBtn.setAlpha(hist.canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(hist.canRedo() ? 1f : 0.35f);
        modeChip.setText(testMode ? Tr.t("uim_mode_test", "Test") + " ▶" : Tr.t("uim_mode_edit", "Edit") + " ✎");
        modeChip.setTextColor(testMode ? Ui.SUCCESS : Ui.TEXT);
        orientChip.setText(landscape ? Tr.t("uim_landscape", "Landscape") : Tr.t("uim_portrait", "Portrait"));
        deviceChip.setText(lw() + "x" + lh());
        gridChip.setText(GRIDS[gridIdx] == 0 ? Tr.t("uim_grid_off", "Grid off") : Tr.t("uim_grid", "Grid") + " " + GRIDS[gridIdx]);
        bgChip.setText(Tr.t("uim_bg", "Background"));
        updateZoomText();
        updateWarnings();
    }

    private void updateWarnings() {
        int n;
        try {
            n = UiLint.check(doc, lw(), lh(), UILayout.APPROX).size();
        } catch (Throwable t) {
            n = 0;
        }
        warnBadge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        warnBadge.setText(String.valueOf(n));
    }

    private void setTestMode(boolean on) {
        testMode = on;
        main.removeCallbacks(testTick);
        updateBars();
        if (on) {
            Ui.toast(act, Tr.t("uim_test_hint", "Test mode: buttons, sliders and lists work like in the game"));
            render(false, 0f);
            main.postDelayed(testTick, 60);
        } else render(false, 0f);
        preview.invalidate();
    }

    // ------------------------------------------------------------------ rendering

    private void render(boolean keep, float delta) {
        if (dialog == null || preview == null) return;
        int lw = lw(), lh = lh();
        int pw = Math.min(lw, preview.wantedPixelWidth());
        int ph = Math.max(16, Math.round(pw * lh / (float) lw));
        if (Gdx.app == null || !engine.available()) {
            if (glOk) glOk = false;
            preview.setLogicalSize(lw, lh);
            preview.setWire(UiRects.compute(doc, lw, lh, UILayout.APPROX));
            return;
        }
        glOk = true;
        engine.request(doc.toJsonString(), lw, lh, pw, ph, BGS[bgIdx], keep, delta);
        // wireframe immediately when there is no picture yet (first frame)
        if (lastFrameAt == 0) preview.setWire(UiRects.compute(doc, lw, lh, UILayout.APPROX));
    }

    @Override
    public void onFrame(Bitmap bmp, UiPreviewGl.Frame f) {
        lastFrameAt = System.currentTimeMillis();
        lastPw = f.pw;
        UiRects r = f.lw == lw() && f.lh == lh() ? UiRects.fromMap(doc, f.lw, f.lh, f.rects) : preview.rects();
        preview.setLogicalSize(lw(), lh());
        preview.setFrame(bmp, r);
    }

    @Override
    public void onError(String message) {
        // keep the last picture; show the wireframe so editing stays possible
        preview.setWire(UiRects.compute(doc, lw(), lh(), UILayout.APPROX));
        if (!warnedField) {
            warnedField = true;
            Ui.toast(act, Tr.t("uim_preview_error", "Preview error: ") + message);
        }
    }

    // ------------------------------------------------------------------ history / changes

    private void flushHistory() {
        main.removeCallbacks(historyFlush);
        if (historyPending) {
            historyPending = false;
            hist.record(doc);
            updateBars();
        }
    }

    /** A property changed: render now, record history soon. */
    @Override
    public void edited() {
        historyPending = true;
        main.removeCallbacks(historyFlush);
        main.postDelayed(historyFlush, 700);
        render(false, 0f);
        updateBars();
    }

    /** The tree changed: record history now and refresh what shows the tree. */
    private void structural() {
        historyPending = false;
        main.removeCallbacks(historyFlush);
        hist.record(doc);
        render(false, 0f);
        updateBars();
        if (tab != 0) refreshTab();
    }

    private void undo() {
        flushHistory();
        UIDocument d = hist.undo();
        if (d != null) swap(d);
    }

    private void redo() {
        flushHistory();
        UIDocument d = hist.redo();
        if (d != null) swap(d);
    }

    private void swap(UIDocument d) {
        String id = selected == null ? null : selected.id;
        doc = d;
        doc.name = name;
        selected = id == null ? null : doc.find(id);
        render(false, 0f);
        updateBars();
        refreshTab();
    }

    // ---- UiPreviewView.Host

    @Override
    public UIDocument doc() {
        return doc;
    }

    @Override
    public UINode selected() {
        return selected;
    }

    @Override
    public void select(UINode n) {
        selected = n;
        preview.invalidate();
        if (tab == 0 && n != null) showTab(2);
        else if (tab != 0) refreshTab();
    }

    @Override
    public void gestureStart() {
        flushHistory();
    }

    @Override
    public void gestureChanged() {
        render(false, 0f);
    }

    @Override
    public void gestureEnd(boolean changed) {
        if (changed) {
            hist.record(doc);
            render(false, 0f);
            updateBars();
            if (tab != 0) refreshTab();
        } else render(false, 0f);
    }

    @Override
    public boolean testMode() {
        return testMode;
    }

    @Override
    public int grid() {
        return GRIDS[gridIdx];
    }

    @Override
    public void testTouch(int type, float px, float py) {
        if (type == 0) {
            float k = lastPw / (float) lw();
            UiRects r = preview.rects();
            UiRects.R hit = r == null ? null : r.hit(px / k, py / k);
            warnedFieldTouch = hit != null && hit.node.type.equals(UINode.TEXTFIELD);
            if (warnedFieldTouch) Ui.toast(act, Tr.t("uim_field_hint", "Typing is not available in the preview: set the field's text in Properties"));
        }
        if (warnedFieldTouch) return;
        engine.touch(type, px, py);
        render(true, 0.016f);
    }

    private boolean warnedFieldTouch;

    @Override
    public void say(String msg) {
        Ui.toast(act, msg);
    }

    // ---- UiPropsView.Host

    @Override
    public Activity activity() {
        return act;
    }

    @Override
    public String projectPath() {
        return project;
    }

    @Override
    public void idChanged() {
        // the tree shows ids: refreshed when its tab opens
    }

    @Override
    public UiRects.R rectOf(String id) {
        UiRects r = preview == null ? null : preview.rects();
        return r == null ? null : r.get(id);
    }

    // ------------------------------------------------------------------ save and menus

    private File fileFor(String n) {
        return new File(UiMakerLauncher.uiDir(project), n + ".ui");
    }

    private boolean save() {
        flushHistory();
        try {
            doc.name = name;
            doc.validate();
            doc.write(fileFor(name));
            hist.markSaved();
            fileExists = true;
            updateBars();
            Ui.toast(act, String.format(Tr.t("uim_saved", "Saved %s.ui"), name));
            if (onSaved != null) onSaved.run();
            return true;
        } catch (Exception e) {
            Ui.toast(act, Tr.t("uim_save_failed", "Could not save: ") + e.getMessage());
            return false;
        }
    }

    private void saveAs() {
        Dialogs.input(act, Tr.t("uim_save_as", "Save as"), Tr.t("uim_save_as_msg", "Name of the UI file (letters, digits, _)"), name, Dialogs::checkName, value -> {
            final String n = value.trim();
            if (n.equals(name)) {
                save();
                return;
            }
            if (fileFor(n).exists()) {
                Dialogs.confirm(act, Tr.t("uim_overwrite", "Replace?"), String.format(Tr.t("uim_overwrite_msg", "%s.ui already exists. Replace it?"), n), Tr.t("uim_replace", "Replace"), () -> {
                    name = n;
                    save();
                });
            } else {
                name = n;
                save();
            }
        });
    }

    private void moreMenu() {
        final String[] items = {Tr.t("uim_save_as", "Save as"), Tr.t("uim_use_template", "Insert a ready-made screen"), Tr.t("uim_json", "Show / copy JSON"),
                Tr.t("uim_clear", "Clear everything"), Tr.t("uim_warnings", "Layout warnings")};
        Dialogs.choose(act, Tr.t("more", "More"), items, i -> {
            switch (i) {
                case 0: saveAs(); break;
                case 1: askTemplate(false); break;
                case 2: Dialogs.longText(act, name + ".ui", doc.toJsonString()); break;
                case 3:
                    Dialogs.confirm(act, Tr.t("uim_clear", "Clear everything"), Tr.t("uim_clear_q", "Remove all elements? (undo brings them back)"), Tr.t("uim_clear", "Clear"), () -> {
                        flushHistory();
                        doc.root.children.clear();
                        selected = null;
                        structural();
                    });
                    break;
                default: showWarnings(); break;
            }
        });
    }

    private void askStart() {
        askTemplate(true);
    }

    /** New file: start blank or from a ready-made screen. */
    private void askTemplate(final boolean blankFirst) {
        List<String> names = UiTemplates.names();
        final ArrayList<String> labels = new ArrayList<>();
        final ArrayList<String> keys = new ArrayList<>();
        if (blankFirst) {
            labels.add(Tr.t("uim_blank", "Blank"));
            keys.add("");
        }
        for (String t : names) {
            labels.add(Tr.t("uim_tpl_" + t, UiTemplates.INFO.get(t)[0]));
            keys.add(t);
        }
        Dialogs.choose(act, blankFirst ? Tr.t("uim_start_from", "Start from") : Tr.t("uim_use_template", "Insert a ready-made screen"), labels.toArray(new String[0]), i -> {
            String t = keys.get(i);
            if (t.isEmpty()) return;
            addTemplate(t);
        });
    }

    private void showWarnings() {
        int a = Math.max(lw(), lh()), b = Math.min(lw(), lh());
        List<String> w = UiLint.checkBoth(doc, a, b, UILayout.APPROX);
        if (w.isEmpty()) {
            Ui.toast(act, Tr.t("uim_no_warnings", "No layout problems found (text sizes are approximate)"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : w) sb.append("• ").append(s).append("\n\n");
        Dialogs.longText(act, Tr.t("uim_warnings", "Layout warnings") + " (" + w.size() + ")", sb.toString().trim());
    }
}
