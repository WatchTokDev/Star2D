package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.star4droid.star2d.buttons.ButtonColors;
import com.star4droid.star2d.buttons.ButtonPresets;
import com.star4droid.star2d.buttons.ButtonRenderer;
import com.star4droid.star2d.buttons.ButtonSpec;
import com.star4droid.star2d.buttons.ButtonStore;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.ColorPickerDialog;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Button Maker: a live preview (checkerboard, with sliders to see how the 9-patch stretches), preset / shape chips,
 * colour, outline, shadow, 3D lip, glow and shine controls, and a bottom bar with the name and Save (Replace when the name exists).
 * Saves images/&lt;name&gt;.9.png (+ &lt;name&gt;_pressed.9.png); fixed shapes (circle, blob, triangles) are saved as plain PNG.
 */
public class ButtonMakerPanel implements PanelHost.Panel {

    public interface OnSaved {
        void on(String useAs);
        /** also tells the pressed twin ("/name_pressed.9.png") when one was made, else null (callers that want it override this) */
        default void on(String useAs, String pressedUseAs) { on(useAs); }
    }

    private final EditorShell shell;
    private final File imagesDir;
    private final OnSaved onSaved;
    private final ButtonSpec initial;
    private final String startName;

    private ButtonSpec spec;
    private Integer baseColor;
    private boolean pressedPreview = false, makePressed = true;
    private float pw = 1f, ph = 1f;

    private Context ctx;
    private View root;
    private PreviewView preview;
    private EditText nameEdit;
    private TextView saveLabel, existsHint, normalChip, pressedChip;
    private final ArrayList<Runnable> syncers = new ArrayList<>();
    private boolean syncing = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable renderNow = this::render;

    /** @param initial settings to start from (null = the default look); @param name image name to prefill (null = empty). */
    public ButtonMakerPanel(EditorShell shell, File imagesDir, ButtonSpec initial, String name, OnSaved onSaved) {
        this.shell = shell;
        this.imagesDir = imagesDir;
        this.onSaved = onSaved;
        this.initial = initial != null ? initial.copy() : ButtonPresets.make(ButtonPresets.DEFAULT, null, null, null);
        this.spec = this.initial.copy();
        this.startName = name == null ? "" : name;
    }

    @Override public String title() { return t("bm_title", "Button Maker"); }
    @Override public int icon() { return R.drawable.ic_s2_palette; }

    @Override
    public View[] headerActions(Context c) {
        ImageView reset = Ui.iconButton(c, R.drawable.ic_s2_refresh, t("bm_reset", "Reset"), v -> {
            spec = initial.copy();
            baseColor = null;
            syncAll();
            changed();
        });
        return new View[]{reset};
    }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        ctx = c;
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), 0);

        // ---- preview (fixed at the top): the Normal / Pressed captions live in their own row ABOVE the picture,
        // so they never cover the button, its shadow, bevel or the 9-patch stretch area
        LinearLayout toggles = Ui.row(c);
        toggles.setGravity(Gravity.CENTER_VERTICAL);
        toggles.setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), Ui.dp(c, 4));
        normalChip = chip(c, t("bm_normal", "Normal"), true, v -> setPressedPreview(false));
        pressedChip = chip(c, t("bm_pressed", "Pressed"), false, v -> setPressedPreview(true));
        toggles.addView(normalChip);
        toggles.addView(pressedChip);
        col.addView(toggles, Ui.matchWrap());

        FrameLayout card = new FrameLayout(c);
        card.setBackground(Ui.rounded(0xFF101214, Ui.dp(c, 10), Ui.BORDER, 1));
        preview = new PreviewView(c);
        card.addView(preview, Ui.match());
        col.addView(card, new LinearLayout.LayoutParams(-1, Ui.dp(c, Ui.isLandscape(c) ? 104 : 150)));

        // preview size sliders (do not change the saved image: they show how the 9-patch stretches)
        LinearLayout sizes = Ui.row(c);
        sizes.setPadding(0, Ui.dp(c, 4), 0, 0);
        sizes.addView(Ui.text(c, "↔", 14, Ui.TEXT_2));
        SeekBar sw = previewSeek(c, true);
        sizes.addView(sw, Ui.weight(1));
        sizes.addView(Ui.text(c, "↕", 14, Ui.TEXT_2));
        SeekBar sh = previewSeek(c, false);
        sizes.addView(sh, Ui.weight(1));
        col.addView(sizes, Ui.matchWrap());

        // ---- controls
        LinearLayout body = Ui.column(c);
        body.setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), Ui.dp(c, 12));
        buildControls(c, body);
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(false);
        scroll.addView(body);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // ---- bottom bar: name + pressed toggle + Save / Replace
        col.addView(Ui.divider(c));
        LinearLayout bar = Ui.row(c);
        bar.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        nameEdit = Ui.input(c, startName, t("bm_name", "Name (e.g. btn_play)"));
        nameEdit.setFilters(new InputFilter[]{nameFilter(), new InputFilter.LengthFilter(48)});
        bar.addView(nameEdit, Ui.weight(1));
        CheckBox pr = new CheckBox(c);
        pr.setText(t("bm_pressed_version", "Pressed version"));
        pr.setTextColor(Ui.TEXT_2);
        pr.setTextSize(12);
        pr.setChecked(true);
        pr.setOnCheckedChangeListener((b, on) -> makePressed = on);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.setMarginStart(Ui.dp(c, 6));
        bar.addView(pr, pl);
        LinearLayout save = Ui.button(c, R.drawable.ic_s2_save, t("bm_save", "Save"), true, v -> save());
        saveLabel = (TextView) save.getChildAt(save.getChildCount() - 1);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.setMarginStart(Ui.dp(c, 6));
        bar.addView(save, sl);
        col.addView(bar, Ui.matchWrap());
        existsHint = Ui.text(c, t("bm_exists", "A button with this name exists: Replace overwrites it (and its pressed version)."), 11.5f, Ui.WARNING);
        existsHint.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 6));
        col.addView(existsHint);
        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { refreshNameState(); }
        });

        root = col;
        refreshNameState();
        changed();
        return root;
    }

    @Override public void onShown() { refreshNameState(); changed(); }

    @Override
    public void onHidden() {
        handler.removeCallbacks(renderNow);
    }

    // ------------------------------------------------------------------------------------------ controls

    private void buildControls(Context c, LinearLayout body) {
        // presets
        body.addView(Ui.sectionLabel(c, t("bm_preset", "Preset")));
        ArrayList<String> ids = new ArrayList<>(ButtonPresets.ids());
        body.addView(chipRow(c, ids.toArray(new String[0]), ids.toArray(new String[0]), () -> spec.preset, id -> {
            ButtonSpec s = ButtonPresets.make(id, baseColor == null ? null : ButtonColors.toString(baseColor), null, null);
            spec = s;
            syncAll();
            changed();
        }));

        // shape
        body.addView(Ui.sectionLabel(c, t("bm_shape", "Shape")));
        body.addView(chipRow(c, ButtonSpec.SHAPES, ButtonSpec.SHAPES, () -> spec.shape, sh -> {
            ButtonPresets.changeShape(spec, sh);
            syncAll();
            changed();
        }));

        // colour
        body.addView(Ui.sectionLabel(c, t("bm_color", "Color")));
        body.addView(paletteRow(c));
        body.addView(fillRow(c));
        slider(c, body, t("bm_angle", "Gradient angle"), 0, 360, true, () -> spec.angle, v -> spec.angle = v);

        // geometry
        body.addView(Ui.sectionLabel(c, t("bm_size", "Size")));
        slider(c, body, t("bm_width", "Width"), 32, 320, true, () -> (float) spec.width, v -> spec.width = Math.round(v));
        slider(c, body, t("bm_height", "Height"), 24, 256, true, () -> (float) spec.height, v -> spec.height = Math.round(v));
        slider(c, body, t("bm_corner", "Corner size"), 0, 0.5f, false,
                () -> spec.shape.equals("chamfer") ? spec.chamferRatio : spec.radiusRatio,
                v -> {
                    if (spec.shape.equals("chamfer")) {
                        spec.chamferRatio = v;
                        spec.chamfer = -1;
                    } else {
                        spec.radiusRatio = v;
                        spec.radius = -1;
                    }
                });

        // rings
        body.addView(Ui.sectionLabel(c, t("bm_outline", "Outline")));
        colorRow(c, body, t("bm_outline_c", "Outline color"), () -> spec.outlineColor, v -> spec.outlineColor = v, true);
        slider(c, body, t("bm_outline_w", "Outline width"), 0, 12, true, () -> spec.outlineWidth, v -> spec.outlineWidth = v);
        colorRow(c, body, t("bm_frame_c", "Frame color"), () -> spec.frameColor, v -> spec.frameColor = v, true);
        slider(c, body, t("bm_frame_w", "Frame width"), 0, 12, true, () -> spec.frameWidth, v -> spec.frameWidth = v);
        colorRow(c, body, t("bm_border_c", "Inner border color"), () -> spec.borderColor, v -> spec.borderColor = v, true);
        slider(c, body, t("bm_border_w", "Inner border width"), 0, 12, true, () -> spec.borderWidth, v -> spec.borderWidth = v);

        // shadow, lip, glow
        body.addView(Ui.sectionLabel(c, t("bm_shadow", "Shadow, 3D edge, glow")));
        colorRow(c, body, t("bm_shadow_c", "Shadow color"), () -> spec.shadowColor, v -> spec.shadowColor = v, true);
        slider(c, body, t("bm_shadow_dy", "Shadow offset"), -8, 16, true, () -> spec.shadowDy, v -> spec.shadowDy = v);
        slider(c, body, t("bm_shadow_dx", "Shadow side offset"), -8, 8, true, () -> spec.shadowDx, v -> spec.shadowDx = v);
        slider(c, body, t("bm_shadow_blur", "Shadow blur"), 0, 20, true, () -> spec.shadowBlur, v -> spec.shadowBlur = v);
        slider(c, body, t("bm_lip", "3D bottom edge"), 0, 16, true, () -> spec.lip, v -> spec.lip = v);
        colorRow(c, body, t("bm_lip_c", "3D edge color (none = auto)"), () -> spec.lipColor, v -> spec.lipColor = v, true);
        colorRow(c, body, t("bm_glow_c", "Glow color"), () -> spec.glowColor, v -> spec.glowColor = v, true);
        slider(c, body, t("bm_glow", "Glow size"), 0, 24, true, () -> spec.glowBlur, v -> spec.glowBlur = v);

        // gloss
        body.addView(Ui.sectionLabel(c, t("bm_gloss", "Shine")));
        body.addView(chipRow(c, ButtonSpec.GLOSS, ButtonSpec.GLOSS, () -> spec.gloss, g -> {
            spec.gloss = g;
            if (!g.equals("none") && spec.glossStrength < 0.05f) spec.glossStrength = 0.4f;
            syncAll();
            changed();
        }));
        slider(c, body, t("bm_gloss_s", "Shine strength"), 0, 1, false, () -> spec.glossStrength, v -> spec.glossStrength = v);
        slider(c, body, t("bm_gloss_h", "Shine height"), 0.05f, 1, false, () -> spec.glossHeight, v -> spec.glossHeight = v);
        slider(c, body, t("bm_gloss_y", "Shine position"), 0, 0.9f, false, () -> spec.glossY, v -> spec.glossY = v);
        colorRow(c, body, t("bm_gloss_c", "Shine color"), () -> spec.glossColor, v -> spec.glossColor = v, false);

        // light
        body.addView(Ui.sectionLabel(c, t("bm_light", "Inner light")));
        colorRow(c, body, t("bm_inner_c", "Inner glow color"), () -> spec.innerGlowColor, v -> spec.innerGlowColor = v, true);
        slider(c, body, t("bm_inner_w", "Inner glow width"), 0, 16, true, () -> spec.innerGlowWidth, v -> spec.innerGlowWidth = v);
        slider(c, body, t("bm_bevel", "Bevel"), 0, 4, true, () -> spec.bevel, v -> spec.bevel = v);
    }

    /** A named-colour palette: tapping re-colours the current preset (all hue-derived colours), the last circle opens the picker. */
    private View paletteRow(Context c) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 6));
        for (java.util.Map.Entry<String, Integer> e : ButtonColors.NAMED.entrySet()) {
            View v = swatch(c, e.getValue(), 30);
            v.setOnClickListener(x -> recolor(e.getValue()));
            row.addView(v);
        }
        TextView custom = Ui.text(c, "+", 16, Ui.TEXT);
        custom.setGravity(Gravity.CENTER);
        custom.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 15), Ui.BORDER, 1));
        custom.setOnClickListener(x -> ColorPickerDialog.show(c, t("bm_color", "Color"), ColorPickerDialog.toHex(baseColor != null ? baseColor : ButtonPresets.defaultColor(spec.preset)), null, hex -> {
            if (hex != null) recolor(ColorPickerDialog.parse(hex) | 0xFF000000);
        }));
        row.addView(custom, new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30)));
        hs.addView(row);
        return hs;
    }

    private void recolor(int base) {
        baseColor = base;
        ButtonSpec f = ButtonPresets.makeWithColor(spec.preset, base);
        spec.fill = f.fill;
        spec.outlineColor = f.outlineColor;
        spec.frameColor = f.frameColor;
        spec.borderColor = f.borderColor;
        spec.lipColor = f.lipColor;
        spec.glowColor = f.glowColor;
        spec.innerGlowColor = f.innerGlowColor;
        syncAll();
        changed();
    }

    /** 1-3 fill colour swatches, plus and minus. */
    private View fillRow(Context c) {
        LinearLayout box = Ui.row(c);
        box.setPadding(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 4));
        Runnable rebuild = () -> {
            box.removeAllViews();
            TextView l = Ui.text(c, t("bm_fill", "Fill gradient"), 12.5f, Ui.TEXT_2);
            box.addView(l, Ui.weight(1));
            for (int i = 0; i < spec.fill.length; i++) {
                final int idx = i;
                View sw = swatch(c, spec.fill[i], 40);
                sw.setOnClickListener(x -> {
                    final int orig = spec.fill[idx];
                    ColorPickerDialog.show(c, t("bm_fill", "Fill gradient"), ColorPickerDialog.toHex(orig),
                            hex -> {
                                spec.fill[idx] = ColorPickerDialog.parse(hex);
                                sw.setBackground(Ui.rounded(spec.fill[idx], Ui.dp(c, 7), Ui.BORDER, 1));
                                changed();
                            },
                            done -> {
                                if (done == null) spec.fill[idx] = orig;
                                else spec.fill[idx] = ColorPickerDialog.parse(done);
                                syncAll();
                                changed();
                            });
                });
                box.addView(sw);
            }
            if (spec.fill.length < 3) box.addView(smallBtn(c, "+", v -> {
                int[] f = java.util.Arrays.copyOf(spec.fill, spec.fill.length + 1);
                f[f.length - 1] = ButtonColors.darken(f[f.length - 2], 0.12f);
                spec.fill = f;
                syncAll();
                changed();
            }));
            if (spec.fill.length > 1) box.addView(smallBtn(c, "−", v -> {
                spec.fill = java.util.Arrays.copyOf(spec.fill, spec.fill.length - 1);
                syncAll();
                changed();
            }));
        };
        rebuild.run();
        syncers.add(rebuild);
        return box;
    }

    private void colorRow(Context c, LinearLayout parent, String label, IntSupplier get, IntConsumer set, boolean optional) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 2), Ui.dp(c, 3), Ui.dp(c, 2), Ui.dp(c, 3));
        row.addView(Ui.text(c, label, 12.5f, Ui.TEXT_2), Ui.weight(1));
        TextView sw = new TextView(c);
        sw.setGravity(Gravity.CENTER);
        sw.setTextColor(Ui.TEXT_3);
        sw.setTextSize(11);
        Runnable paint = () -> {
            int col = get.getAsInt();
            sw.setText(col == 0 ? "—" : "");
            sw.setBackground(Ui.rounded(col == 0 ? 0xFF121417 : col, Ui.dp(c, 7), Ui.BORDER, 1));
        };
        paint.run();
        sw.setOnClickListener(v -> {
            final int orig = get.getAsInt();
            ColorPickerDialog.show(c, label, ColorPickerDialog.toHex(orig == 0 ? 0xFFFFFFFF : orig),
                    hex -> {
                        set.accept(ColorPickerDialog.parse(hex));
                        paint.run();
                        changed();
                    },
                    done -> {
                        set.accept(done == null ? orig : ColorPickerDialog.parse(done));
                        paint.run();
                        changed();
                    });
        });
        row.addView(sw, new LinearLayout.LayoutParams(Ui.dp(c, 46), Ui.dp(c, 28)));
        if (optional) {
            TextView off = smallBtn(c, "×", v -> {
                set.accept(0);
                paint.run();
                changed();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28));
            lp.setMarginStart(Ui.dp(c, 6));
            row.addView(off, lp);
        }
        parent.addView(row, Ui.matchWrap());
        syncers.add(paint);
    }

    private void slider(Context c, LinearLayout parent, String label, float min, float max, boolean isInt, Supplier<Float> get, Consumer<Float> set) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), 0);
        TextView l = Ui.text(c, label, 12.5f, Ui.TEXT_2);
        l.setMaxLines(2);
        row.addView(l, new LinearLayout.LayoutParams(Ui.dp(c, 112), -2));
        SeekBar sb = new SeekBar(c);
        sb.setMax(1000);
        row.addView(sb, Ui.weight(1));
        TextView val = Ui.text(c, "", 12, Ui.TEXT);
        val.setGravity(Gravity.END);
        row.addView(val, new LinearLayout.LayoutParams(Ui.dp(c, 40), -2));
        Runnable sync = () -> {
            float v = get.get();
            syncing = true;
            sb.setProgress(Math.round((Math.max(min, Math.min(max, v)) - min) / (max - min) * 1000f));
            syncing = false;
            val.setText(isInt ? String.valueOf(Math.round(v)) : String.format(Locale.US, "%.2f", v));
        };
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (syncing || !fromUser) return;
                float v = min + p / 1000f * (max - min);
                if (isInt) v = Math.round(v);
                set.accept(v);
                val.setText(isInt ? String.valueOf(Math.round(v)) : String.format(Locale.US, "%.2f", v));
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        sync.run();
        syncers.add(sync);
        parent.addView(row, Ui.matchWrap());
    }

    private SeekBar previewSeek(Context c, boolean horizontal) {
        SeekBar sb = new SeekBar(c);
        sb.setMax(100);
        sb.setProgress(Math.round((1f - 0.5f) / 2.5f * 100f)); // 1.0 on a 0.5..3.0 scale
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float v = 0.5f + p / 100f * 2.5f;
                if (horizontal) pw = v;
                else ph = v;
                if (preview != null) preview.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }

    private LinearLayout chipRow(Context c, String[] labels, String[] ids, Supplier<String> current, Consumer<String> pick) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 2), 0, Ui.dp(c, 2), Ui.dp(c, 2));
        final ArrayList<TextView> chips = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            TextView ch = chip(c, labels[i].replace('_', ' '), false, v -> pick.accept(id));
            chips.add(ch);
            row.addView(ch);
        }
        hs.addView(row);
        syncers.add(() -> {
            for (int i = 0; i < ids.length; i++) styleChip(c, chips.get(i), ids[i].equals(current.get()));
        });
        syncers.get(syncers.size() - 1).run();
        LinearLayout wrap = Ui.column(c);
        wrap.addView(hs);
        return wrap;
    }

    // ------------------------------------------------------------------------------------------ small widgets

    private TextView chip(Context c, String text, boolean selected, View.OnClickListener l) {
        TextView t = Ui.text(c, text, 12.5f, Ui.TEXT);
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        t.setMaxLines(1);
        styleChip(c, t, selected);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(Ui.dp(c, 6));
        t.setLayoutParams(lp);
        return t;
    }

    private void styleChip(Context c, TextView t, boolean selected) {
        t.setBackground(Ui.rounded(selected ? Ui.ACCENT : Ui.SURFACE_3, Ui.dp(c, 15)));
        t.setTextColor(selected ? 0xFFFFFFFF : Ui.TEXT_2);
    }

    private View swatch(Context c, int color, int sizeDp) {
        View v = new View(c);
        v.setBackground(Ui.rounded(color, Ui.dp(c, sizeDp >= 36 ? 7 : sizeDp / 2f), Ui.BORDER, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp >= 36 ? 28 : sizeDp));
        lp.setMarginStart(Ui.dp(c, 6));
        v.setLayoutParams(lp);
        return v;
    }

    private TextView smallBtn(Context c, String text, View.OnClickListener l) {
        TextView t = Ui.text(c, text, 16, Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 7), Ui.BORDER, 1));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28));
        lp.setMarginStart(Ui.dp(c, 6));
        t.setLayoutParams(lp);
        return t;
    }

    private static InputFilter nameFilter() {
        return (src, start, end, dst, ds, de) -> {
            StringBuilder sb = null;
            for (int i = start; i < end; i++) {
                char ch = src.charAt(i);
                boolean ok = ch == '_' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
                if (!ok && sb == null) {
                    sb = new StringBuilder();
                    sb.append(src, start, i);
                }
                if (ok && sb != null) sb.append(ch);
            }
            return sb;
        };
    }

    private String t(String key, String def) {
        return Tr.t(key, def);
    }

    // ------------------------------------------------------------------------------------------ state

    private void syncAll() {
        for (Runnable r : new ArrayList<>(syncers)) r.run();
    }

    private void setPressedPreview(boolean on) {
        pressedPreview = on;
        styleChip(ctx, normalChip, !on);
        styleChip(ctx, pressedChip, on);
        changed();
    }

    private void changed() {
        handler.removeCallbacks(renderNow);
        handler.postDelayed(renderNow, 25);
    }

    private void render() {
        if (preview == null) return;
        try {
            ButtonSpec s = pressedPreview ? spec.pressedVariant() : spec.copy();
            ButtonRenderer.Result r = ButtonRenderer.render(s);
            preview.set(r);
        } catch (Throwable e) {
            Ui.toast(ctx, String.valueOf(e));
        }
    }

    private void refreshNameState() {
        if (nameEdit == null) return;
        String n = nameEdit.getText().toString().trim();
        boolean exists = !n.isEmpty() && ButtonStore.existing(imagesDir, n) != null;
        saveLabel.setText(exists ? t("bm_replace", "Replace") : t("bm_save", "Save"));
        existsHint.setVisibility(exists ? View.VISIBLE : View.GONE);
    }

    private void save() {
        final String name = nameEdit.getText().toString().trim();
        String err = ButtonStore.checkName(name);
        if (err != null) {
            nameEdit.setError(err);
            return;
        }
        final ButtonSpec s = spec.copy();
        final boolean pressed = makePressed;
        io.execute(() -> {
            try {
                ButtonStore.Saved sv = ButtonStore.save(imagesDir, name, s, pressed, true);
                try {
                    if (Bridge.ready()) Bridge.gl(() -> Bridge.app().applyExternalChanges(Bridge.app().openSceneFingerprints()));
                } catch (Throwable ignored) {
                }
                Ui.ui(() -> {
                    String msg = (sv.replaced ? t("bm_replaced", "Replaced") : t("bm_saved", "Saved")) + ": " + sv.useAs
                            + (sv.pressedUseAs != null ? "  +  " + sv.pressedUseAs : "");
                    if (!sv.stretchable) msg += "  (" + t("bm_fixed_shape", "fixed shape: keep the aspect ratio") + ")";
                    Ui.toast(ctx, msg);
                    refreshNameState();
                    try {
                        shell.files.refreshList();
                    } catch (Throwable ignored) {
                    }
                    if (onSaved != null) onSaved.on(sv.useAs, sv.pressedUseAs);
                });
            } catch (Throwable e) {
                Ui.toast(ctx, e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------------------------------ preview view

    /** Checkerboard + the rendered button drawn as a 9-patch at the size set by the two preview sliders. */
    private final class PreviewView extends View {
        private ButtonRenderer.Result res;
        private final Paint checkA = new Paint(), checkB = new Paint(), bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        PreviewView(Context c) {
            super(c);
            checkA.setColor(0xFF2A2D33);
            checkB.setColor(0xFF343840);
        }

        void set(ButtonRenderer.Result r) {
            Bitmap old = res == null ? null : res.bitmap;
            res = r;
            invalidate();
            if (old != null && old != r.bitmap) old.recycle();
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float tile = Ui.dp(getContext(), 10);
            for (int y = 0; y * tile < h; y++)
                for (int x = 0; x * tile < w; x++)
                    cv.drawRect(x * tile, y * tile, (x + 1) * tile, (y + 1) * tile, ((x + y) & 1) == 0 ? checkA : checkB);
            if (res == null || res.bitmap.isRecycled()) return;
            Bitmap b = res.bitmap;
            int bw = b.getWidth(), bh = b.getHeight();
            float sx = pw, sy = res.stretchable ? ph : pw;
            float totalW = bw * sx, totalH = bh * sy;
            if (res.stretchable) {
                totalW = Math.max(totalW, res.minW + 2);
                totalH = Math.max(totalH, res.minH + 2);
            }
            float k = Math.min((w - Ui.dp(getContext(), 32)) / totalW, (h - Ui.dp(getContext(), 32)) / totalH);
            k = Math.min(k, getResources().getDisplayMetrics().density * 2f);
            if (k <= 0) return;
            float dw = totalW * k, dh = totalH * k;
            float left = (w - dw) / 2f, top = (h - dh) / 2f;
            if (!res.stretchable) {
                cv.drawBitmap(b, null, new RectF(left, top, left + dw, top + dh), bitmapPaint);
                return;
            }
            float cl = res.sx0 * k, cr = (bw - res.sx1) * k, ct = res.sy0 * k, cb = (bh - res.sy1) * k;
            float[] xs = {left, left + cl, left + dw - cr, left + dw};
            float[] ys = {top, top + ct, top + dh - cb, top + dh};
            int[] sxs = {0, res.sx0, res.sx1, bw}, sys = {0, res.sy0, res.sy1, bh};
            for (int j = 0; j < 3; j++)
                for (int i = 0; i < 3; i++)
                    cv.drawBitmap(b, new Rect(sxs[i], sys[j], sxs[i + 1], sys[j + 1]), new RectF(xs[i], ys[j], xs[i + 1], ys[j + 1]), bitmapPaint);
        }
    }
}
