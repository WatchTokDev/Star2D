package com.star4droid.star2d.editor.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter;
import com.star4droid.star2d.editor.ui.SingleInputDialog;
import com.star4droid.star2d.editor.ui.sub.inputs.NumberInputDialog;
import com.star4droid.star2d.fonts.FontPresets;
import com.star4droid.star2d.ui.Tr;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The screen of the bitmap font maker (.s2df): header (cancel / title / save), a big live preview, a strip of ready presets and the
 * settings in cards: font file, size and spacing, fill, outline, shadow and an "advanced" card with the remaining FreeType options.
 *
 * The form keeps the font in plain strings ({@link #params()}, {@link #name()}, {@link #ttf()}, {@link #rtl()}) exactly as they are stored
 * in the .s2df file; every control is only a view of that model, so the UI can be rebuilt (rotation) without losing anything.
 * It knows nothing about files or the editor: {@link BitmapFontEditor} wraps it in a full-screen dialog and does the saving.
 * Portrait: preview and presets on top, settings scroll below. Landscape: preview + presets on the left, settings on the right.
 * GL thread only.
 */
public class FontMakerForm extends Table {

    /** What the form needs from the editor. */
    public interface Env {
        boolean isRtl();
        void toast(String message);
        /** Shared colour picker window (added to the stage when used), or null. */
        ColorPicker colorPicker();
        /** Opens the project's file picker for ttf / otf files; calls back with the picked path relative to root. */
        void pickTtf(String root, Consumer<String> onPick);
        /** Shows the help text of one option (tap on its name). */
        void details(String key, String fallback);
    }

    // ----------------------------------------------------------------- the option list (reflection, like the engine does)

    /** One FreeTypeFontParameter option. kind: float, int, enum, color, bool. */
    static final class Fld {
        String key, kind, def;
        String[] options;
        FontPresets.Spec spec;
    }

    private static List<Fld> FIELDS;

    static synchronized List<Fld> fields() {
        if (FIELDS != null) return FIELDS;
        ArrayList<Fld> out = new ArrayList<>();
        Class<?> cls = FreeTypeFontGenerator.FreeTypeFontParameter.class;
        FreeTypeFontGenerator.FreeTypeFontParameter instance = new FreeTypeFontGenerator.FreeTypeFontParameter();
        for (Field field : cls.getFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                Object dv = null;
                boolean isEnum = false;
                try {
                    dv = field.get(instance);
                    isEnum = dv != null && dv.getClass().isEnum();
                } catch (Exception ignored) {
                }
                String type = field.getType().getName().toLowerCase();
                Fld f = new Fld();
                f.key = field.getName();
                if (type.contains("float")) f.kind = "float";
                else if (type.equals("int") || type.contains("integer")) f.kind = "int";
                else if (isEnum) {
                    f.kind = "enum";
                    Field[] consts = dv.getClass().getFields();
                    f.options = new String[consts.length];
                    for (int i = 0; i < consts.length; i++) f.options[i] = consts[i].getName();
                } else if (type.contains("color")) f.kind = "color";
                else if (type.equals("boolean")) f.kind = "bool";
                else continue; // strings, packer...: not editable (same as the old dialog)
                f.def = dv == null ? null : (f.kind.equals("color") ? ((Color) dv).toString().toUpperCase() : dv.toString());
                if (f.def == null) continue;
                f.spec = FontPresets.spec(f.key);
                out.add(f);
            } catch (Throwable t) {
                // an unknown field type is simply not editable
            }
        }
        FIELDS = out;
        return out;
    }

    private static final String[] G_SIZE = {"size", "spaceX", "spaceY", "kerning"};
    private static final String[] G_FILL = {"color", "gamma"};
    private static final String[] G_OUTLINE = {"borderWidth", "borderColor", "borderStraight", "borderGamma"};
    private static final String[] G_SHADOW = {"shadowOffsetX", "shadowOffsetY", "shadowColor"};

    private static final String[][] LABELS = {
            {"size", "Size (px)"}, {"spaceX", "Letter spacing"}, {"spaceY", "Line spacing"}, {"kerning", "Kerning"},
            {"color", "Color"}, {"gamma", "Smoothness"},
            {"borderWidth", "Width"}, {"borderColor", "Color"}, {"borderStraight", "Sharp corners"}, {"borderGamma", "Smoothness"},
            {"shadowOffsetX", "Offset X"}, {"shadowOffsetY", "Offset Y"}, {"shadowColor", "Color"},
            {"hinting", "Hinting"}, {"mono", "Pixel look (no anti-aliasing)"}, {"renderCount", "Render passes"},
            {"minFilter", "Shrink filter"}, {"magFilter", "Enlarge filter"},
            {"padTop", "Padding top"}, {"padLeft", "Padding left"}, {"padBottom", "Padding bottom"}, {"padRight", "Padding right"},
            {"flip", "Flip vertically"}, {"genMipMaps", "Generate mipmaps"}, {"incremental", "Incremental glyphs"}};

    private static String labelOf(String key) {
        for (String[] l : LABELS) if (l[0].equals(key)) return Tr.t("font_p_" + key, l[1]);
        return Tr.t(key);
    }

    /** Display name of an enum value (hinting, texture filters): the stored value stays the raw token. */
    private static String optionLabel(String v) {
        if (v == null || v.isEmpty()) return "";
        return Tr.t("font_o_" + v, v);
    }

    private static boolean in(String[] arr, String k) {
        for (String s : arr) if (s.equals(k)) return true;
        return false;
    }

    // ----------------------------------------------------------------- model

    private final Env env;
    private String name = "", ttf = "", root = "";
    private boolean rtlFont, nameLocked;
    private final LinkedHashMap<String, String> p = new LinkedHashMap<>();
    private String activePreset;
    private boolean advancedOpen;
    private static int previewBg = 0;
    private static final Color[] PREVIEW_BGS = {Color.valueOf("0F1114FF"), Color.valueOf("6B7280FF"), Color.valueOf("F1F3F5FF")};

    public Runnable onSave, onCancel;

    public FontMakerForm(Env env) {
        this.env = env;
        setTouchable(Touchable.enabled);
    }

    /**
     * @param saved the values of an existing .s2df (name / font / rtl / options as strings) or null for a new font
     * @param root absolute folder of the .s2df (the ttf path is relative to it)
     */
    public void load(String name, boolean nameLocked, String root, Map<String, ?> saved) {
        this.name = name == null ? "" : name;
        this.nameLocked = nameLocked;
        this.root = root == null ? "" : root;
        Object f = saved == null ? null : saved.get("font");
        this.ttf = f == null ? "" : f.toString();
        Object r = saved == null ? null : saved.get("rtl");
        this.rtlFont = r != null && r.toString().equals("true");
        p.clear();
        for (Fld fl : fields()) {
            Object v = saved == null ? null : saved.get(fl.key);
            String n = v == null ? null : norm(fl, v.toString());
            p.put(fl.key, n != null ? n : fl.def);
        }
        activePreset = null;
        advancedOpen = false;
    }

    public String name() {
        return name;
    }

    public String ttf() {
        return ttf;
    }

    public boolean rtl() {
        return rtlFont;
    }

    public Map<String, String> params() {
        return new LinkedHashMap<>(p);
    }

    public void setTtf(String t) {
        ttf = t == null ? "" : t;
        if (built) {
            syncAll();
            touched(false);
        }
    }

    public void setName(String n) {
        name = n == null ? "" : n;
    }

    /** Normalizes a stored/typed value for an option, or returns null when it is not valid. */
    static String norm(Fld f, String raw) {
        try {
            String v = raw.trim();
            switch (f.kind) {
                case "float":
                    return String.valueOf(Float.parseFloat(v));
                case "int":
                    return String.valueOf(Math.round(Float.parseFloat(v)));
                case "color":
                    return Color.valueOf(v).toString().toUpperCase();
                case "bool":
                    return v.equalsIgnoreCase("true") ? "true" : (v.equalsIgnoreCase("false") ? "false" : null);
                case "enum":
                    for (String o : f.options) if (o.equalsIgnoreCase(v)) return o;
                    return null;
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static float num(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static String show(String v) {
        float f = num(v, Float.NaN);
        if (Float.isNaN(f)) return v;
        if (f == Math.rint(f)) return String.valueOf((int) f);
        return String.valueOf(Math.round(f * 100f) / 100f);
    }

    private Fld fld(String key) {
        for (Fld f : fields()) if (f.key.equals(key)) return f;
        return null;
    }

    private void put(String key, String value, boolean user) {
        p.put(key, value);
        touched(user);
    }

    private void touched(boolean user) {
        if (user) activePreset = null;
        previewDirty = true;
        previewWait = 0.1f;
        if (chipsBuilt) syncChips();
    }

    // ----------------------------------------------------------------- state of the built UI

    private FontUi.Theme th;
    private boolean built, rtl, land;
    private float builtW, builtH;
    private ScrollPane settingsScroll;
    private float lockY;
    private final ArrayList<Runnable> rowSync = new ArrayList<>();
    private Label previewLabel, titleSub;
    private FontUi.Panel previewCard;
    private final ArrayList<FontUi.Btn> bgDots = new ArrayList<>();
    private float previewCardH;
    private boolean previewDirty;
    private float previewWait;
    private BitmapFont previewFont;
    private Actor popup;

    private static final class Chip {
        FontPresets.Preset preset;
        Label sample;
        FontUi.Btn box;
    }

    private final ArrayList<Chip> chips = new ArrayList<>();
    private boolean chipsBuilt;
    private final HashMap<String, BitmapFont> chipFonts = new HashMap<>();
    private final HashMap<String, FreeTypeFontGenerator> generators = new HashMap<>();
    private final ArrayList<String> chipFailed = new ArrayList<>();

    // ----------------------------------------------------------------- lifecycle

    @Override
    public void act(float dt) {
        super.act(dt);
        float w = getWidth(), h = getHeight();
        if (w < 2f || h < 2f) return;
        if (!built || Math.abs(w - builtW) > 1f || Math.abs(h - builtH) > 1f) {
            try {
                build();
            } catch (Throwable t) {
                Gdx.app.log("star2d_fonts", "font maker ui : " + t);
                buildFallback(t);
            }
            return;
        }
        generateChipFonts();
        if (previewDirty) {
            previewWait -= dt;
            if (previewWait <= 0f) refreshPreview();
        }
    }

    @Override
    protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
        if (th != null) th.fill(batch, x, y, getWidth(), getHeight(), FontUi.BG, parentAlpha * getColor().a, 0);
    }

    /** Frees GL resources (fonts, textures). The model stays; build() runs again when the form is shown again. */
    public void dispose() {
        closePopup();
        built = false;
        chipsBuilt = false;
        chips.clear();
        previewLabel = null;
        for (BitmapFont f : chipFonts.values()) {
            try {
                f.dispose();
            } catch (Throwable ignored) {
            }
        }
        chipFonts.clear();
        chipFailed.clear();
        disposeGenerators();
        if (previewFont != null) {
            try {
                previewFont.dispose();
            } catch (Throwable ignored) {
            }
            previewFont = null;
        }
        clearChildren();
        if (th != null) th.dispose();
        th = null;
    }

    private void disposeGenerators() {
        for (FreeTypeFontGenerator g : generators.values()) {
            try {
                g.dispose();
            } catch (Throwable ignored) {
            }
        }
        generators.clear();
    }

    /** Back key: closes an open popup first. Returns true when it did. */
    public boolean closePopup() {
        if (popup == null) return false;
        popup.remove();
        popup = null;
        return true;
    }

    private void buildFallback(Throwable t) {
        built = true;
        builtW = getWidth();
        builtH = getHeight();
        clearChildren();
        if (th == null) th = new FontUi.Theme();
        Label l = th.label(Tr.t("font_ui_error", "The font editor could not be drawn") + "\n" + t, true, FontUi.DANGER);
        l.setWrap(true);
        add(l).growX().pad(th.dp(16)).row();
        FontUi.Btn close = new FontUi.Btn(th, FontUi.ACCENT, 10, () -> {
            if (onCancel != null) onCancel.run();
        });
        close.add(th.label(Tr.t("cancel", "Cancel"), false, FontUi.TEXT)).pad(th.dp(10), th.dp(24), th.dp(10), th.dp(24));
        add(close).top();
        top();
    }

    // ----------------------------------------------------------------- build

    private void build() {
        clearChildren();
        closePopup();
        rowSync.clear();
        chips.clear();
        bgDots.clear();
        chipsBuilt = false;
        previewLabel = null;
        if (th == null) th = new FontUi.Theme();
        rtl = env.isRtl();
        built = true;
        builtW = getWidth();
        builtH = getHeight();
        land = builtW > builtH * 1.05f;
        float m = th.dp(12);
        top();

        add(buildHeader()).growX().height(th.dp(56)).row();
        add(new FontUi.Panel(th, FontUi.BORDER, 0)).growX().height(1f).row();

        settingsScroll = buildSettings();
        if (!land) {
            previewCardH = Math.max(th.dp(96), Math.min(th.dp(170), builtH * 0.17f));
            Table topBlock = new Table();
            topBlock.add(buildPreviewBlock()).growX().row();
            topBlock.add(buildPresets()).growX().padTop(th.dp(8)).row();
            add(topBlock).growX().pad(m, m, 0, m).row();
            add(settingsScroll).grow().row();
        } else {
            float chipStrip = th.dp(78 + 8 + 22);
            float avail = builtH - th.dp(56) - th.dp(24) /*paddings*/ - th.dp(26 + 10) /*preview title + gap*/ - chipStrip - th.dp(8);
            previewCardH = Math.max(th.dp(64), Math.min(th.dp(170), avail));
            Table body = new Table();
            Table left = new Table();
            left.top();
            left.add(buildPreviewBlock()).growX().row();
            left.add(buildPresets()).grow().padTop(th.dp(8)).row();
            body.add(left).width(builtW * 0.44f).growY().top().pad(m, m, m, 0);
            body.add(settingsScroll).grow();
            add(body).grow().row();
        }
        refreshPreview();
        syncChips();
    }

    private Table buildHeader() {
        FontUi.Panel header = new FontUi.Panel(th, FontUi.SURFACE, 0);
        float h = th.dp(40);
        FontUi.Btn cancel = new FontUi.Btn(th, FontUi.SURFACE_3, 10, () -> {
            if (onCancel != null) onCancel.run();
        });
        cancel.add(th.label(Tr.t("cancel", "Cancel"), false, FontUi.TEXT)).pad(0, th.dp(14), 0, th.dp(14));
        FontUi.Btn save = new FontUi.Btn(th, FontUi.ACCENT, 10, () -> {
            if (onSave != null) onSave.run();
        });
        save.add(th.label(Tr.t("save", "Save"), false, Color.WHITE)).pad(0, th.dp(18), 0, th.dp(18));

        Table titles = new Table();
        Label t = th.label(Tr.t("bitmapFontEditor", "Font Editor"), false, FontUi.TEXT);
        t.setAlignment(Align.center);
        titleSub = th.label(name.isEmpty() ? "" : name + ".s2df", true, FontUi.TEXT_3);
        titleSub.setAlignment(Align.center);
        titles.add(t).growX().minWidth(0).row();
        titles.add(titleSub).growX().minWidth(0);

        float side = th.dp(12);
        if (!rtl) {
            header.add(cancel).height(h).padLeft(side).minWidth(th.dp(76));
            header.add(titles).growX().minWidth(0).padLeft(th.dp(8)).padRight(th.dp(8));
            header.add(save).height(h).padRight(side).minWidth(th.dp(76));
        } else {
            header.add(save).height(h).padLeft(side).minWidth(th.dp(76));
            header.add(titles).growX().minWidth(0).padLeft(th.dp(8)).padRight(th.dp(8));
            header.add(cancel).height(h).padRight(side).minWidth(th.dp(76));
        }
        return header;
    }

    // ----------------------------------------------------------------- preview

    private Table buildPreviewBlock() {
        Table block = new Table();
        // caption row ABOVE the card (never drawn over the sample): "Preview" + the background switch
        Table bar = new Table();
        Label cap = th.label(Tr.t("font_preview", "Preview"), false, FontUi.TEXT_2);
        cap.setAlignment(rtl ? Align.right : Align.left);
        Table dots = new Table();
        for (int i = 0; i < PREVIEW_BGS.length; i++) {
            final int idx = i;
            FontUi.Btn dot = new FontUi.Btn(th, PREVIEW_BGS[i], 11, () -> {
                previewBg = idx;
                styleDots();
                if (previewCard != null) previewCard.fillColor(PREVIEW_BGS[previewBg]);
            });
            bgDots.add(dot);
            dots.add(dot).size(th.dp(24)).pad(0, th.dp(4), 0, th.dp(4));
        }
        styleDots();
        if (!rtl) {
            bar.add(cap).growX().minWidth(0);
            bar.add(dots);
        } else {
            bar.add(dots);
            bar.add(cap).growX().minWidth(0);
        }
        block.add(bar).growX().height(th.dp(30)).row();

        previewCard = new FontUi.Panel(th, PREVIEW_BGS[previewBg], 12).outline(FontUi.BORDER, 1f);
        previewLabel = th.label("", false, Color.WHITE);
        previewLabel.setEllipsis(false);
        previewLabel.setAlignment(Align.center);
        previewCard.add(previewLabel).expand().center().maxWidth(Math.max(th.dp(80), (land ? builtW * 0.44f : builtW - th.dp(24)) - th.dp(24)));
        block.add(previewCard).growX().height(previewCardH).row();
        return block;
    }

    private void styleDots() {
        for (int i = 0; i < bgDots.size(); i++)
            bgDots.get(i).outline(i == previewBg ? FontUi.ACCENT : FontUi.TEXT_3, i == previewBg ? th.dp(2.5f) : 1f);
    }

    private String sample() {
        return rtlFont ? "أبج AaBb 123" : "Aa Bb 123";
    }

    private FileHandle resolveTtf() {
        FileHandle f = null;
        if (!ttf.isEmpty()) {
            f = Gdx.files.absolute(root + (ttf.startsWith("/") ? "" : "/") + ttf);
            if (!f.exists()) f = Gdx.files.absolute(ttf);
        }
        if (f == null || !f.exists()) f = Gdx.files.internal("files/DroidSans.ttf");
        return f;
    }

    /** Draws the sample with the values currently in the model (GL thread). */
    private void refreshPreview() {
        previewDirty = false;
        if (previewLabel == null || th == null) return;
        try {
            float size = Math.max(8f, num(p.get("size"), 32f));
            float target = Math.max(th.dp(22), Math.min(th.dp(120), previewCardH * 0.4f));
            float scale = Math.max(0.15f, Math.min(8f, target / size));
            String text = sample();
            BitmapFont font = FontPresets.generate(resolveTtf(), p, scale, text);
            BitmapFont old = previewFont;
            previewFont = font;
            previewLabel.setStyle(new Label.LabelStyle(font, Color.WHITE));
            previewLabel.setFontScale(1f);
            previewLabel.setText(text);
            float maxW = (land ? builtW * 0.44f : builtW - th.dp(24)) - th.dp(40);
            float pw = previewLabel.getPrefWidth();
            if (pw > maxW && pw > 0) previewLabel.setFontScale(maxW / pw);
            previewLabel.invalidateHierarchy();
            if (old != null) old.dispose();
        } catch (Throwable t) {
            Gdx.app.log("star2d_fonts", "preview : " + t);
            previewLabel.setStyle(th.style(true, FontUi.DANGER));
            previewLabel.setFontScale(1f);
            previewLabel.setText(Tr.t("font_preview_error", "This font can't be drawn: check the font file and the values"));
            previewLabel.invalidateHierarchy();
        }
    }

    // ----------------------------------------------------------------- presets

    private Table buildPresets() {
        Table block = new Table();
        Label cap = th.label(Tr.t("font_presets", "Presets"), false, FontUi.TEXT_2);
        Label hint = th.label(Tr.t("font_presets_hint", "tap one to apply"), true, FontUi.TEXT_3);
        cap.setAlignment(rtl ? Align.right : Align.left);
        hint.setAlignment(rtl ? Align.left : Align.right);
        Table bar = new Table();
        if (!rtl) {
            bar.add(cap).minWidth(0).padRight(th.dp(8));
            bar.add(hint).growX().minWidth(0);
        } else {
            bar.add(hint).growX().minWidth(0);
            bar.add(cap).minWidth(0).padLeft(th.dp(8));
        }
        block.add(bar).growX().height(th.dp(26)).row();

        Table strip = new Table();
        List<FontPresets.Preset> presets;
        try {
            presets = FontPresets.all();
        } catch (Throwable t) {
            presets = new ArrayList<>();
        }
        float cw = th.dp(100), ch = th.dp(80);
        int cols = Math.max(2, (int) ((builtW * 0.44f - th.dp(12)) / (cw + th.dp(8))));
        for (final FontPresets.Preset pr : presets) {
            final Chip chip = new Chip();
            chip.preset = pr;
            chip.box = new FontUi.Btn(th, Color.valueOf("3A3F47FF"), 10, () -> applyPreset(pr));
            chip.box.outline(FontUi.BORDER, 1f);
            chip.sample = th.label("", false, Color.WHITE);
            chip.sample.setEllipsis(false);
            chip.sample.setAlignment(Align.center);
            BitmapFont f = chipFonts.get(pr.id);
            if (f != null) {
                chip.sample.setStyle(new Label.LabelStyle(f, Color.WHITE));
                chip.sample.setText(pr.rtl ? "أب" : "Aa");
            }
            Label capt = th.label(Tr.t("fontp_" + pr.id, pr.name), true, FontUi.TEXT);
            capt.setAlignment(Align.center);
            chip.box.add(chip.sample).expand().center().row();
            chip.box.add(capt).growX().minWidth(0).pad(0, th.dp(4), th.dp(4), th.dp(4));
            chips.add(chip);
            Cell<?> cell = strip.add(chip.box).size(cw, ch).padRight(th.dp(8));
            if (land) {
                cell.padBottom(th.dp(8));
                if (chips.size() % cols == 0) strip.row();
            }
        }
        chipsBuilt = true;
        ScrollPane.ScrollPaneStyle sst = new ScrollPane.ScrollPaneStyle();
        sst.vScrollKnob = th.tinted(2.5f, new Color(1f, 1f, 1f, 0.35f));
        ScrollPane sp = new ScrollPane(strip, sst);
        sp.setScrollingDisabled(land, !land);
        sp.setFlickScroll(true);
        sp.setOverscroll(false, false);
        sp.setFadeScrollBars(true);
        sp.setScrollbarsOnTop(true);
        if (land) {
            strip.top().left();
            block.add(sp).grow().row();
        } else block.add(sp).growX().height(ch + th.dp(6)).row();
        return block;
    }

    private void syncChips() {
        for (Chip c : chips) {
            boolean on = c.preset.id.equals(activePreset);
            c.box.outline(on ? FontUi.ACCENT : FontUi.BORDER, on ? th.dp(2.5f) : 1f);
        }
    }

    /** Builds the preset thumbnails a couple per frame so opening the editor does not stall. */
    private void generateChipFonts() {
        if (!chipsBuilt || chips.isEmpty()) return;
        long start = System.nanoTime();
        boolean any = false;
        for (Chip c : chips) {
            String id = c.preset.id;
            if (chipFonts.containsKey(id) || chipFailed.contains(id)) continue;
            any = true;
            try {
                FreeTypeFontGenerator g = generators.get(c.preset.ttf);
                if (g == null) {
                    g = new FreeTypeFontGenerator(Gdx.files.internal("files/" + c.preset.ttf));
                    generators.put(c.preset.ttf, g);
                }
                float size = Math.max(8f, num(c.preset.params.containsKey("size") ? c.preset.params.get("size") : "32", 32f));
                float target = th.dp(24);
                String txt = c.preset.rtl ? "أب" : "Aa";
                BitmapFont f = FontPresets.generate(g, c.preset.params, Math.max(0.1f, Math.min(2f, target / size)), txt);
                chipFonts.put(id, f);
                c.sample.setStyle(new Label.LabelStyle(f, Color.WHITE));
                c.sample.setText(txt);
                c.sample.invalidateHierarchy();
            } catch (Throwable t) {
                chipFailed.add(id);
                c.sample.setStyle(th.style(false, FontUi.TEXT_3));
                c.sample.setText("?");
                Gdx.app.log("star2d_fonts", "chip " + id + " : " + t);
            }
            if (System.nanoTime() - start > 8_000_000L) return;
        }
        if (!any && !generators.isEmpty()) disposeGenerators();
    }

    private void applyPreset(FontPresets.Preset pr) {
        try {
            if (!root.isEmpty()) FontPresets.ensureBundledTtf(Gdx.files.absolute(root), pr.ttf);
            ttf = "/" + pr.ttf;
            rtlFont = pr.rtl;
            for (Fld f : fields()) {
                String v = pr.params.get(f.key);
                String n = v == null ? null : norm(f, v);
                p.put(f.key, n != null ? n : f.def);
            }
            syncAll();
            activePreset = pr.id;
            previewDirty = true;
            previewWait = 0f;
            syncChips();
        } catch (Throwable t) {
            env.toast("Error : " + t);
        }
    }

    // ----------------------------------------------------------------- settings

    /**
     * While a slider is held the list must not scroll. Disabling vertical scrolling would reset the scroll amount to 0 (the list
     * jumped to the top the moment a slider was touched), so only the drag-to-scroll gesture is switched off.
     */
    private void lockScroll() {
        if (settingsScroll == null) return;
        lockY = settingsScroll.getScrollY();
        settingsScroll.setCancelTouchFocus(false);
        settingsScroll.setFlickScroll(false);
    }

    private void unlockScroll() {
        if (settingsScroll == null) return;
        settingsScroll.setCancelTouchFocus(true);
        settingsScroll.setFlickScroll(true);
        settingsScroll.setScrollY(lockY);
        settingsScroll.updateVisualScroll();
    }

    private ScrollPane buildSettings() {
        ScrollPane.ScrollPaneStyle st = new ScrollPane.ScrollPaneStyle();
        st.vScrollKnob = th.tinted(2.5f, new Color(1f, 1f, 1f, 0.35f));
        Table content = new Table();
        content.top();
        fillSettings(content);
        ScrollPane sp = new ScrollPane(content, st);
        sp.setScrollingDisabled(true, false);
        sp.setFlickScroll(true);
        sp.setOverscroll(false, true);
        sp.setFadeScrollBars(true);
        sp.setScrollbarsOnTop(true);
        return sp;
    }

    private void fillSettings(Table content) {
        content.clearChildren();
        rowSync.clear();
        float m = th.dp(12);
        content.pad(m);
        content.defaults().growX();

        FontUi.Panel c = card(Tr.t("font_sec_source", "Font"), null);
        addRow(c, nameRow());
        addRow(c, ttfRow());
        addRow(c, switchRow(Tr.t("supportRtl", "Support RTL"), "supportRtl", () -> rtlFont, v -> {
            rtlFont = v;
            touched(true);
        }));
        content.add(c).padBottom(m).row();

        content.add(group(Tr.t("font_sec_size", "Size and spacing"), G_SIZE)).padBottom(m).row();
        content.add(group(Tr.t("font_sec_fill", "Fill"), G_FILL)).padBottom(m).row();
        content.add(group(Tr.t("font_sec_outline", "Outline"), G_OUTLINE)).padBottom(m).row();
        content.add(group(Tr.t("font_sec_shadow", "Shadow"), G_SHADOW)).padBottom(m).row();

        // everything else, folded away by default
        final FontUi.Panel adv = card(Tr.t("font_sec_advanced", "Advanced"), () -> {
            advancedOpen = !advancedOpen;
            // rebuilt a moment later: this runs inside the click event of a header that is about to be replaced
            Gdx.app.postRunnable(() -> {
                if (!built || settingsScroll == null) return;
                float y = settingsScroll.getScrollY();
                fillSettings(content);
                settingsScroll.validate();
                settingsScroll.setScrollY(y);
            });
        });
        if (advancedOpen) {
            for (Fld f : fields()) {
                if (in(G_SIZE, f.key) || in(G_FILL, f.key) || in(G_OUTLINE, f.key) || in(G_SHADOW, f.key)) continue;
                addRow(adv, rowFor(f));
            }
        }
        content.add(adv).padBottom(m).row();
        content.add().height(th.dp(8)).row();
    }

    private FontUi.Panel group(String title, String[] keys) {
        FontUi.Panel c = card(title, null);
        for (String k : keys) {
            Fld f = fld(k);
            if (f != null) addRow(c, rowFor(f));
        }
        return c;
    }

    /** A card with a title. toggle != null makes the title a button with a chevron (collapsible card). */
    private FontUi.Panel card(String title, Runnable toggle) {
        FontUi.Panel c = new FontUi.Panel(th, FontUi.SURFACE, 12).outline(FontUi.BORDER, 1f);
        c.pad(th.dp(12));
        c.defaults().growX();
        Label l = th.label(title.toUpperCase(), true, FontUi.ACCENT);
        l.setAlignment(rtl ? Align.right : Align.left);
        if (toggle == null) {
            c.add(l).minWidth(0).row();
        } else {
            Table head = new Table();
            FontUi.Chevron ch = new FontUi.Chevron(th, advancedOpen, FontUi.TEXT_2);
            if (!rtl) {
                head.add(l).growX().minWidth(0);
                head.add(ch).size(th.dp(18));
            } else {
                head.add(ch).size(th.dp(18));
                head.add(l).growX().minWidth(0);
            }
            head.setTouchable(Touchable.enabled);
            head.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    toggle.run();
                }
            });
            c.add(head).height(th.dp(30)).row();
        }
        return c;
    }

    private void addRow(FontUi.Panel c, Actor row) {
        c.add(row).growX().padTop(th.dp(8)).row();
    }

    private Actor rowFor(Fld f) {
        switch (f.kind) {
            case "bool":
                return switchRow(labelOf(f.key), f.key, () -> "true".equals(p.get(f.key)), v -> put(f.key, v ? "true" : "false", true));
            case "color":
                return colorRow(f);
            case "enum":
                return enumRow(f);
            default:
                return numberRow(f);
        }
    }

    /** Label (left) + control (right); mirrored for right-to-left languages. */
    private Table shell(String key, Actor control, float controlMinW) {
        Table t = new Table();
        Label l = th.label(labelOf(key), false, FontUi.TEXT);
        l.setAlignment(rtl ? Align.right : Align.left);
        l.setTouchable(Touchable.enabled);
        final Fld f = fld(key);
        l.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                env.details(key, f != null && f.spec != null ? Tr.t("font_d_" + key, f.spec.desc) : "");
            }
        });
        float h = th.dp(40);
        if (!rtl) {
            t.add(l).growX().minWidth(0).height(h).padRight(th.dp(8));
            t.add(control).minWidth(controlMinW).height(th.dp(36));
        } else {
            t.add(control).minWidth(controlMinW).height(th.dp(36));
            t.add(l).growX().minWidth(0).height(h).padLeft(th.dp(8));
        }
        return t;
    }

    private Table numberRow(final Fld f) {
        final boolean isInt = f.kind.equals("int");
        final Label val = th.label("", false, FontUi.TEXT);
        val.setAlignment(Align.center);
        final FontUi.Btn vb = new FontUi.Btn(th, FontUi.SURFACE_3, 8, null);
        vb.add(val).growX().minWidth(0).pad(0, th.dp(8), 0, th.dp(8));
        final FontUi.DSlider[] slider = new FontUi.DSlider[1];
        final Runnable sync = () -> {
            String v = p.get(f.key);
            val.setText(show(v));
            if (slider[0] != null) slider[0].setValue(num(v, 0f));
        };
        if (f.spec != null && f.spec.max > f.spec.min) {
            float range = f.spec.max - f.spec.min;
            float step = isInt ? 1f : (range >= 10f ? 0.5f : 0.05f);
            slider[0] = new FontUi.DSlider(th, f.spec.min, f.spec.max, step, v -> {
                put(f.key, isInt ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 100f) / 100f), true);
                val.setText(show(p.get(f.key)));
            }, this::lockScroll, this::unlockScroll);
        }
        vb.onClick(() -> {
            if (getStage() == null) return;
            NumberInputDialog.getInstance().setValue(show(p.get(f.key))).setCondition(NumberInputDialog.floatCondition).setOnInput(s -> {
                String n = norm(f, s);
                if (n != null) {
                    put(f.key, n, true);
                    sync.run();
                }
            }).show(getStage());
        });
        sync.run();
        rowSync.add(sync);

        // one line: name | slider | value (the slider is left-to-right in every language)
        Table t = new Table();
        Label l = th.label(labelOf(f.key), false, FontUi.TEXT);
        l.setAlignment(rtl ? Align.right : Align.left);
        l.setTouchable(Touchable.enabled);
        l.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                env.details(f.key, f.spec != null ? Tr.t("font_d_" + f.key, f.spec.desc) : "");
            }
        });
        float h = th.dp(40);
        if (slider[0] == null) {
            if (!rtl) {
                t.add(l).growX().minWidth(0).height(h).padRight(th.dp(8));
                t.add(vb).minWidth(th.dp(72)).height(th.dp(36));
            } else {
                t.add(vb).minWidth(th.dp(72)).height(th.dp(36));
                t.add(l).growX().minWidth(0).height(h).padLeft(th.dp(8));
            }
            return t;
        }
        float lw = th.dp(land ? 118 : 112);
        if (!rtl) {
            t.add(l).width(lw).minWidth(0).height(h);
            t.add(slider[0]).growX().height(th.dp(34)).padLeft(th.dp(4)).padRight(th.dp(8));
            t.add(vb).width(th.dp(64)).height(th.dp(36));
        } else {
            t.add(vb).width(th.dp(64)).height(th.dp(36));
            t.add(slider[0]).growX().height(th.dp(34)).padLeft(th.dp(8)).padRight(th.dp(4));
            t.add(l).width(lw).minWidth(0).height(h);
        }
        return t;
    }

    private Table colorRow(final Fld f) {
        final FontUi.Swatch sw = new FontUi.Swatch(th);
        final Label hex = th.label("", true, FontUi.TEXT_2);
        hex.setEllipsis(false);
        final FontUi.Btn b = new FontUi.Btn(th, FontUi.SURFACE_3, 8, null);
        b.add(sw).size(th.dp(34), th.dp(22)).pad(0, th.dp(8), 0, th.dp(8));
        b.add(hex).padRight(th.dp(8));
        final Runnable sync = () -> {
            String v = p.get(f.key);
            try {
                sw.setSwatch(Color.valueOf(v));
            } catch (Throwable t) {
                sw.setSwatch(Color.WHITE);
            }
            hex.setText("#" + v);
        };
        b.onClick(() -> pickColor(f, sync));
        sync.run();
        rowSync.add(sync);
        return shell(f.key, b, th.dp(150));
    }

    private void pickColor(final Fld f, final Runnable sync) {
        final ColorPicker cp = env.colorPicker();
        final Stage st = getStage();
        if (cp == null || st == null) return;
        final String old = p.get(f.key);
        Color oldColor;
        try {
            oldColor = Color.valueOf(old);
        } catch (Throwable t) {
            oldColor = Color.WHITE;
        }
        cp.setListener(null);
        cp.setColor(oldColor);
        cp.setListener(new ColorPickerAdapter() {
            @Override
            public void changed(Color c) {
                put(f.key, c.toString().toUpperCase(), true);
                sync.run();
            }

            @Override
            public void finished(Color c) {
                put(f.key, c.toString().toUpperCase(), true);
                sync.run();
                cp.setListener(null);
            }

            @Override
            public void canceled(Color o) {
                put(f.key, old, true);
                sync.run();
                cp.setListener(null);
            }
        });
        st.addActor(cp);
        cp.toFront();
    }

    private Table enumRow(final Fld f) {
        final Label val = th.label("", false, FontUi.TEXT);
        val.setAlignment(Align.center);
        final FontUi.Btn b = new FontUi.Btn(th, FontUi.SURFACE_3, 8, null);
        b.add(val).growX().minWidth(0).pad(0, th.dp(10), 0, th.dp(6));
        b.add(new FontUi.Chevron(th, false, FontUi.TEXT_2)).size(th.dp(16)).padRight(th.dp(8));
        final Runnable sync = () -> val.setText(optionLabel(p.get(f.key)));
        b.onClick(() -> showChoice(labelOf(f.key), f.options, p.get(f.key), v -> {
            put(f.key, v, true);
            sync.run();
        }));
        sync.run();
        rowSync.add(sync);
        return shell(f.key, b, th.dp(150));
    }

    private Table switchRow(String label, String key, final java.util.function.BooleanSupplier get, final Consumer<Boolean> set) {
        final FontUi.Switch sw = new FontUi.Switch(th, get.getAsBoolean(), v -> set.accept(v));
        Table t = new Table();
        Label l = th.label(label, false, FontUi.TEXT);
        l.setAlignment(rtl ? Align.right : Align.left);
        l.setTouchable(Touchable.enabled);
        final Fld f = fld(key);
        l.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (f != null) env.details(key, f.spec != null ? Tr.t("font_d_" + key, f.spec.desc) : "");
                else env.details(key, "");
            }
        });
        float h = th.dp(40);
        if (!rtl) {
            t.add(l).growX().minWidth(0).height(h).padRight(th.dp(8));
            t.add(sw).size(th.dp(46), th.dp(26));
        } else {
            t.add(sw).size(th.dp(46), th.dp(26));
            t.add(l).growX().minWidth(0).height(h).padLeft(th.dp(8));
        }
        rowSync.add(() -> sw.setOn(get.getAsBoolean()));
        return t;
    }

    private Table nameRow() {
        final Label val = th.label("", false, nameLocked ? FontUi.TEXT_3 : FontUi.TEXT);
        val.setAlignment(rtl ? Align.left : Align.right);
        final FontUi.Btn b = new FontUi.Btn(th, FontUi.SURFACE_3, 8, null);
        b.add(val).growX().minWidth(0).pad(0, th.dp(10), 0, th.dp(10));
        b.enabled(!nameLocked);
        b.onClick(() -> {
            if (getStage() == null) return;
            new SingleInputDialog(Tr.t("font_file_name", "File name"), Tr.t("font_file_name_hint", "Name of the font file (without .s2df)"), name, s -> {
                String v = s == null ? "" : s.trim();
                name = v;
                if (titleSub != null) titleSub.setText(v.isEmpty() ? "" : v + ".s2df");
                val.setText(v);
            }).show(getStage());
        });
        rowSync.add(() -> val.setText(name));
        val.setText(name);
        return labelledValue(Tr.t("fileName", "File name"), b);
    }

    private Table ttfRow() {
        final Label val = th.label("", false, FontUi.TEXT);
        val.setAlignment(rtl ? Align.left : Align.right);
        final FontUi.Btn b = new FontUi.Btn(th, FontUi.SURFACE_3, 8, null);
        b.add(val).growX().minWidth(0).pad(0, th.dp(10), 0, th.dp(10));
        b.onClick(() -> env.pickTtf(root, path -> {
            ttf = path == null ? "" : path;
            val.setText(ttf.isEmpty() ? Tr.t("font_choose_ttf", "Choose a font file") : ttf);
            touched(true);
        }));
        final Runnable sync = () -> val.setText(ttf.isEmpty() ? Tr.t("font_choose_ttf", "Choose a font file") : ttf);
        sync.run();
        rowSync.add(sync);
        return labelledValue(Tr.t("font_ttf", "Font file"), b);
    }

    private Table labelledValue(String label, Actor control) {
        Table t = new Table();
        Label l = th.label(label, false, FontUi.TEXT);
        l.setAlignment(rtl ? Align.right : Align.left);
        float h = th.dp(36);
        if (!rtl) {
            t.add(l).width(th.dp(120)).minWidth(0).padRight(th.dp(8));
            t.add(control).growX().height(h);
        } else {
            t.add(control).growX().height(h);
            t.add(l).width(th.dp(120)).minWidth(0).padLeft(th.dp(8));
        }
        return t;
    }

    private void syncAll() {
        for (Runnable r : new ArrayList<>(rowSync)) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    // ----------------------------------------------------------------- choice popup

    private void showChoice(String title, String[] options, String current, final Consumer<String> pick) {
        final Stage st = getStage();
        if (st == null || options == null) return;
        closePopup();
        final FontUi.Panel dim = new FontUi.Panel(th, new Color(0f, 0f, 0f, 0.62f), 0);
        dim.setFillParent(true);
        dim.setTouchable(Touchable.enabled);
        dim.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (event.getTarget() == dim) closePopup();
            }
        });
        FontUi.Panel box = new FontUi.Panel(th, FontUi.SURFACE, 14).outline(FontUi.BORDER, 1f);
        box.setTouchable(Touchable.enabled);
        box.pad(th.dp(12));
        Label t = th.label(title, false, FontUi.TEXT_2);
        t.setAlignment(Align.center);
        box.add(t).growX().minWidth(0).padBottom(th.dp(8)).row();
        Table list = new Table();
        float bh = th.dp(44);
        for (final String o : options) {
            boolean on = o.equals(current);
            FontUi.Btn ob = new FontUi.Btn(th, on ? FontUi.ACCENT : FontUi.SURFACE_3, 10, () -> {
                closePopup();
                pick.accept(o);
            });
            Label ol = th.label(optionLabel(o), false, on ? Color.WHITE : FontUi.TEXT);
            ol.setAlignment(Align.center);
            ob.add(ol).growX().minWidth(0);
            list.add(ob).growX().height(bh).padBottom(th.dp(6)).row();
        }
        ScrollPane sp = new ScrollPane(list, new ScrollPane.ScrollPaneStyle());
        sp.setScrollingDisabled(true, false);
        sp.setOverscroll(false, true);
        float listH = Math.min(options.length * (bh + th.dp(6)), Math.max(th.dp(120), st.getHeight() * 0.62f));
        box.add(sp).growX().height(listH).row();
        float w = Math.min(st.getWidth() - th.dp(32), th.dp(320));
        dim.add(box).width(w);
        popup = dim;
        st.addActor(dim);
        dim.toFront();
    }
}
