package com.star4droid.star2d.ui.uimaker;

import com.star4droid.star2d.ui.Tr;
import com.star4droid.template.ui.UIWidgetRegistry;
import com.star4droid.template.ui.UIWidgetRegistry.Prop;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translation helpers of the UI Maker for the texts that come from the registries ({@link UIWidgetRegistry}, {@link UiTemplates},
 * {@link com.star4droid.template.ui.UITheme}) and from the pure-Java editing code ({@link UiOps}, node moves). The registries stay
 * pure Java with English texts; this class gives them translation keys and keeps the English text as the default, so a missing
 * translation never shows a key.
 *
 * Key scheme (all prefixed {@code uim_}):
 * <pre>
 *   widget name        uim_w_&lt;type&gt;              e.g. uim_w_button
 *   widget description uim_w_&lt;type&gt;_d
 *   property label     uim_p_&lt;c|type&gt;_&lt;key&gt;     c = a property every widget has, e.g. uim_p_c_alpha, uim_p_linear_spacing
 *   property help      uim_pd_&lt;c|type&gt;_&lt;key&gt;
 *   option             uim_o_&lt;option&gt;            e.g. uim_o_center, uim_o_jelly (lower case)
 *   theme role         uim_role_&lt;role&gt;           e.g. uim_role_primary, uim_role_surfacealt
 *   theme preset       uim_preset_&lt;name&gt;         e.g. uim_preset_glass_neon
 *   template           uim_tpl_&lt;name&gt; / uim_tpl_&lt;name&gt;_d
 *   group / category   uim_g_* / uim_cat_*
 * </pre>
 */
public final class UiTr {
    private UiTr() {}

    private static String low(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ widgets

    public static String widgetName(String type, String def) {
        return Tr.t("uim_w_" + low(type), def == null ? type : def);
    }

    /** Name of a widget type; the type itself when it is not in the registry. */
    public static String widgetName(String type) {
        UIWidgetRegistry.Widget w = UIWidgetRegistry.get(type);
        return widgetName(w == null ? type : w.type, w == null ? type : w.name);
    }

    public static String widgetDesc(String type, String def) {
        return Tr.t("uim_w_" + low(type) + "_d", def == null ? "" : def);
    }

    public static String categoryTitle(String cat) {
        switch (cat) {
            case "Layout": return Tr.t("uim_cat_layout", "Layout");
            case "Display": return Tr.t("uim_cat_display", "Display");
            default: return Tr.t("uim_cat_input", "Input");
        }
    }

    public static String groupTitle(String g) {
        switch (g) {
            case UIWidgetRegistry.G_TEXT: return Tr.t("uim_g_text", "Text");
            case UIWidgetRegistry.G_VALUE: return Tr.t("uim_g_value", "Value");
            case UIWidgetRegistry.G_LOOK: return Tr.t("uim_g_look", "Appearance");
            case UIWidgetRegistry.G_LAYOUT: return Tr.t("uim_g_layout", "Layout");
            case UIWidgetRegistry.G_BEHAVIOR: return Tr.t("uim_g_behavior", "Behavior");
            default: return Tr.t("uim_g_adv", "Advanced");
        }
    }

    // ------------------------------------------------------------------ properties

    private static String propKey(String nodeType, Prop p) {
        return (p.common ? "c" : low(nodeType)) + "_" + p.key;
    }

    public static String propLabel(String nodeType, Prop p) {
        return Tr.t("uim_p_" + propKey(nodeType, p), p.label);
    }

    public static String propDesc(String nodeType, Prop p) {
        return Tr.t("uim_pd_" + propKey(nodeType, p), p.desc == null ? "" : p.desc);
    }

    /** Label of an enum option / style name / size kind. The empty option reads "default". */
    public static String optionLabel(String option) {
        if (option == null || option.isEmpty()) return Tr.t("uim_default", "default");
        return Tr.t("uim_o_" + low(option), option);
    }

    // ------------------------------------------------------------------ theme

    public static String roleName(String role) {
        return Tr.t("uim_role_" + low(role), role);
    }

    public static String presetName(String preset) {
        return Tr.t("uim_preset_" + low(preset), preset.replace('_', ' '));
    }

    // ------------------------------------------------------------------ templates

    public static String templateName(String t) {
        String[] kit = UiKit.SCREENS.get(t);
        if (kit != null) return Tr.t("uim_kit_" + t, kit[0]);
        String[] info = UiTemplates.INFO.get(t);
        return Tr.t("uim_tpl_" + t, info == null ? t : info[0]);
    }

    public static String templateDesc(String t) {
        String[] kit = UiKit.SCREENS.get(t);
        if (kit != null) return Tr.t("uim_kit_" + t + "_d", kit[1]);
        String[] info = UiTemplates.INFO.get(t);
        return Tr.t("uim_tpl_" + t + "_d", info == null ? "" : info[1]);
    }

    // ------------------------------------------------------------------ messages of the editing code

    private static final Pattern CANNOT_HOLD = Pattern.compile("^(\\S+) '([^']*)' cannot hold children$");
    private static final Pattern TYPE_CANNOT_HOLD = Pattern.compile("^(\\S+) cannot hold children$");
    private static final Pattern MUST_ONE_OF = Pattern.compile("^'([^']*)' must be one of: (.*)$", Pattern.DOTALL);
    private static final Pattern MUST_BOOL = Pattern.compile("^'([^']*)' must be true or false$");
    private static final Pattern MUST_SIZE = Pattern.compile("^'([^']*)' must be a number \\(px\\), a percent like 50%, wrap or fill$");
    private static final Pattern NO_NODE = Pattern.compile("^no node '([^']*)'$");
    private static final Pattern NEEDS_NUMBER = Pattern.compile("^'([^']*)' needs a number, got '(.*)'$", Pattern.DOTALL);
    private static final Pattern NEEDS_N = Pattern.compile("^'([^']*)' needs 1, 2 or 4 numbers$");
    private static final Pattern MUST_COLOR = Pattern.compile("^'([^']*)' must be #rrggbb, #rrggbbaa or a role: (.*)$", Pattern.DOTALL);

    /**
     * Translates an error text produced by {@link UiOps}, {@link com.star4droid.template.ui.UINode#moveTo} or the registry's
     * {@code set} for a person. Unknown texts are returned as they are.
     */
    public static String err(String e) {
        if (e == null) return null;
        switch (e) {
            case "the root cannot move": return Tr.t("uim_e_root_move", "The root cannot be moved");
            case "the root cannot be moved": return Tr.t("uim_e_root_move", "The root cannot be moved");
            case "already first": return Tr.t("uim_e_first", "Already first");
            case "already last": return Tr.t("uim_e_last", "Already last");
            case "there is no node above to move into": return Tr.t("uim_e_nothing_above", "There is no element above to move into");
            case "already at the top level": return Tr.t("uim_e_top_level", "Already at the top level");
            case "cannot move a node into itself": return Tr.t("uim_e_into_itself", "An element cannot be moved into itself");
            case "the root cannot be wrapped": return Tr.t("uim_e_root_wrap", "The root cannot be wrapped");
            case "id cannot be empty": return Tr.t("uim_e_id_empty", "The id cannot be empty");
            case "no parent": return Tr.t("uim_e_no_parent", "No parent chosen");
            default: break;
        }
        Matcher m = CANNOT_HOLD.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_cannot_hold2", "%1$s '%2$s' cannot hold children"), widgetName(m.group(1)), m.group(2));
        m = TYPE_CANNOT_HOLD.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_cannot_hold", "%s cannot hold children"), widgetName(m.group(1)));
        m = MUST_ONE_OF.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_one_of", "'%1$s' must be one of: %2$s"), m.group(1), m.group(2));
        m = MUST_BOOL.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_bool", "'%s' must be on or off"), m.group(1));
        m = MUST_SIZE.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_size", "'%s' must be a number (px), a percent like 50%%, wrap or fill"), m.group(1));
        m = NO_NODE.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_no_node", "No element '%s'"), m.group(1));
        m = NEEDS_NUMBER.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_needs_number", "'%1$s' needs a number, got '%2$s'"), m.group(1), m.group(2));
        m = NEEDS_N.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_needs_n", "'%s' needs 1, 2 or 4 numbers"), m.group(1));
        m = MUST_COLOR.matcher(e);
        if (m.matches()) return String.format(Tr.t("uim_e_color", "'%1$s' must be #rrggbb, #rrggbbaa or a role: %2$s"), m.group(1), m.group(2));
        return e;
    }
}
