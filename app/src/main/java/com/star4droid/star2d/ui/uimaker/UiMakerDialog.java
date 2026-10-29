package com.star4droid.star2d.ui.uimaker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
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
import com.star4droid.star2d.ui.Snack;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UI Maker: full-screen editor of a project's {@code UI/<name>.ui} file ({@code name} may contain folders: {@code menus/pause}).
 *
 * Top: live preview in a device frame (real renderer, tap to select, drag to move, handles to resize, handles on the frame to resize the
 * phone itself, pinch to zoom, test mode).
 * Bottom: tabs Elements (palette + templates) / Tree / Properties / Theme; the panel can be dragged to any size and hidden.
 * Toolbar: undo, redo, warnings, more, save. See docs/UI.md for the file format.
 */
public final class UiMakerDialog implements UiPreviewView.Host, UiPropsView.Host, PreviewEngine.Listener {
    /** Entry used by {@link UiMakerLauncher}. */
    public static void open(Context c, String projectPath, String uiName, Runnable onSaved) {
        Activity a = unwrap(c);
        if (a == null) {
            Ui.toast(c, Tr.t("uim_need_activity", "Cannot open the UI Maker from here"));
            return;
        }
        new UiMakerDialog(a, projectPath, cleanName(uiName), onSaved).show();
    }

    private static String cleanName(String n) {
        String s = n == null ? "" : n.replace('\\', '/').replaceAll("(?i)\\.ui$", "").trim();
        while (s.startsWith("/")) s = s.substring(1);
        if (s.contains("..") || s.endsWith("/")) return "";
        return s;
    }

    private static Activity unwrap(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    // ------------------------------------------------------------------ state
    /** Screen presets, portrait {short, long} in logical px. */
    private static final int[][] PHONES = {{320, 480}, {360, 640}, {360, 720}, {360, 780}, {375, 667}, {390, 844}, {393, 852}, {412, 915}, {414, 896}, {430, 932},
            {480, 854}, {540, 960}, {720, 1280}, {720, 1600}, {750, 1334}, {1080, 1920}, {1080, 2340}, {1080, 2400}, {1440, 2560}, {1440, 3120}};
    private static final int[][] TABLETS = {{600, 1024}, {768, 1024}, {800, 1280}, {820, 1180}, {1200, 1920}, {1600, 2560}};
    private static final int[] GRIDS = {0, 8, 16, 32};
    private static final int[] BGS = {0xFF1C2230, 0xFF000000, 0xFFFFFFFF, 0xFF6C8FB8};
    private static final Pattern SIZE_TEXT = Pattern.compile("^\\s*(\\d{1,5})\\s*[xX×*,\\s]\\s*(\\d{1,5})\\s*$");

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
    /** Logical size of the phone in the preview (starts from the document's design size, changed by the chips and the frame handles). */
    private int curW = 1280, curH = 720;
    private int gridIdx = 0;
    private int bgIdx = 0;
    private int tab = 0;
    /** Theme tab: false = whole UI, true = the selected element only. */
    private boolean themeScopeSel;

    private Dialog dialog;
    private FrameLayout host;
    private UiPreviewView preview;
    private PreviewEngine engine;
    private FrameLayout body;
    private View stripView;
    private TextView title, warnBadge, zoomText, saveBtn;
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

    // bottom panel (resizable / hideable)
    private Split split;
    private Grabber grabber;
    private LinearLayout panel, panelContent;
    private float fracP = 0.58f, fracL = 0.45f;
    private boolean collapsed;
    /** Panel size in px while it is dragged or animated; -1 = derived from the fraction / collapsed state. */
    private float shownPx = -1;
    private ValueAnimator panelAnim;

    private UiMakerDialog(Activity a, String project, String name, Runnable onSaved) {
        this.act = a;
        this.project = project;
        this.name = name.isEmpty() ? "ui" : name;
        this.onSaved = onSaved;
        File file = fileFor(this.name);
        fileExists = file.isFile();
        doc = fileExists ? UIDocument.read(file) : UiOps.newDocument(this.name, 1280, 720);
        if (doc.warnings.size() > 0 && fileExists) {
            // read problems are shown once, the document is still usable
            Ui.toast(a, doc.warnings.get(0));
        }
        doc.name = this.name;
        hist = new UiHistory(doc);
        sizeFromDoc();
        try {
            gridIdx = Math.min(GRIDS.length - 1, Ui.prefs(a).getInt("uim_grid", 0));
            bgIdx = Math.min(BGS.length - 1, Ui.prefs(a).getInt("uim_bg", 0));
            fracP = clampFrac(Ui.prefs(a).getFloat("uim_split_p", fracP));
            fracL = clampFrac(Ui.prefs(a).getFloat("uim_split_l", fracL));
            collapsed = Ui.prefs(a).getBoolean("uim_split_collapsed", false);
        } catch (Throwable ignored) {
        }
    }

    private static float clampFrac(float f) {
        return Float.isNaN(f) ? 0.5f : Math.max(0.2f, Math.min(0.8f, f));
    }

    private void sizeFromDoc() {
        curW = clampSize(Math.round(doc.designWidth));
        curH = clampSize(Math.round(doc.designHeight));
    }

    private static int clampSize(int v) {
        if (v <= 0) return 720;
        return Math.max(UiPreviewView.MIN_SIZE, Math.min(UiPreviewView.MAX_SIZE, v));
    }

    // ------------------------------------------------------------------ window

    private void show() {
        engine = new PreviewEngine(project, this);
        dialog = new Dialog(act, android.R.style.Theme_Black_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        // the content sits in a FrameLayout that also hosts the snackbars (activity level ones would be hidden behind this window)
        host = new FrameLayout(act);
        host.setLayoutDirection(Ui.isRtl(act) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        host.addView(buildRoot(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(host);
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
            main.removeCallbacks(restoreSave);
            if (panelAnim != null) panelAnim.cancel();
            Snack.popHost(host);
            engine.dispose();
        });
        Snack.pushHost(host);
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
        confirmUnsaved(this::close);
    }

    /** Runs {@code then} when there is nothing unsaved, or after the person saved or discarded the changes. */
    private void confirmUnsaved(final Runnable then) {
        flushHistory();
        if (!hist.dirty()) {
            then.run();
            return;
        }
        new AlertDialog.Builder(act)
                .setTitle(Tr.t("uim_unsaved", "Unsaved changes"))
                .setMessage(Tr.t("uim_unsaved_msg", "Save the changes to this UI before closing?"))
                .setPositiveButton(Tr.t("save", "Save"), (d, i) -> {
                    if (save()) then.run();
                })
                .setNegativeButton(Tr.t("uim_discard", "Discard"), (d, i) -> then.run())
                .setNeutralButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    // ------------------------------------------------------------------ layout

    /**
     * Portrait: preview on top, panel below. Landscape: side by side. The panel has a fixed size in px (a fraction of the available
     * space, or only its grabber when it is hidden); the preview takes what is left.
     */
    private final class Split extends LinearLayout {
        private int lastLand = -1;
        View a;

        Split(Context c) {
            super(c);
        }

        boolean land() {
            return lastLand == 1;
        }

        int total() {
            return land() ? getWidth() : getHeight();
        }

        int handlePx() {
            return Ui.dp(act, 26);
        }

        int clampPx(float px, int total) {
            int max = Math.max(handlePx(), Math.min(total - Ui.dp(act, 64), Math.round(total * 0.82f)));
            return Math.round(Math.max(handlePx(), Math.min(max, px)));
        }

        float frac() {
            return land() ? fracL : fracP;
        }

        int restingPx(int total) {
            return collapsed ? handlePx() : clampPx(frac() * total, total);
        }

        int panelPx(int total) {
            return shownPx >= 0 ? clampPx(shownPx, total) : restingPx(total);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            int land = w > h ? 1 : 0;
            if (a != null && panel != null) {
                if (land != lastLand) {
                    lastLand = land;
                    setOrientation(land == 1 ? HORIZONTAL : VERTICAL);
                    panel.setOrientation(land == 1 ? HORIZONTAL : VERTICAL);
                    grabber.setVertical(land == 1);
                    grabber.setLayoutParams(new LinearLayout.LayoutParams(land == 1 ? handlePx() : ViewGroup.LayoutParams.MATCH_PARENT, land == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : handlePx()));
                    panelContent.setLayoutParams(new LinearLayout.LayoutParams(land == 1 ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, land == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : 0, 1));
                }
                int px = panelPx(land == 1 ? w : h);
                LayoutParams la = (LayoutParams) a.getLayoutParams();
                la.width = land == 1 ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
                la.height = land == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
                la.weight = 1;
                LayoutParams lb = (LayoutParams) panel.getLayoutParams();
                lb.width = land == 1 ? px : ViewGroup.LayoutParams.MATCH_PARENT;
                lb.height = land == 1 ? ViewGroup.LayoutParams.MATCH_PARENT : px;
                lb.weight = 0;
                int vis = px <= handlePx() + 2 ? View.GONE : View.VISIBLE;
                if (panelContent.getVisibility() != vis) panelContent.setVisibility(vis);
            }
            super.onMeasure(wSpec, hSpec);
        }
    }

    /** The bar on the panel edge that faces the preview: drag it to resize the panel, tap it to hide / show the panel. */
    private final class Grabber extends View {
        private boolean vertical;
        private boolean down, dragging;
        private float downRaw, startPx, desired;
        private int sign = -1;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int slop = ViewConfiguration.get(act).getScaledTouchSlop();

        Grabber(Context c) {
            super(c);
            setBackgroundColor(Ui.SURFACE_2);
            setContentDescription(Tr.t("uim_panel_toggle", "Resize or hide the panel"));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setVertical(boolean v) {
            vertical = v;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            float d = Ui.dp(act, 1);
            boolean hidden = collapsed && !dragging && shownPx < 0;
            p.setColor(down || dragging ? Ui.ACCENT : hidden ? Ui.TEXT_2 : Ui.TEXT_3);
            p.setStrokeWidth(Ui.dp(act, hidden ? 2.5f : 4));
            if (!hidden) {
                float half = Ui.dp(act, 20);
                if (vertical) c.drawLine(cx, cy - half, cx, cy + half, p);
                else c.drawLine(cx - half, cy, cx + half, cy, p);
                return;
            }
            // hidden: a chevron that points to where the panel will grow
            path.reset();
            if (vertical) {
                boolean towardLeft = split != null && split.a != null && split.a.getLeft() < panel.getLeft();
                float k = towardLeft ? 1 : -1;
                path.moveTo(cx + 3 * d * k, cy - 7 * d);
                path.lineTo(cx - 3 * d * k, cy);
                path.lineTo(cx + 3 * d * k, cy + 7 * d);
            } else {
                path.moveTo(cx - 7 * d, cy + 3 * d);
                path.lineTo(cx, cy - 3 * d);
                path.lineTo(cx + 7 * d, cy + 3 * d);
            }
            c.drawPath(path, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (panelAnim != null) panelAnim.cancel();
                    down = true;
                    dragging = false;
                    downRaw = vertical ? e.getRawX() : e.getRawY();
                    startPx = vertical ? panel.getWidth() : panel.getHeight();
                    boolean panelAfter = split.a != null && panel.getLeft() > split.a.getLeft();
                    sign = vertical ? (panelAfter ? -1 : 1) : -1;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (!down) return true;
                    float delta = (vertical ? e.getRawX() : e.getRawY()) - downRaw;
                    if (!dragging && Math.abs(delta) > slop) dragging = true;
                    if (dragging) {
                        desired = startPx + sign * delta;
                        shownPx = split.clampPx(desired, split.total());
                        split.requestLayout();
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean wasDragging = dragging;
                    boolean cancel = e.getActionMasked() == MotionEvent.ACTION_CANCEL;
                    down = false;
                    dragging = false;
                    if (wasDragging) releaseDrag(desired);
                    else if (!cancel) setCollapsed(!collapsed, true);
                    invalidate();
                    return true;
                }
                default:
                    return true;
            }
        }
    }

    /** A drag of the grabber ended with the panel at {@code desired} px: hide it when it is nearly closed, otherwise keep that size. */
    private void releaseDrag(float desired) {
        int total = split.total();
        if (total <= 0) return;
        float f = desired / total;
        if (f < 0.12f) {
            collapsed = true;
        } else {
            collapsed = false;
            if (split.land()) fracL = clampFrac(f);
            else fracP = clampFrac(f);
        }
        saveSplitPrefs();
        animatePanel(split.restingPx(total));
    }

    private void setCollapsed(boolean c, boolean animate) {
        collapsed = c;
        saveSplitPrefs();
        int total = split == null ? 0 : split.total();
        if (!animate || total <= 0) {
            shownPx = -1;
            if (split != null) split.requestLayout();
            return;
        }
        animatePanel(split.restingPx(total));
    }

    private void saveSplitPrefs() {
        try {
            Ui.prefs(act).edit().putFloat("uim_split_p", fracP).putFloat("uim_split_l", fracL).putBoolean("uim_split_collapsed", collapsed).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Moves the panel from where it is now to {@code to} px with a short ease; afterwards the size is derived from the state again. */
    private void animatePanel(float to) {
        if (panelAnim != null) panelAnim.cancel();
        float from = shownPx >= 0 ? shownPx : (split.land() ? panel.getWidth() : panel.getHeight());
        final boolean[] cancelled = new boolean[1];
        panelAnim = ValueAnimator.ofFloat(from, to);
        panelAnim.setDuration(220);
        panelAnim.setInterpolator(new DecelerateInterpolator());
        panelAnim.addUpdateListener(a -> {
            shownPx = (float) a.getAnimatedValue();
            split.requestLayout();
            grabber.invalidate();
        });
        panelAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled[0] = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled[0]) return;
                shownPx = -1;
                split.requestLayout();
                grabber.invalidate();
            }
        });
        panelAnim.start();
    }

    private View buildRoot() {
        LinearLayout root = Ui.column(act);
        root.setBackgroundColor(Ui.BG);
        root.addView(buildToolbar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 52)));
        root.addView(Ui.divider(act));
        split = new Split(act);
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
        stripView = buildPreviewStrip();
        pv.addView(stripView, sl);
        // the phone is centered above the chips, not behind them
        stripView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> preview.setBottomInset(b - t));

        panel = Ui.column(act);
        panel.setBackgroundColor(Ui.SURFACE);
        grabber = new Grabber(act);
        panel.addView(grabber, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 26)));
        panelContent = Ui.column(act);
        panelContent.addView(buildTabBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 48)));
        panelContent.addView(Ui.divider(act));
        body = new FrameLayout(act);
        panelContent.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        panel.addView(panelContent, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        split.a = pv;
        split.addView(pv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        split.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 200)));
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
        title.setEllipsize(android.text.TextUtils.TruncateAt.START);
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
        saveBtn = Ui.text(act, Tr.t("save", "Save"), 14, 0xFFFFFFFF);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
        saveBtn.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(act, 8)));
        saveBtn.setOnClickListener(v -> save());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(act, 38));
        sp.setMarginStart(Ui.dp(act, 4));
        bar.addView(saveBtn, sp);
        return bar;
    }

    /** The Save button turns green and reads "Saved" for a moment (next to the snackbar, so a save is never missed). */
    private void flashSaved() {
        if (saveBtn == null) return;
        main.removeCallbacks(restoreSave);
        saveBtn.setText("✓ " + Tr.t("uim_saved_short", "Saved"));
        saveBtn.setBackground(Ui.rounded(Ui.SUCCESS, Ui.dp(act, 8)));
        main.postDelayed(restoreSave, 1600);
    }

    private final Runnable restoreSave = new Runnable() {
        @Override
        public void run() {
            if (saveBtn == null) return;
            saveBtn.setText(Tr.t("save", "Save"));
            saveBtn.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(act, 8)));
        }
    };

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
            int t = curW;
            curW = curH;
            curH = t;
            applyDevice();
        });
        deviceChip = stripChip("", v -> showSizePicker());
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
            t.setOnClickListener(v -> {
                // choosing a tab while the panel is hidden shows it again
                if (collapsed) setCollapsed(false, true);
                showTab(idx);
            });
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
            LinearLayout card = Ui.column(act);
            card.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
            card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(act, 10), Ui.BORDER, 1));
            TextView n = Ui.text(act, UiTr.templateName(t), 14, Ui.TEXT);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(n);
            TextView d = Ui.text(act, UiTr.templateDesc(t), 11.5f, Ui.TEXT_3);
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
            root.addView(Ui.sectionLabel(act, UiTr.categoryTitle(cat)));
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

    private View widgetCard(final UIWidgetRegistry.Widget w) {
        LinearLayout card = Ui.row(act);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 10));
        card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(act, 10), Ui.BORDER, 1));
        card.addView(Ui.smallIcon(act, UiIcons.forType(w.type), Ui.ACCENT, 26));
        LinearLayout col = Ui.column(act);
        col.setPadding(Ui.dp(act, 10), 0, 0, 0);
        TextView n = Ui.text(act, UiTr.widgetName(w.type, w.name), 14, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(n);
        TextView d = Ui.text(act, UiTr.widgetDesc(w.type, w.desc), 11, Ui.TEXT_3);
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
            Ui.toast(act, UiTr.err(err[0]));
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

    private void unlockFirst() {
        Ui.toast(act, Tr.t("uim_unlock_first", "This element is locked. Unlock it first."));
    }

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
            if (selected == null || selected.parent == null || (selected.locked && i != 4)) b.setAlpha(0.35f);
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
        String extra = n.has("text") ? n.str("text", "") : UiTr.widgetName(n.type);
        if (extra.length() > 28) extra = extra.substring(0, 27) + "…";
        TextView sub = Ui.text(act, extra.replace('\n', ' '), 11, Ui.TEXT_3);
        sub.setMaxLines(1);
        col.addView(sub);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        // own theme: a dot in the preset's main color
        String own = n.has("theme") ? n.str("theme", "").trim() : "";
        if (!own.isEmpty() && UITheme.isPreset(own)) {
            View dot = new View(act);
            dot.setBackground(Ui.rounded(UiPropsView.toArgb(UITheme.look(own).primary), Ui.dp(act, 6), 0xFFFFFFFF, Ui.dp(act, 1)));
            dot.setContentDescription(UiTr.presetName(own));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(act, 12), Ui.dp(act, 12));
            dl.setMarginEnd(Ui.dp(act, 4));
            dot.setOnClickListener(v -> Ui.toast(act, id.getText() + ": " + UiTr.presetName(own)));
            row.addView(dot, dl);
        }
        if (n.parent != null) {
            ImageView lock = Ui.smallIcon(act, n.locked ? R.drawable.ic_s2_lock : R.drawable.ic_s2_lock_open, n.locked ? Ui.WARNING : Ui.TEXT_3, 20);
            lock.setPadding(Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 6), Ui.dp(act, 6));
            lock.setContentDescription(n.locked ? Tr.t("uim_unlock", "Unlock") : Tr.t("uim_lock", "Lock (cannot be selected or moved in the preview)"));
            lock.setOnClickListener(v -> {
                flushHistory();
                n.locked = !n.locked;
                structural();
            });
            row.addView(lock, new LinearLayout.LayoutParams(Ui.dp(act, 36), Ui.dp(act, 36)));
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
        if (n.locked && n.parent != null && k != 4) {
            unlockFirst();
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
                if (c == null) err = Tr.t("uim_root_fixed_dup", "The root cannot be duplicated");
                else selected = c;
                break;
            }
            case 5: {
                final String[] types = {UINode.PANEL, UINode.STACK, UINode.LINEAR, UINode.GRID, UINode.SCROLL};
                String[] labels = new String[types.length];
                for (int i = 0; i < types.length; i++) labels[i] = UiTr.widgetName(types[i]);
                Dialogs.choose(act, Tr.t("uim_wrap", "Wrap in a container"), labels, i -> {
                    String[] e = new String[1];
                    UINode box = UiOps.wrap(doc, n, types[i], e);
                    if (box == null) Ui.toast(act, UiTr.err(e[0]));
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
        if (err != null) Ui.toast(act, UiTr.err(err));
        else structural();
    }

    // ---- theme

    /** Two-way switch of the Theme tab. */
    private View segment(String text, boolean on, boolean enabled, View.OnClickListener l) {
        TextView t = Ui.text(act, text, 13, on ? 0xFFFFFFFF : Ui.TEXT_2);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(act, 12), Ui.dp(act, 9), Ui.dp(act, 12), Ui.dp(act, 9));
        t.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_3, Ui.dp(act, 10)));
        t.setAlpha(enabled ? 1f : 0.4f);
        if (enabled) t.setOnClickListener(l);
        return t;
    }

    private View buildTheme() {
        LinearLayout root = Ui.column(act);
        root.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 24));
        final UINode sel = selected != null && selected.parent != null ? selected : null;
        if (sel == null) themeScopeSel = false;
        // scope: the whole UI, or only the selected element (and what is inside it)
        LinearLayout scope = Ui.row(act);
        LinearLayout.LayoutParams sw = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        sw.setMarginEnd(Ui.dp(act, 6));
        scope.addView(segment(Tr.t("uim_scope_all", "Whole UI"), !themeScopeSel, true, v -> {
            themeScopeSel = false;
            refreshTab();
        }), sw);
        scope.addView(segment(sel == null ? Tr.t("uim_scope_sel_none", "Selected element") : String.format(Tr.t("uim_scope_sel", "Selected: %s"), sel.id), themeScopeSel, sel != null, v -> {
            themeScopeSel = true;
            refreshTab();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(scope, Ui.matchWrap());

        if (themeScopeSel && sel != null) {
            root.addView(Ui.sectionLabel(act, String.format(Tr.t("uim_theme_of", "Look of %s"), sel.id)));
            final String own = sel.has("theme") ? sel.str("theme", "").trim() : "";
            LinearLayout chips = Ui.row(act);
            for (final String p : withEmpty(UITheme.PRESETS)) {
                chips.addView(UiPropsView.presetChip(act, p, p.equalsIgnoreCase(own), true, v -> {
                    flushHistory();
                    String err = UIWidgetRegistry.set(sel, "theme", p);
                    if (err != null) Ui.toast(act, UiTr.err(err));
                    edited();
                    refreshTab();
                }));
            }
            HorizontalScrollView hs = new HorizontalScrollView(act);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(chips);
            root.addView(hs, Ui.matchWrap());
            TextView note = Ui.text(act, Tr.t("uim_theme_sel_note", "Only this element and everything inside it. It uses the chosen preset as it is, so the colors and shape set for the whole UI do not apply to it."), 12, Ui.TEXT_3);
            note.setPadding(0, Ui.dp(act, 8), 0, 0);
            root.addView(note);
        } else {
            addDocTheme(root);
        }
        addDesignSize(root);
        return root;
    }

    private static String[] withEmpty(String[] a) {
        String[] r = new String[a.length + 1];
        r[0] = "";
        System.arraycopy(a, 0, r, 1, a.length);
        return r;
    }

    /** Preset, colors and numbers of the document theme. */
    private void addDocTheme(LinearLayout root) {
        root.addView(Ui.sectionLabel(act, Tr.t("uim_theme_preset", "Look of the whole UI")));
        LinearLayout chips = Ui.row(act);
        for (final String p : UITheme.PRESETS) {
            boolean on = p.equalsIgnoreCase(doc.theme.preset);
            chips.addView(UiPropsView.presetChip(act, p, on, true, v -> {
                flushHistory();
                doc.theme.preset = p;
                edited();
                refreshTab();
            }));
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
            TextView t = Ui.text(act, UiTr.roleName(role) + (ov == null ? "" : "  " + ov), 14, ov == null ? Ui.TEXT_2 : Ui.TEXT);
            t.setPadding(Ui.dp(act, 10), 0, 0, 0);
            r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (ov != null) {
                ImageView reset = Ui.smallIcon(act, R.drawable.ic_s2_refresh, Ui.TEXT_3, 20);
                reset.setPadding(Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4), Ui.dp(act, 4));
                reset.setContentDescription(Tr.t("uim_reset", "Reset"));
                reset.setOnClickListener(v -> {
                    flushHistory();
                    doc.theme.colors.remove(role);
                    edited();
                    refreshTab();
                });
                r.addView(reset);
            }
            r.setOnClickListener(v -> ColorPickerDialog.show(act, UiTr.roleName(role), ColorPickerDialog.toHex(UiPropsView.toArgb(doc.theme.color(role))), null, hex -> {
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
                reset.setContentDescription(Tr.t("uim_reset", "Reset"));
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
    }

    private void addDesignSize(LinearLayout root) {
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
    }

    private static String fmt(float v) {
        return v == (int) v ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------ device / strip state

    private int lw() {
        return curW;
    }

    private int lh() {
        return curH;
    }

    private boolean landscape() {
        return curW >= curH;
    }

    private void applyDevice() {
        preview.setLogicalSize(lw(), lh());
        preview.fit();
        updateBars();
        render(false, 0f);
    }

    private void setSize(int w, int h) {
        curW = clampSize(w);
        curH = clampSize(h);
        applyDevice();
    }

    private View sizeSection(String text) {
        TextView t = Ui.sectionLabel(act, text);
        return t;
    }

    private View sizeRow(final String label, final int w, final int h, final AlertDialog[] dlg) {
        boolean on = w == curW && h == curH;
        LinearLayout r = Ui.row(act);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(act, 44));
        r.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
        r.setBackgroundColor(on ? Ui.ACCENT_SOFT : Color.TRANSPARENT);
        TextView t = Ui.text(act, label, 14, on ? Ui.ACCENT : Ui.TEXT);
        if (on) t.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (on) r.addView(Ui.smallIcon(act, R.drawable.ic_s2_check, Ui.ACCENT, 20));
        r.setOnClickListener(v -> {
            if (dlg[0] != null) dlg[0].dismiss();
            setSize(w, h);
        });
        return r;
    }

    /** Screen size picker: phones, tablets and ratio helpers in the current orientation, plus a custom size. */
    private void showSizePicker() {
        final boolean land = landscape();
        final AlertDialog[] dlg = new AlertDialog[1];
        ScrollView sv = new ScrollView(act);
        LinearLayout col = Ui.column(act);
        sv.addView(col);
        col.addView(sizeSection(Tr.t("uim_size_phones", "Phones")));
        for (int[] d : PHONES) col.addView(sizeRow(d[land ? 1 : 0] + " x " + d[land ? 0 : 1], d[land ? 1 : 0], d[land ? 0 : 1], dlg), Ui.matchWrap());
        col.addView(sizeSection(Tr.t("uim_size_tablets", "Tablets")));
        for (int[] d : TABLETS) col.addView(sizeRow(d[land ? 1 : 0] + " x " + d[land ? 0 : 1], d[land ? 1 : 0], d[land ? 0 : 1], dlg), Ui.matchWrap());
        col.addView(sizeSection(Tr.t("uim_size_ratios", "Ratios (keeps the long side)")));
        int longSide = Math.max(curW, curH);
        int[][] ratios = {{16, 9}, {4, 3}};
        for (int[] r : ratios) {
            int shortSide = clampSize(Math.round(longSide * r[1] / (float) r[0]));
            col.addView(sizeRow(r[0] + ":" + r[1] + "   " + (land ? longSide : shortSide) + " x " + (land ? shortSide : longSide), land ? longSide : shortSide, land ? shortSide : longSide, dlg), Ui.matchWrap());
        }
        LinearLayout custom = Ui.row(act);
        custom.setGravity(Gravity.CENTER_VERTICAL);
        custom.setMinimumHeight(Ui.dp(act, 48));
        custom.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
        custom.addView(Ui.smallIcon(act, R.drawable.ic_s2_edit, Ui.ACCENT, 20));
        TextView ct = Ui.text(act, Tr.t("uim_size_custom", "Custom..."), 14, Ui.ACCENT);
        ct.setPadding(Ui.dp(act, 10), 0, 0, 0);
        custom.addView(ct);
        custom.setOnClickListener(v -> {
            if (dlg[0] != null) dlg[0].dismiss();
            askCustomSize();
        });
        col.addView(custom, Ui.matchWrap());
        LinearLayout box = Ui.column(act);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 380)));
        dlg[0] = new AlertDialog.Builder(act).setTitle(Tr.t("uim_device", "Screen size")).setView(box)
                .setNegativeButton(Tr.t("close", "Close"), null).create();
        dlg[0].show();
    }

    private static String checkSizeText(String v) {
        Matcher m = SIZE_TEXT.matcher(v == null ? "" : v);
        if (!m.matches()) return Tr.t("uim_size_bad", "Write it like 1280x720");
        int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
        if (w < UiPreviewView.MIN_SIZE || h < UiPreviewView.MIN_SIZE || w > UiPreviewView.MAX_SIZE || h > UiPreviewView.MAX_SIZE)
            return String.format(Tr.t("uim_size_range", "Each side must be between %1$d and %2$d"), UiPreviewView.MIN_SIZE, UiPreviewView.MAX_SIZE);
        return null;
    }

    private void askCustomSize() {
        Dialogs.input(act, Tr.t("uim_size_custom_title", "Custom size"), Tr.t("uim_size_custom_msg", "Width x height in logical pixels, e.g. 1280x720"), curW + "x" + curH,
                UiMakerDialog::checkSizeText, value -> {
                    Matcher m = SIZE_TEXT.matcher(value);
                    if (m.matches()) setSize(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                });
    }

    private void updateZoomText() {
        zoomText.setText(Math.round(preview.zoom() * 100) + "%");
    }

    /** Chips that show the size (cheap: used while the phone frame is dragged). */
    private void updateSizeChips() {
        orientChip.setText(landscape() ? Tr.t("uim_landscape", "Landscape") : Tr.t("uim_portrait", "Portrait"));
        deviceChip.setText(lw() + "x" + lh());
    }

    private void updateBars() {
        String n = name + ".ui" + (hist.dirty() || historyPending ? " *" : "");
        title.setText(n);
        undoBtn.setAlpha(hist.canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(hist.canRedo() ? 1f : 0.35f);
        modeChip.setText(testMode ? Tr.t("uim_mode_test", "Test") + " ▶" : Tr.t("uim_mode_edit", "Edit") + " ✎");
        modeChip.setTextColor(testMode ? Ui.SUCCESS : Ui.TEXT);
        updateSizeChips();
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
            UiRects.R hit = r == null ? null : r.hit(px / k, py / k, true); // locked elements still work in test mode
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

    @Override
    public void deviceResizeStart() {
        flushHistory();
    }

    @Override
    public void deviceResized(int w, int h) {
        curW = clampSize(w);
        curH = clampSize(h);
        try {
            // layout of the new size right away, so the selection outline follows while the real picture is being drawn
            preview.setRects(UiRects.compute(doc, curW, curH, UILayout.APPROX));
        } catch (Throwable ignored) {
        }
        render(false, 0f);
        updateSizeChips();
    }

    @Override
    public void deviceResizeEnd() {
        applyDevice();
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
    public void structureChanged() {
        flushHistory();
        structural();
    }

    @Override
    public UiRects.R rectOf(String id) {
        UiRects r = preview == null ? null : preview.rects();
        return r == null ? null : r.get(id);
    }

    // ------------------------------------------------------------------ save and menus

    /** {@code UI/<n>.ui}; {@code n} may contain folders ("menus/pause"). */
    private File fileFor(String n) {
        return new File(UiMakerLauncher.uiDir(project), n + ".ui");
    }

    private boolean save() {
        flushHistory();
        try {
            File f = fileFor(name);
            File dir = f.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException(Tr.t("ui_folder_failed", "Could not create the folder"));
            doc.name = name;
            doc.validate();
            doc.write(f);
            hist.markSaved();
            fileExists = true;
            updateBars();
            flashSaved();
            Ui.toast(act, String.format(Tr.t("uim_saved", "Saved %s.ui"), name));
            if (onSaved != null) onSaved.run();
            return true;
        } catch (Exception e) {
            Ui.toast(act, Tr.t("uim_save_failed", "Could not save: ") + e.getMessage());
            return false;
        }
    }

    private void saveAs() {
        flushHistory();
        UiFolders.newUiDialog(act, project, Tr.t("uim_save_as", "Save as"), UiFolders.baseOf(name), UiFolders.folderOf(name), full -> {
            if (full.equals(name)) {
                save();
                return;
            }
            if (fileFor(full).exists()) {
                Dialogs.confirm(act, Tr.t("uim_overwrite", "Replace?"), String.format(Tr.t("uim_overwrite_msg", "%s.ui already exists. Replace it?"), full), Tr.t("uim_replace", "Replace"), () -> {
                    name = full;
                    save();
                });
            } else {
                name = full;
                save();
            }
        });
    }

    /** "Open another UI...": pick a file (folder by folder), ask about unsaved changes, then load it into this editor. */
    private void openOther() {
        UiFolders.pickUi(act, project, name, picked -> {
            if (picked.equals(name)) {
                Ui.toast(act, Tr.t("uim_already_open", "This UI is already open"));
                return;
            }
            confirmUnsaved(() -> loadDoc(picked));
        });
    }

    private void loadDoc(String newName) {
        File f = fileFor(newName);
        if (!f.isFile()) {
            Ui.toast(act, Tr.t("uim_not_found", "File not found: ") + newName + ".ui");
            return;
        }
        UIDocument d = UIDocument.read(f);
        main.removeCallbacks(historyFlush);
        historyPending = false;
        if (testMode) {
            testMode = false;
            main.removeCallbacks(testTick);
        }
        doc = d;
        name = newName;
        doc.name = name;
        fileExists = true;
        hist = new UiHistory(doc);
        selected = null;
        lastFrameAt = 0;
        sizeFromDoc();
        if (d.warnings.size() > 0) Ui.toast(act, d.warnings.get(0));
        applyDevice();
        updateBars();
        showTab(doc.root.children.isEmpty() ? 0 : 1);
    }

    private void moreMenu() {
        final String[] items = {Tr.t("uim_save_as", "Save as"), Tr.t("uim_open_other", "Open another UI..."), Tr.t("uim_use_template", "Insert a ready-made screen"),
                Tr.t("uim_json", "Show / copy JSON"), Tr.t("uim_clear", "Clear everything"), Tr.t("uim_warnings", "Layout warnings")};
        Dialogs.choose(act, Tr.t("more", "More"), items, i -> {
            switch (i) {
                case 0: saveAs(); break;
                case 1: openOther(); break;
                case 2: askTemplate(false); break;
                case 3: Dialogs.longText(act, name + ".ui", doc.toJsonString()); break;
                case 4:
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
            labels.add(UiTr.templateName(t));
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
        List<String> w = UiLint.checkBoth(doc, a, b, UILayout.APPROX, Tr::t);
        if (w.isEmpty()) {
            Ui.toast(act, Tr.t("uim_no_warnings", "No layout problems found (text sizes are approximate)"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : w) sb.append("• ").append(s).append("\n\n");
        Dialogs.longText(act, Tr.t("uim_warnings", "Layout warnings") + " (" + w.size() + ")", sb.toString().trim());
    }
}
