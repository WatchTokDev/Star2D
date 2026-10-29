package com.star4droid.star2d.ui.uimaker;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.ColorPickerDialog;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.IconBrowser;
import com.star4droid.star2d.ui.NumberPad;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UILayout;
import com.star4droid.template.ui.UINode;
import com.star4droid.template.ui.UITheme;
import com.star4droid.template.ui.UIWidgetRegistry;
import com.star4droid.template.ui.UIWidgetRegistry.Kind;
import com.star4droid.template.ui.UIWidgetRegistry.Prop;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * The "Properties" tab of the UI Maker: one editor per property of the selected node, built from {@link UIWidgetRegistry} (so a property
 * added there shows up here by itself). Color / number / image / enum / size / margin editors reuse the editor's own pickers.
 */
final class UiPropsView {
    interface Host {
        Activity activity();

        String projectPath();

        UIDocument doc();

        /** A property changed: render again (history is recorded a moment later). */
        void edited();

        /** The id changed: the tree must be refreshed. */
        void idChanged();

        /** Current rectangle of a node in logical px, or null. */
        UiRects.R rectOf(String id);
    }

    private static final HashSet<String> COLLAPSED = new HashSet<>();

    static {
        COLLAPSED.add("Advanced");
    }

    private final Host host;
    private final Context c;

    UiPropsView(Host host) {
        this.host = host;
        this.c = host.activity();
    }

    // ------------------------------------------------------------------ build

    private static final String[] ORDER = {UIWidgetRegistry.G_TEXT, UIWidgetRegistry.G_VALUE, UIWidgetRegistry.G_LOOK, UIWidgetRegistry.G_LAYOUT, UIWidgetRegistry.G_BEHAVIOR, UIWidgetRegistry.G_ADV};
    private static final String[] LAYOUT_FIRST = {"anchor", "w", "h", "margin", "x", "y", "weight"};

    View build(final UINode n) {
        LinearLayout root = Ui.column(c);
        root.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 24));
        if (n == null) {
            TextView t = Ui.text(c, Tr.t("uim_nothing_selected", "Nothing selected. Tap an element in the preview or in the Tree tab."), 14, Ui.TEXT_3);
            t.setPadding(0, Ui.dp(c, 24), 0, 0);
            t.setGravity(Gravity.CENTER);
            root.addView(t);
            return root;
        }
        UIWidgetRegistry.Widget wd = UIWidgetRegistry.get(n.type);
        // header: type + id
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.smallIcon(c, UiIcons.forType(n.type), Ui.ACCENT, 22));
        TextView tn = Ui.text(c, wd == null ? n.type : wd.name, 15, Ui.TEXT);
        tn.setTypeface(Typeface.DEFAULT_BOLD);
        tn.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 10), 0);
        head.addView(tn);
        final EditText id = Ui.input(c, n.id, "id");
        id.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        Runnable applyId = () -> {
            String want = id.getText().toString().trim();
            if (want.isEmpty() || want.equals(n.id)) {
                id.setText(n.id);
                return;
            }
            String fin = UiOps.rename(host.doc(), n, want);
            id.setText(fin);
            host.idChanged();
            host.edited();
        };
        id.setOnEditorActionListener((v, a, e) -> {
            applyId.run();
            return false;
        });
        id.setOnFocusChangeListener((v, f) -> {
            if (!f) applyId.run();
        });
        head.addView(id, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(head, Ui.matchWrap());
        if (wd != null && wd.desc != null) {
            TextView d = Ui.text(c, wd.desc, 12, Ui.TEXT_3);
            d.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
            root.addView(d);
        }

        // group the properties
        LinkedHashMap<String, ArrayList<Prop>> groups = new LinkedHashMap<>();
        for (String g : ORDER) groups.put(g, new ArrayList<Prop>());
        for (Prop pr : UIWidgetRegistry.propsFor(n.type)) {
            if (pr.key.equals("id")) continue;
            String g = pr.advanced ? UIWidgetRegistry.G_ADV : pr.group;
            if (!groups.containsKey(g)) groups.put(g, new ArrayList<Prop>());
            groups.get(g).add(pr);
        }
        ArrayList<Prop> lay = groups.get(UIWidgetRegistry.G_LAYOUT);
        java.util.Collections.sort(lay, (a, b) -> Integer.compare(layoutRank(a.key), layoutRank(b.key)));
        for (java.util.Map.Entry<String, ArrayList<Prop>> e : groups.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            addSection(root, n, e.getKey(), e.getValue());
        }
        return root;
    }

    private static int layoutRank(String key) {
        for (int i = 0; i < LAYOUT_FIRST.length; i++) if (LAYOUT_FIRST[i].equals(key)) return i;
        return 100;
    }

    private String groupTitle(String g) {
        switch (g) {
            case UIWidgetRegistry.G_TEXT: return Tr.t("uim_g_text", "Text");
            case UIWidgetRegistry.G_VALUE: return Tr.t("uim_g_value", "Value");
            case UIWidgetRegistry.G_LOOK: return Tr.t("uim_g_look", "Appearance");
            case UIWidgetRegistry.G_LAYOUT: return Tr.t("uim_g_layout", "Layout");
            case UIWidgetRegistry.G_BEHAVIOR: return Tr.t("uim_g_behavior", "Behavior");
            default: return Tr.t("uim_g_adv", "Advanced");
        }
    }

    private void addSection(final LinearLayout root, final UINode n, final String group, ArrayList<Prop> props) {
        final LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 4));
        final TextView title = Ui.text(c, groupTitle(group).toUpperCase(Locale.ROOT), 12, Ui.TEXT_2);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.08f);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final ImageView arrow = Ui.smallIcon(c, COLLAPSED.contains(group) ? R.drawable.ic_s2_expand_more : R.drawable.ic_s2_expand_less, Ui.TEXT_3, 20);
        head.addView(arrow);
        root.addView(head, Ui.matchWrap());
        final LinearLayout body = Ui.column(c);
        for (Prop pr : props) body.addView(row(n, pr), Ui.matchWrap());
        body.setVisibility(COLLAPSED.contains(group) ? View.GONE : View.VISIBLE);
        root.addView(body, Ui.matchWrap());
        head.setOnClickListener(v -> {
            boolean hide = body.getVisibility() == View.VISIBLE;
            body.setVisibility(hide ? View.GONE : View.VISIBLE);
            if (hide) COLLAPSED.add(group);
            else COLLAPSED.remove(group);
            arrow.setImageResource(hide ? R.drawable.ic_s2_expand_more : R.drawable.ic_s2_expand_less);
        });
    }

    // ------------------------------------------------------------------ one row

    /** A property row that can rebuild itself (after a reset, or to show the new value). */
    private View row(final UINode n, final Prop pr) {
        final FrameLayout holder = new FrameLayout(c);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            holder.removeAllViews();
            holder.addView(rowBody(n, pr, fill[0]));
        };
        fill[0].run();
        return holder;
    }

    private View rowBody(final UINode n, final Prop pr, final Runnable refresh) {
        LinearLayout box = Ui.column(c);
        box.setPadding(0, Ui.dp(c, 5), 0, Ui.dp(c, 5));
        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = Ui.text(c, pr.label, 13, Ui.TEXT_2);
        top.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (UiOps.isSet(n, pr.key) && !pr.key.equals("id")) {
            ImageView reset = Ui.smallIcon(c, R.drawable.ic_s2_refresh, Ui.TEXT_3, 18);
            reset.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
            reset.setContentDescription(Tr.t("uim_reset", "Reset"));
            reset.setOnClickListener(v -> {
                UiOps.reset(n, pr.key);
                host.edited();
                refresh.run();
            });
            top.addView(reset);
        }
        box.addView(top, Ui.matchWrap());
        if (pr.desc != null && !pr.desc.isEmpty() && pr.kind != Kind.BOOL) {
            // short hint under the label only for the ones that need it
        }
        View ctl = control(n, pr, refresh);
        box.addView(ctl, Ui.matchWrap());
        return box;
    }

    private View control(UINode n, Prop pr, Runnable refresh) {
        switch (pr.kind) {
            case NUMBER: case INT: return numberControl(n, pr, refresh);
            case BOOL: return boolControl(n, pr, refresh);
            case ENUM: return chips(n, pr, refresh);
            case ANCHOR: return anchorControl(n, pr, refresh);
            case COLOR: return colorControl(n, pr, refresh);
            case IMAGE: return imageControl(n, pr, refresh);
            case SOUND: return fileControl(n, pr, refresh, "sounds", new String[]{"ogg", "mp3", "wav"}, false);
            case FONT: return fileControl(n, pr, refresh, "fonts", new String[]{"s2df", "json", "fnt"}, true);
            case SIZE: return sizeControl(n, pr, refresh);
            case MARGIN: case PADDING: return boxControl(n, pr, refresh);
            case MULTILINE: return textControl(n, pr, true);
            default: return textControl(n, pr, false);
        }
    }

    private void set(UINode n, Prop pr, String v) {
        String err = UIWidgetRegistry.set(n, pr.key, v);
        if (err != null) Ui.toast(c, err);
        host.edited();
    }

    // ------------------------------------------------------------------ number

    private LinearLayout pill() {
        LinearLayout l = Ui.row(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView button(String t, View.OnClickListener l) {
        TextView b = Ui.text(c, t, 16, Ui.TEXT);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 8)));
        b.setMinWidth(Ui.dp(c, 44));
        b.setMinHeight(Ui.dp(c, 40));
        b.setOnClickListener(l);
        return b;
    }

    private static String fmt(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private View numberControl(final UINode n, final Prop pr, final Runnable refresh) {
        LinearLayout l = pill();
        final String cur = UIWidgetRegistry.get(n, pr.key);
        final float step = Float.isNaN(pr.step) || pr.step <= 0 ? 1f : pr.step;
        l.addView(button("−", v -> bump(n, pr, -step, refresh)));
        final TextView val = Ui.text(c, cur.isEmpty() ? pr.def : cur, 15, cur.isEmpty() ? Ui.TEXT_3 : Ui.TEXT);
        val.setGravity(Gravity.CENTER);
        val.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, 1));
        val.setMinHeight(Ui.dp(c, 40));
        val.setOnClickListener(v -> NumberPad.show(c, pr.label, cur.isEmpty() ? pr.def : cur, value -> {
            set(n, pr, value);
            refresh.run();
        }, () -> Dialogs.input(c, pr.label, rangeText(pr), cur, null, value -> {
            set(n, pr, value);
            refresh.run();
        })));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        l.addView(val, lp);
        l.addView(button("+", v -> bump(n, pr, step, refresh)));
        return l;
    }

    private String rangeText(Prop pr) {
        if (Float.isNaN(pr.min) && Float.isNaN(pr.max)) return pr.desc == null ? "" : pr.desc;
        return (pr.desc == null ? "" : pr.desc + "\n") + (Float.isNaN(pr.min) ? "" : "min " + fmt(pr.min)) + (Float.isNaN(pr.max) ? "" : "  max " + fmt(pr.max));
    }

    private void bump(UINode n, Prop pr, float delta, Runnable refresh) {
        String cur = UIWidgetRegistry.get(n, pr.key);
        float v;
        try {
            v = Float.parseFloat(cur.isEmpty() ? pr.def : cur);
        } catch (Exception e) {
            v = 0;
        }
        v = Math.round((v + delta) * 1000f) / 1000f;
        set(n, pr, fmt(v));
        refresh.run();
    }

    // ------------------------------------------------------------------ bool / enum / anchor

    private View boolControl(final UINode n, final Prop pr, final Runnable refresh) {
        final SwitchCompat sw = new SwitchCompat(c);
        String cur = UIWidgetRegistry.get(n, pr.key);
        boolean on = cur.isEmpty() ? pr.def.equalsIgnoreCase("true") : cur.equalsIgnoreCase("true");
        sw.setChecked(on);
        sw.setText(pr.desc == null ? "" : shorten(pr.desc, 60));
        sw.setTextColor(Ui.TEXT_3);
        sw.setTextSize(12);
        sw.setOnCheckedChangeListener((b, checked) -> {
            set(n, pr, String.valueOf(checked));
            refresh.run();
        });
        return sw;
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private TextView chip(String t, boolean on, View.OnClickListener l) {
        TextView v = Ui.text(c, t, 13, on ? 0xFFFFFFFF : Ui.TEXT_2);
        v.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        v.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_3, Ui.dp(c, 16)));
        v.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Ui.dp(c, 6));
        v.setLayoutParams(lp);
        return v;
    }

    private HorizontalScrollView hscroll(LinearLayout inner) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.addView(inner);
        return h;
    }

    private View chips(final UINode n, final Prop pr, final Runnable refresh) {
        LinearLayout l = Ui.row(c);
        String cur = UIWidgetRegistry.get(n, pr.key);
        for (final String o : pr.options) {
            boolean on = o.equalsIgnoreCase(cur);
            String shown = o.isEmpty() ? Tr.t("uim_default", "default") : o;
            l.addView(chip(shown, on, v -> {
                set(n, pr, o);
                refresh.run();
            }));
        }
        return hscroll(l);
    }

    private static final String[][] GRID9 = {{"topleft", "top", "topright"}, {"left", "center", "right"}, {"bottomleft", "bottom", "bottomright"}};

    private View anchorControl(final UINode n, final Prop pr, final Runnable refresh) {
        LinearLayout box = Ui.row(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout grid = Ui.column(c);
        String cur = n.anchor == null ? "topleft" : n.anchor.toLowerCase(Locale.ROOT);
        for (String[] r : GRID9) {
            LinearLayout row = Ui.row(c);
            for (final String a : r) {
                View b = new View(c);
                boolean on = cur.equals(a);
                b.setBackground(Ui.rounded(on ? Ui.ACCENT : Ui.SURFACE_3, Ui.dp(c, 6)));
                b.setOnClickListener(v -> {
                    set(n, pr, a);
                    refresh.run();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 34));
                lp.setMargins(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
                row.addView(b, lp);
            }
            grid.addView(row);
        }
        box.addView(grid);
        LinearLayout side = Ui.column(c);
        side.setPadding(Ui.dp(c, 12), 0, 0, 0);
        for (final String a : new String[]{"stretch", "stretchx", "stretchy"}) {
            TextView ch = chip(a, cur.equals(a), v -> {
                set(n, pr, a);
                refresh.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(c, 4);
            side.addView(ch, lp);
        }
        box.addView(side);
        return box;
    }

    // ------------------------------------------------------------------ color

    /** RGBA8888 (libGDX) to Android ARGB. */
    static int toArgb(int rgba) {
        return Color.argb(rgba & 255, (rgba >>> 24) & 255, (rgba >>> 16) & 255, (rgba >>> 8) & 255);
    }

    private int resolve(String spec) {
        try {
            return toArgb(host.doc().theme.resolveColor(spec, 0xFFFFFFFF));
        } catch (Throwable t) {
            return Color.WHITE;
        }
    }

    private View colorControl(final UINode n, final Prop pr, final Runnable refresh) {
        LinearLayout box = Ui.column(c);
        LinearLayout top = pill();
        final String cur = UIWidgetRegistry.get(n, pr.key);
        View sw = new View(c);
        sw.setBackground(Ui.rounded(cur.isEmpty() ? Ui.SURFACE_3 : resolve(cur), Ui.dp(c, 8), Ui.BORDER, Ui.dp(c, 1)));
        top.addView(sw, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 36)));
        TextView t = Ui.text(c, cur.isEmpty() ? (pr.def.isEmpty() ? Tr.t("uim_default", "default") : pr.def) : cur, 14, cur.isEmpty() ? Ui.TEXT_3 : Ui.TEXT);
        t.setPadding(Ui.dp(c, 10), 0, 0, 0);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(button("…", v -> ColorPickerDialog.show(c, pr.label, cur.isEmpty() ? "FFFFFFFF" : toHexForPicker(cur), null, hex -> {
            if (hex == null) return;
            set(n, pr, "#" + hex.toLowerCase(Locale.ROOT));
            refresh.run();
        })));
        box.addView(top, Ui.matchWrap());
        // theme roles
        LinearLayout roles = Ui.row(c);
        roles.setPadding(0, Ui.dp(c, 6), 0, 0);
        for (final String r : UITheme.ROLES) {
            View dot = new View(c);
            boolean on = r.equalsIgnoreCase(cur);
            dot.setBackground(Ui.rounded(resolve(r), Ui.dp(c, 14), on ? 0xFFFFFFFF : Ui.BORDER, Ui.dp(c, on ? 3 : 1)));
            dot.setContentDescription(r);
            dot.setOnClickListener(v -> {
                set(n, pr, r);
                refresh.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28));
            lp.setMarginEnd(Ui.dp(c, 6));
            roles.addView(dot, lp);
        }
        box.addView(hscroll(roles), Ui.matchWrap());
        return box;
    }

    /** The picker wants rrggbbaa; the property may hold a role or #rrggbb[aa]. */
    private String toHexForPicker(String spec) {
        int argb = resolve(spec);
        return ColorPickerDialog.toHex(argb);
    }

    // ------------------------------------------------------------------ image / files

    private File imagesDir() {
        return new File(host.projectPath(), "images");
    }

    private View imageControl(final UINode n, final Prop pr, final Runnable refresh) {
        LinearLayout l = pill();
        final String cur = UIWidgetRegistry.get(n, pr.key);
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, 1));
        iv.setImageResource(R.drawable.ic_s2_image);
        iv.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT_3));
        l.addView(iv, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        if (!cur.isEmpty()) {
            File f = new File(imagesDir(), cur.startsWith("/") ? cur.substring(1) : cur);
            if (f.isFile()) FilePickerDialog.thumb(iv, f, Ui.dp(c, 48));
        }
        TextView t = Ui.text(c, cur.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : cur, 13, cur.isEmpty() ? Ui.TEXT_3 : Ui.TEXT);
        t.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        t.setMaxLines(2);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        View.OnClickListener pick = v -> FilePickerDialog.show(c, pr.label, imagesDir().getAbsolutePath(), new String[]{"png", "jpg", "jpeg", "webp"}, true, (file, rel) -> {
            set(n, pr, "/" + rel.replace('\\', '/').replaceFirst("^/", ""));
            refresh.run();
        });
        iv.setOnClickListener(pick);
        t.setOnClickListener(pick);
        ImageView icons = Ui.iconButton(c, R.drawable.ic_s2_widgets, Tr.t("icn_title", "Icons"), v -> IconBrowser.show(host.activity(), new File(host.projectPath()), true, (useAs, file) -> {
            set(n, pr, useAs);
            refresh.run();
        }));
        l.addView(icons);
        return l;
    }

    private View fileControl(final UINode n, final Prop pr, final Runnable refresh, final String folder, final String[] ext, final boolean withFolder) {
        LinearLayout l = pill();
        final String cur = UIWidgetRegistry.get(n, pr.key);
        TextView t = Ui.text(c, cur.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : cur, 13, cur.isEmpty() ? Ui.TEXT_3 : Ui.TEXT);
        t.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, 1));
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10));
        t.setOnClickListener(v -> FilePickerDialog.show(c, pr.label, new File(host.projectPath(), folder).getAbsolutePath(), ext, false, (file, rel) -> {
            String r = rel.replace('\\', '/').replaceFirst("^/", "");
            set(n, pr, withFolder ? folder + "/" + r : r);
            refresh.run();
        }));
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return l;
    }

    // ------------------------------------------------------------------ size / margin / padding

    private View sizeControl(final UINode n, final Prop pr, final Runnable refresh) {
        LinearLayout box = Ui.column(c);
        final String cur = UIWidgetRegistry.get(n, pr.key);
        UINode.Size s = UINode.parseSize(cur);
        LinearLayout l = Ui.row(c);
        final String[] kinds = {"", "wrap", "fill", "px", "%"};
        for (final String k : kinds) {
            boolean on;
            switch (k) {
                case "": on = cur.isEmpty(); break;
                case "wrap": on = s.kind == UINode.WRAP; break;
                case "fill": on = s.kind == UINode.FILL; break;
                case "px": on = s.kind == UINode.PX; break;
                default: on = s.kind == UINode.PCT; break;
            }
            String label = k.isEmpty() ? Tr.t("uim_default", "default") : k;
            l.addView(chip(label, on, v -> {
                String val;
                UiRects.R r = host.rectOf(n.id);
                switch (k) {
                    case "": val = ""; break;
                    case "wrap": val = "wrap"; break;
                    case "fill": val = "fill"; break;
                    case "px": val = r == null ? "100" : fmt(Math.round(pr.key.equals("w") ? r.w : r.h)); break;
                    default: val = "50%"; break;
                }
                set(n, pr, val);
                refresh.run();
            }));
        }
        box.addView(hscroll(l), Ui.matchWrap());
        if (s.kind == UINode.PX || s.kind == UINode.PCT) {
            final boolean pct = s.kind == UINode.PCT;
            LinearLayout nl = pill();
            nl.setPadding(0, Ui.dp(c, 6), 0, 0);
            final float step = pct ? 5 : 1;
            final float cv = pct ? s.v * 100f : s.v;
            nl.addView(button("−", v -> setSize(n, pr, pct, cv - step * (pct ? 1 : 8), refresh)));
            TextView val = Ui.text(c, fmt(cv) + (pct ? "%" : " px"), 15, Ui.TEXT);
            val.setGravity(Gravity.CENTER);
            val.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, 1));
            val.setMinHeight(Ui.dp(c, 40));
            val.setOnClickListener(v -> NumberPad.show(c, pr.label, fmt(cv), value -> {
                try {
                    setSize(n, pr, pct, Float.parseFloat(value), refresh);
                } catch (Exception ignored) {
                }
            }, null));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
            nl.addView(val, lp);
            nl.addView(button("+", v -> setSize(n, pr, pct, cv + step * (pct ? 1 : 8), refresh)));
            box.addView(nl, Ui.matchWrap());
        }
        return box;
    }

    private void setSize(UINode n, Prop pr, boolean pct, float v, Runnable refresh) {
        v = Math.max(pct ? 1 : 1, v);
        set(n, pr, fmt(v) + (pct ? "%" : ""));
        refresh.run();
    }

    private View boxControl(final UINode n, final Prop pr, final Runnable refresh) {
        final float[] v;
        if (pr.kind == Kind.MARGIN) v = new float[]{n.ml, n.mt, n.mr, n.mb};
        else v = UILayout.padding(n).clone();
        LinearLayout l = Ui.row(c);
        String[] names = {Tr.t("uim_left", "Left"), Tr.t("uim_top", "Top"), Tr.t("uim_right", "Right"), Tr.t("uim_bottom", "Bottom")};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout cell = Ui.column(c);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView cap = Ui.text(c, names[i], 11, Ui.TEXT_3);
            cap.setGravity(Gravity.CENTER);
            cell.addView(cap);
            TextView val = Ui.text(c, fmt(v[i]), 15, Ui.TEXT);
            val.setGravity(Gravity.CENTER);
            val.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 8), Ui.BORDER, 1));
            val.setMinHeight(Ui.dp(c, 40));
            val.setOnClickListener(x -> NumberPad.show(c, pr.label + " – " + names[idx], fmt(v[idx]), value -> {
                try {
                    float f = Float.parseFloat(value);
                    float[] nv = v.clone();
                    nv[idx] = f;
                    set(n, pr, nv[0] + "," + nv[1] + "," + nv[2] + "," + nv[3]);
                    refresh.run();
                } catch (Exception ignored) {
                }
            }, null));
            cell.addView(val, Ui.matchWrap());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMarginEnd(Ui.dp(c, 6));
            l.addView(cell, lp);
        }
        // "all": sets the four values to the first one
        TextView all = button("=", x -> {
            set(n, pr, fmt(v[0]));
            refresh.run();
        });
        all.setContentDescription(Tr.t("uim_all_same", "Same on all sides"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        l.addView(all, lp);
        return l;
    }

    // ------------------------------------------------------------------ text

    private View textControl(final UINode n, final Prop pr, boolean multi) {
        final EditText e = Ui.input(c, UIWidgetRegistry.get(n, pr.key), pr.def.isEmpty() ? "" : pr.def);
        if (multi) {
            e.setSingleLine(false);
            e.setMinLines(2);
            e.setMaxLines(6);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        e.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        final Runnable[] pending = new Runnable[1];
        e.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                final String v = s.toString();
                if (pending[0] != null) e.removeCallbacks(pending[0]);
                pending[0] = () -> {
                    if (!v.equals(UIWidgetRegistry.get(n, pr.key))) {
                        String err = UIWidgetRegistry.set(n, pr.key, v);
                        if (err == null) host.edited();
                    }
                };
                e.postDelayed(pending[0], 350);
            }
        });
        return e;
    }
}
