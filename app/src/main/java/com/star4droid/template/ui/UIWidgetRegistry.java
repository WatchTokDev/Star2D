package com.star4droid.template.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data about every widget type: display name, icon id (from the icon library), palette category, events and the list of property
 * descriptors (type, range, default, description). The UI Maker palette / property editor and the AI tool are driven from this
 * table, so adding a property here is enough to make it editable everywhere. Pure Java.
 *
 * Properties live in two places of the JSON node: {@code where == NODE} are fields of the node (id, anchor, w, alpha...),
 * {@code where == PROPS} are entries of {@code node.props}. {@link #set} / {@link #get} hide that difference.
 */
public final class UIWidgetRegistry {
    private UIWidgetRegistry() {}

    public enum Kind {NUMBER, INT, TEXT, MULTILINE, BOOL, ENUM, COLOR, IMAGE, SOUND, FONT, SIZE, ANCHOR, MARGIN, PADDING}

    public static final boolean NODE = true, PROPS = false;

    /** Describes one editable property. */
    public static final class Prop {
        public String key, label, group, desc, def = "";
        public Kind kind;
        public boolean where;
        public float min = Float.NaN, max = Float.NaN, step = Float.NaN;
        public String[] options = new String[0];
        /** True when a value is only shown/needed if another property is set (for editors that hide advanced fields). */
        public boolean advanced;
        /** True for the properties every widget type has (so translations can share one key per property). */
        public boolean common;

        Prop(String key, String label, Kind kind, boolean where, String def, String group, String desc) {
            this.key = key;
            this.label = label;
            this.kind = kind;
            this.where = where;
            this.def = def;
            this.group = group;
            this.desc = desc;
        }

        Prop range(float min, float max, float step) {
            this.min = min;
            this.max = max;
            this.step = step;
            return this;
        }

        Prop opts(String... o) {
            this.options = o;
            return this;
        }

        Prop adv() {
            this.advanced = true;
            return this;
        }
    }

    /** Describes one widget type. */
    public static final class Widget {
        public String type, name, icon, category, desc;
        public boolean container;
        /** Properties of this type only (common ones come from {@link #commonProps}). */
        public final ArrayList<Prop> props = new ArrayList<>();
        /** Event names an inner node of this type can have. */
        public String[] events = new String[0];
        /** Names of the {@code style} presets (empty = none). */
        public String[] styles = new String[0];
    }

    private static final LinkedHashMap<String, Widget> WIDGETS = new LinkedHashMap<>();
    private static final ArrayList<Prop> COMMON = new ArrayList<>();

    public static final String G_LAYOUT = "Layout", G_LOOK = "Appearance", G_BEHAVIOR = "Behavior", G_TEXT = "Text", G_VALUE = "Value", G_ADV = "Advanced";

    private static String[] withEmpty(String[] a) {
        String[] r = new String[a.length + 1];
        r[0] = "";
        System.arraycopy(a, 0, r, 1, a.length);
        return r;
    }

    private static Prop p(ArrayList<Prop> l, String key, String label, Kind k, boolean where, String def, String group, String desc) {
        Prop pr = new Prop(key, label, k, where, def, group, desc);
        pr.common = l == COMMON;
        l.add(pr);
        return pr;
    }

    private static Widget w(String type, String name, String icon, String category, boolean container, String desc, String[] events) {
        Widget wd = new Widget();
        wd.type = type;
        wd.name = name;
        wd.icon = icon;
        wd.category = category;
        wd.container = container;
        wd.desc = desc;
        wd.events = events;
        wd.styles = UIStyles.names(type);
        WIDGETS.put(type, wd);
        return wd;
    }

    private static final String[] EV_TOUCH = {"onClick", "onTouchStart", "onTouchEnd"};

    static {
        // ---------------------------------------------------------------- common
        ArrayList<Prop> c = COMMON;
        p(c, "id", "Id", Kind.TEXT, NODE, "", G_LAYOUT, "Unique name. Used in events (case \"id\") and in UIView.find(id). Letters, digits, _.");
        p(c, "anchor", "Anchor", Kind.ANCHOR, NODE, "topleft", G_LAYOUT, "Where the node sticks inside its parent.")
                .opts("topleft", "top", "topright", "left", "center", "right", "bottomleft", "bottom", "bottomright", "stretch", "stretchx", "stretchy");
        p(c, "margin", "Margin", Kind.MARGIN, NODE, "0", G_LAYOUT, "Distance from the parent edges: one number or [left, top, right, bottom].");
        p(c, "x", "Offset X", Kind.NUMBER, NODE, "0", G_LAYOUT, "Extra move to the right after anchoring (px).").range(-4000, 4000, 1);
        p(c, "y", "Offset Y", Kind.NUMBER, NODE, "0", G_LAYOUT, "Extra move UP after anchoring (px).").range(-4000, 4000, 1);
        p(c, "w", "Width", Kind.SIZE, NODE, "", G_LAYOUT, "px (\"120\"), percent (\"50%\"), \"wrap\" or \"fill\". Empty = default of the type.");
        p(c, "h", "Height", Kind.SIZE, NODE, "", G_LAYOUT, "px (\"120\"), percent (\"50%\"), \"wrap\" or \"fill\". Empty = default of the type.");
        p(c, "weight", "Weight", Kind.NUMBER, NODE, "0", G_LAYOUT, "Share of the free space inside a Linear parent (0 = none).").range(0, 20, 0.5f);
        p(c, "visible", "Visible", Kind.BOOL, NODE, "true", G_BEHAVIOR, "Hidden nodes are not drawn, take no room in layouts and get no touch.");
        p(c, "enabled", "Enabled", Kind.BOOL, NODE, "true", G_BEHAVIOR, "false: no events and drawn dimmed. Change at run time with ui.setEnabled(id, v).");
        p(c, "interactable", "Interactable", Kind.BOOL, PROPS, "true", G_BEHAVIOR, "false: visible but ignores touch and is dimmed (design time switch, same effect as enabled=false).");
        p(c, "alpha", "Opacity", Kind.NUMBER, NODE, "1", G_LOOK, "0 transparent .. 1 opaque (applies to the children too).").range(0, 1, 0.05f);
        p(c, "theme", "Theme", Kind.ENUM, PROPS, "", G_LOOK, "Look of this element and everything inside it. Empty = the theme of the whole UI (Theme tab).")
                .opts(withEmpty(UITheme.PRESETS));
        p(c, "tint", "Tint", Kind.COLOR, PROPS, "", G_LOOK, "Color multiplied over the whole node. Empty = none.").adv();
        p(c, "rotation", "Rotation", Kind.NUMBER, PROPS, "0", G_LOOK, "Degrees, counter-clockwise, around the pivot. Does not change the layout.").range(-360, 360, 1).adv();
        p(c, "scale", "Scale", Kind.NUMBER, PROPS, "1", G_LOOK, "Scale around the pivot. Does not change the layout.").range(0.1f, 5, 0.05f).adv();
        p(c, "pivotX", "Pivot X", Kind.NUMBER, PROPS, "0.5", G_LOOK, "0 left .. 1 right.").range(0, 1, 0.05f).adv();
        p(c, "pivotY", "Pivot Y", Kind.NUMBER, PROPS, "0.5", G_LOOK, "0 bottom .. 1 top.").range(0, 1, 0.05f).adv();
        p(c, "touchable", "Touchable", Kind.ENUM, NODE, "", G_BEHAVIOR, "enabled: gets touches (default for widgets) | disabled: node and children ignore touch | childrenOnly: only the children get touch.")
                .opts("", "enabled", "disabled", "childrenOnly");
        p(c, "tag", "Tag", Kind.TEXT, PROPS, "", G_ADV, "Free text you can read with ui.getTag(id) to group nodes.").adv();
        p(c, "tooltip", "Tooltip", Kind.TEXT, PROPS, "", G_ADV, "Text shown while the node is long pressed (optional).").adv();
        p(c, "clickSound", "Click sound", Kind.SOUND, PROPS, "", G_BEHAVIOR, "Sound file of the project (sounds/ folder) played on click, e.g. \"click.ogg\".").adv();
        p(c, "pressScale", "Press effect", Kind.BOOL, PROPS, "", G_BEHAVIOR, "Shrinks a little while pressed. Default true for Button, false for the others.");
        p(c, "action", "On click (no code)", Kind.TEXT, PROPS, "", G_BEHAVIOR, "Built-in actions, ';' between several: show:id, hide:id, toggle:id, page:id (show it, hide its siblings), tab:id (page + mark this tab), select (mark this button in its row), open:uiName, back, set:id.key=value.");
        p(c, "tabOn", "Selected tab style", Kind.TEXT, PROPS, "", G_ADV, "Button style while this tab is selected (tab: action). Empty = primary.").adv();
        p(c, "tabOff", "Tab style", Kind.TEXT, PROPS, "", G_ADV, "Button style while another tab is selected. Empty = ghost.").adv();

        // ---------------------------------------------------------------- containers
        Widget panel = w(UINode.PANEL, "Panel", "square-rounded", "Layout", true, "Box with a background (card, glass, outline...) that holds children placed with anchors.", EV_TOUCH);
        p(panel.props, "style", "Style", Kind.ENUM, PROPS, "card", G_LOOK, "Look of the panel.").opts(UIStyles.names("Panel"));
        p(panel.props, "radius", "Corner radius", Kind.NUMBER, PROPS, "", G_LOOK, "px. Empty = theme.").range(0, 200, 1);
        p(panel.props, "panelColor", "Color", Kind.COLOR, PROPS, "", G_LOOK, "Fill color (role or #rrggbb[aa]). Empty = style.");
        p(panel.props, "borderColor", "Border color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = theme.").adv();
        p(panel.props, "border", "Border width", Kind.NUMBER, PROPS, "", G_LOOK, "px. Empty = style.").range(0, 20, 1).adv();
        p(panel.props, "shadow", "Shadow", Kind.NUMBER, PROPS, "", G_LOOK, "px. Empty = style.").range(0, 30, 1).adv();
        p(panel.props, "image", "Background image", Kind.IMAGE, PROPS, "", G_LOOK, "Project image or .9.png that replaces the drawn background.");
        p(panel.props, "padding", "Padding", Kind.PADDING, PROPS, "0", G_LAYOUT, "Space between the border and the children: number or [l,t,r,b].");
        p(panel.props, "clip", "Clip children", Kind.BOOL, PROPS, "false", G_BEHAVIOR, "Hides what leaves the panel.").adv();

        Widget stack = w(UINode.STACK, "Stack", "layers", "Layout", true, "Invisible container: children are placed on top of each other with anchors (HUD layers).", EV_TOUCH);
        p(stack.props, "padding", "Padding", Kind.PADDING, PROPS, "0", G_LAYOUT, "Space around the children: number or [l,t,r,b].");
        p(stack.props, "bgColor", "Background", Kind.COLOR, PROPS, "", G_LOOK, "Optional fill color. Empty = transparent.").adv();
        p(stack.props, "clip", "Clip children", Kind.BOOL, PROPS, "false", G_BEHAVIOR, "Hides what leaves the box.").adv();

        Widget lin = w(UINode.LINEAR, "Linear layout", "view-list", "Layout", true, "Puts children in a row or a column with spacing, alignment and weights.", EV_TOUCH);
        p(lin.props, "orientation", "Direction", Kind.ENUM, PROPS, "vertical", G_LAYOUT, "vertical = column, horizontal = row.").opts("vertical", "horizontal");
        p(lin.props, "spacing", "Spacing", Kind.NUMBER, PROPS, "8", G_LAYOUT, "px between children.").range(0, 200, 1);
        p(lin.props, "padding", "Padding", Kind.PADDING, PROPS, "8", G_LAYOUT, "px inside the box: number or [l,t,r,b].");
        p(lin.props, "align", "Cross align", Kind.ENUM, PROPS, "start", G_LAYOUT, "Position across the direction.").opts("start", "center", "end", "stretch");
        p(lin.props, "gravity", "Main align", Kind.ENUM, PROPS, "start", G_LAYOUT, "Position along the direction when nothing has a weight.")
                .opts("start", "center", "end", "space_between", "space_around", "space_evenly");
        p(lin.props, "bgColor", "Background", Kind.COLOR, PROPS, "", G_LOOK, "Optional fill color. Empty = transparent.").adv();
        p(lin.props, "image", "Background image", Kind.IMAGE, PROPS, "", G_LOOK, "Optional picture / .9.png.").adv();
        p(lin.props, "clip", "Clip children", Kind.BOOL, PROPS, "false", G_BEHAVIOR, "Hides what leaves the box.").adv();

        Widget grid = w(UINode.GRID, "Grid", "view-grid", "Layout", true, "Children in rows and columns of equal width (inventory, level select).", EV_TOUCH);
        p(grid.props, "columns", "Columns", Kind.INT, PROPS, "3", G_LAYOUT, "Number of columns.").range(1, 20, 1);
        p(grid.props, "spacing", "Spacing", Kind.NUMBER, PROPS, "8", G_LAYOUT, "px between cells (hspacing / vspacing override one direction).").range(0, 200, 1);
        p(grid.props, "rowHeight", "Row height", Kind.NUMBER, PROPS, "0", G_LAYOUT, "px. 0 = tallest child of the row.").range(0, 1000, 1);
        p(grid.props, "padding", "Padding", Kind.PADDING, PROPS, "8", G_LAYOUT, "px inside the box.");
        p(grid.props, "align", "Cell align", Kind.ENUM, PROPS, "center", G_LAYOUT, "Position of a smaller child inside its cell.").opts("start", "center", "end", "stretch");
        p(grid.props, "bgColor", "Background", Kind.COLOR, PROPS, "", G_LOOK, "Optional fill color.").adv();

        Widget scroll = w(UINode.SCROLL, "ScrollView", "unfold-more-horizontal", "Layout", true,
                "Scrolls its content by dragging. Put ONE child (usually a Linear or Grid); vertical, horizontal or both.", new String[]{"onScroll"});
        p(scroll.props, "direction", "Direction", Kind.ENUM, PROPS, "vertical", G_LAYOUT, "Which way the content scrolls.").opts("vertical", "horizontal", "both");
        p(scroll.props, "scrollbar", "Scrollbar", Kind.BOOL, PROPS, "true", G_LOOK, "Shows a thin bar while scrolling.");
        p(scroll.props, "overscroll", "Bounce", Kind.BOOL, PROPS, "true", G_BEHAVIOR, "Content may be pulled past the end and springs back.");
        p(scroll.props, "fling", "Fling", Kind.BOOL, PROPS, "true", G_BEHAVIOR, "Keeps moving after a quick swipe.");
        p(scroll.props, "padding", "Padding", Kind.PADDING, PROPS, "0", G_LAYOUT, "px around the scrolled content.").adv();
        p(scroll.props, "bgColor", "Background", Kind.COLOR, PROPS, "", G_LOOK, "Optional fill color.").adv();

        // ---------------------------------------------------------------- display
        Widget label = w(UINode.LABEL, "Label", "text", "Display", false, "Text. Supports several lines, wrapping and Arabic shaping with the project RTL font.", EV_TOUCH);
        textProps(label.props, "Label");
        p(label.props, "wrap", "Wrap text", Kind.BOOL, PROPS, "false", G_TEXT, "Breaks long text into lines inside the width.");
        p(label.props, "textAlign", "Align", Kind.ENUM, PROPS, "left", G_TEXT, "Horizontal alignment of the lines.").opts("left", "center", "right");
        p(label.props, "vAlign", "Vertical align", Kind.ENUM, PROPS, "top", G_TEXT, "Where the text sits in the box when the box is taller than the text.").opts("top", "middle", "bottom");
        p(label.props, "autoFit", "Shrink to fit", Kind.BOOL, PROPS, "false", G_TEXT, "Makes the letters smaller until the text fits the box.").adv();
        p(label.props, "minFontSize", "Smallest size", Kind.NUMBER, PROPS, "12", G_TEXT, "px. Shrink to fit never goes below this.").range(6, 100, 1).adv();
        p(label.props, "ellipsis", "Ellipsis (...)", Kind.BOOL, PROPS, "false", G_TEXT, "Cuts a too long line with three dots.").adv();
        p(label.props, "uppercase", "UPPERCASE", Kind.BOOL, PROPS, "false", G_TEXT, "Shows the text in capital letters (the stored text is not changed).").adv();
        p(label.props, "textOpacity", "Text opacity", Kind.NUMBER, PROPS, "100", G_LOOK, "0 - 100 percent.").range(0, 100, 5).adv();
        p(label.props, "shadow", "Text shadow", Kind.BOOL, PROPS, "false", G_LOOK, "Dark drop shadow behind the letters.").adv();

        Widget image = w(UINode.IMAGE, "Image", "image", "Display", false, "Picture from the project's images folder (also .9.png).", EV_TOUCH);
        p(image.props, "image", "Image", Kind.IMAGE, PROPS, "", G_LOOK, "Project image path, e.g. \"/hero.png\".");
        p(image.props, "scaling", "Scaling", Kind.ENUM, PROPS, "fit", G_LOOK, "fit keeps the shape, fill covers the box, stretch distorts, none = real size.").opts("fit", "fill", "stretch", "none");
        p(image.props, "flipX", "Flip X", Kind.BOOL, PROPS, "false", G_LOOK, "Mirror left/right.").adv();
        p(image.props, "flipY", "Flip Y", Kind.BOOL, PROPS, "false", G_LOOK, "Mirror up/down.").adv();

        Widget icon = w(UINode.ICON, "Icon", "star", "Display", false, "Small one-color picture (from the icon library) painted with the color property.", EV_TOUCH);
        p(icon.props, "image", "Icon image", Kind.IMAGE, PROPS, "", G_LOOK, "Project image of the icon (white icons from the icon library).");
        p(icon.props, "color", "Color", Kind.COLOR, PROPS, "text", G_LOOK, "Role or #rrggbb.");

        Widget prog = w(UINode.PROGRESS, "Progress bar", "battery-medium", "Display", false, "Bar filled up to value/max (health, loading, XP).", new String[0]);
        valueProps(prog.props, false);
        p(prog.props, "style", "Style", Kind.ENUM, PROPS, "classic", G_LOOK, "Look of the bar.").opts(UIStyles.names("Progress"));
        barOverrides(prog.props, false);
        p(prog.props, "vertical", "Vertical", Kind.BOOL, PROPS, "false", G_LAYOUT, "Fills from bottom to top.");
        p(prog.props, "showText", "Show text", Kind.BOOL, PROPS, "false", G_TEXT, "Writes the value on the bar.");
        p(prog.props, "textFormat", "Text format", Kind.ENUM, PROPS, "percent", G_TEXT, "percent: 50% | value: 50 | fraction: 50/100").opts("percent", "value", "fraction");
        p(prog.props, "animate", "Smooth change", Kind.BOOL, PROPS, "true", G_BEHAVIOR, "Moves smoothly to a new value.");

        // ---------------------------------------------------------------- input
        Widget button = w(UINode.BUTTON, "Button", "button-a", "Input", false, "Pressable button with text and/or icon. Scales down a little while pressed.", EV_TOUCH);
        textProps(button.props, "Button");
        p(button.props, "style", "Style", Kind.ENUM, PROPS, "primary", G_LOOK, "Color variant or look.").opts(UIStyles.names("Button"));
        p(button.props, "shape", "Shape", Kind.ENUM, PROPS, "", G_LOOK, "Corner shape. Empty = theme.").opts("", "round", "pill", "square", "cut");
        p(button.props, "buttonColor", "Color", Kind.COLOR, PROPS, "", G_LOOK, "Fill color (role or #rrggbb[aa]). Empty = style.");
        p(button.props, "icon", "Icon image", Kind.IMAGE, PROPS, "", G_LOOK, "Small picture next to the text.");
        p(button.props, "iconPosition", "Icon position", Kind.ENUM, PROPS, "left", G_LOOK, "").opts("left", "right", "top");
        p(button.props, "image", "Image", Kind.IMAGE, PROPS, "", G_LOOK, "Picture / .9.png replacing the drawn button.");
        p(button.props, "pressedImage", "Pressed image", Kind.IMAGE, PROPS, "", G_LOOK, "Picture shown while pressed. Empty = name_pressed.9.png if it exists, else the normal one.");
        p(button.props, "radius", "Corner radius", Kind.NUMBER, PROPS, "", G_LOOK, "px. Empty = theme.").range(0, 200, 1).adv();
        p(button.props, "callClick", "Call on click", Kind.TEXT, PROPS, "", G_BEHAVIOR, "Calls a method of a scene element when clicked (set with the editor).").adv();
        p(button.props, "callStart", "Call on touch start", Kind.TEXT, PROPS, "", G_BEHAVIOR, "Calls a method of a scene element when the touch starts.").adv();
        p(button.props, "callEnd", "Call on touch end", Kind.TEXT, PROPS, "", G_BEHAVIOR, "Calls a method of a scene element when the touch ends.").adv();
        p(button.props, "pressScaleAmount", "Press scale", Kind.NUMBER, PROPS, "0.94", G_BEHAVIOR, "Size while pressed (1 = no effect).").range(0.5f, 1, 0.01f).adv();

        Widget slider = w(UINode.SLIDER, "Slider", "tune", "Input", false, "Drag the knob to choose a number between min and max.", new String[]{"onChange"});
        valueProps(slider.props, true);
        p(slider.props, "style", "Style", Kind.ENUM, PROPS, "classic", G_LOOK, "Look of track, fill and knob.").opts(UIStyles.names("Slider"));
        barOverrides(slider.props, true);
        p(slider.props, "step", "Step", Kind.NUMBER, PROPS, "0", G_VALUE, "Size of one step. 0 = smooth.").range(0, 1000, 0.01f);
        p(slider.props, "snapToStep", "Snap to step", Kind.BOOL, PROPS, "true", G_VALUE, "Knob jumps from step to step. false: only the value is rounded.");
        p(slider.props, "vertical", "Vertical", Kind.BOOL, PROPS, "false", G_LAYOUT, "Bottom = min, top = max. Give it a tall size.");
        p(slider.props, "showValueLabel", "Show value", Kind.BOOL, PROPS, "false", G_TEXT, "Writes the value above the knob.");
        p(slider.props, "decimals", "Decimals", Kind.INT, PROPS, "0", G_TEXT, "Digits after the point in the value label.").range(0, 6, 1).adv();
        p(slider.props, "changeMode", "onChange fires", Kind.ENUM, PROPS, "continuous", G_BEHAVIOR, "continuous: while dragging | release: only when the finger lifts.").opts("continuous", "release");

        Widget cb = w(UINode.CHECKBOX, "Checkbox", "checkbox-marked", "Input", false, "Box with a tick and a text.", new String[]{"onChange", "onClick"});
        toggleProps(cb.props, "Checkbox");
        Widget radio = w(UINode.RADIO, "Radio button", "radiobox-marked", "Input", false, "Round choice; only one radio of the same group is on.", new String[]{"onChange", "onClick"});
        toggleProps(radio.props, "Radio");
        p(radio.props, "group", "Group", Kind.TEXT, PROPS, "group1", G_VALUE, "Radios with the same group name exclude each other (the group is the whole .ui).");

        Widget sw = w(UINode.SWITCH, "Switch", "toggle-switch", "Input", false, "On/off toggle.", new String[]{"onChange", "onClick"});
        p(sw.props, "checked", "On", Kind.BOOL, PROPS, "false", G_VALUE, "Current state.");
        p(sw.props, "style", "Style", Kind.ENUM, PROPS, "classic", G_LOOK, "Look of the switch.").opts(UIStyles.names("Switch"));
        p(sw.props, "trackOnColor", "On color", Kind.COLOR, PROPS, "", G_LOOK, "Track color when on. Empty = style.");
        p(sw.props, "trackOffColor", "Off color", Kind.COLOR, PROPS, "", G_LOOK, "Track color when off. Empty = style.");
        p(sw.props, "knobColor", "Knob color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.").adv();
        p(sw.props, "knobScale", "Knob size", Kind.NUMBER, PROPS, "", G_LOOK, "Knob diameter as part of the height (0.5 .. 1).").range(0.3f, 1, 0.05f).adv();

        Widget tf = w(UINode.TEXTFIELD, "Text field", "keyboard", "Input", false, "Single line text input with the on-screen keyboard.", new String[]{"onChange", "onSubmit"});
        p(tf.props, "text", "Text", Kind.TEXT, PROPS, "", G_VALUE, "Current content.");
        p(tf.props, "hint", "Hint", Kind.TEXT, PROPS, "", G_TEXT, "Grey text shown while empty.");
        p(tf.props, "style", "Style", Kind.ENUM, PROPS, "classic", G_LOOK, "Look of the box.").opts(UIStyles.names("TextField"));
        p(tf.props, "password", "Password", Kind.BOOL, PROPS, "false", G_BEHAVIOR, "Shows dots instead of letters.");
        p(tf.props, "numeric", "Numbers only", Kind.BOOL, PROPS, "false", G_BEHAVIOR, "Only digits, point and minus.");
        p(tf.props, "maxLength", "Max length", Kind.INT, PROPS, "0", G_BEHAVIOR, "0 = unlimited.").range(0, 1000, 1);
        p(tf.props, "readOnly", "Read only", Kind.BOOL, PROPS, "false", G_BEHAVIOR, "Shows the text but cannot be edited.").adv();
        p(tf.props, "fontSize", "Font size", Kind.NUMBER, PROPS, "", G_TEXT, "px. Empty = theme.").range(8, 200, 1);
        p(tf.props, "color", "Text color", Kind.COLOR, PROPS, "", G_TEXT, "Role or #rrggbb. Empty = theme.");
        p(tf.props, "font", "Font", Kind.FONT, PROPS, "", G_TEXT, "Font json made by the font creator. Empty = theme / default.").adv();
        p(tf.props, "bgColor", "Background", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.").adv();
        p(tf.props, "borderColor", "Border color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.").adv();
        p(tf.props, "radius", "Corner radius", Kind.NUMBER, PROPS, "", G_LOOK, "px. Empty = style.").range(0, 100, 1).adv();
    }

    private static void textProps(ArrayList<Prop> l, String type) {
        p(l, "text", "Text", Kind.MULTILINE, PROPS, type, G_TEXT, "The words. \\n makes a new line.");
        p(l, "fontSize", "Font size", Kind.NUMBER, PROPS, "", G_TEXT, "px. Empty = theme (28).").range(8, 200, 1);
        p(l, "color", "Text color", Kind.COLOR, PROPS, "", G_TEXT, "Role (text, primary...) or #rrggbb. Empty = theme.");
        p(l, "font", "Font", Kind.FONT, PROPS, "", G_TEXT, "Font json made by the font creator. Empty = theme / default font.").adv();
    }

    private static void valueProps(ArrayList<Prop> l, boolean slider) {
        p(l, "value", "Value", Kind.NUMBER, PROPS, slider ? "0.5" : "50", G_VALUE, "Current value.");
        p(l, "min", "Min", Kind.NUMBER, PROPS, "0", G_VALUE, "Smallest value.");
        p(l, "max", "Max", Kind.NUMBER, PROPS, slider ? "1" : "100", G_VALUE, "Largest value.");
    }

    private static void barOverrides(ArrayList<Prop> l, boolean knob) {
        p(l, "trackColor", "Track color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.");
        p(l, "fillColor", "Fill color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.");
        p(l, "trackHeight", "Track thickness", Kind.NUMBER, PROPS, "", G_LOOK, "px. Empty = style.").range(1, 100, 1);
        p(l, "trackImage", "Track image", Kind.IMAGE, PROPS, "", G_LOOK, "Picture / .9.png for the empty part.").adv();
        p(l, "fillImage", "Fill image", Kind.IMAGE, PROPS, "", G_LOOK, "Picture / .9.png for the filled part.").adv();
        if (knob) {
            p(l, "knobColor", "Knob color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.");
            p(l, "knobSize", "Knob size", Kind.NUMBER, PROPS, "", G_LOOK, "px diameter. Empty = style.").range(4, 150, 1);
            p(l, "knobImage", "Knob image", Kind.IMAGE, PROPS, "", G_LOOK, "Picture for the knob.").adv();
        }
    }

    private static void toggleProps(ArrayList<Prop> l, String type) {
        p(l, "text", "Text", Kind.TEXT, PROPS, type, G_TEXT, "Words next to the box.");
        p(l, "checked", "Checked", Kind.BOOL, PROPS, "false", G_VALUE, "Current state.");
        p(l, "style", "Style", Kind.ENUM, PROPS, "classic", G_LOOK, "Look of the box.").opts(UIStyles.names(type));
        p(l, "boxColor", "Box color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.");
        p(l, "checkColor", "Check color", Kind.COLOR, PROPS, "", G_LOOK, "Empty = style.");
        p(l, "boxSize", "Box size", Kind.NUMBER, PROPS, "", G_LOOK, "px. Empty = from the font size.").range(12, 120, 1).adv();
        p(l, "fontSize", "Font size", Kind.NUMBER, PROPS, "", G_TEXT, "px. Empty = theme.").range(8, 200, 1);
        p(l, "color", "Text color", Kind.COLOR, PROPS, "", G_TEXT, "Role or #rrggbb. Empty = theme.");
        p(l, "font", "Font", Kind.FONT, PROPS, "", G_TEXT, "Font json. Empty = theme / default.").adv();
    }

    // ------------------------------------------------------------------ queries

    /** Types in palette order. */
    public static List<String> types() {
        return new ArrayList<>(WIDGETS.keySet());
    }

    public static Widget get(String type) {
        String t = UINode.canonicalType(type);
        return t == null ? null : WIDGETS.get(t);
    }

    public static List<Widget> all() {
        return new ArrayList<>(WIDGETS.values());
    }

    /** Palette categories in order: Layout, Display, Input. */
    public static List<String> categories() {
        ArrayList<String> l = new ArrayList<>();
        for (Widget w : WIDGETS.values()) if (!l.contains(w.category)) l.add(w.category);
        return l;
    }

    public static List<Prop> commonProps() {
        return new ArrayList<>(COMMON);
    }

    /** Type specific properties followed by the common ones. */
    public static List<Prop> propsFor(String type) {
        ArrayList<Prop> l = new ArrayList<>();
        Widget w = get(type);
        if (w != null) l.addAll(w.props);
        l.addAll(COMMON);
        return l;
    }

    public static Prop find(String type, String key) {
        for (Prop pr : propsFor(type)) if (pr.key.equals(key)) return pr;
        return null;
    }

    /** Event names available for a node (the type's own). */
    public static String[] eventsOf(UINode n) {
        Widget w = get(n.type);
        return w == null ? new String[0] : w.events;
    }

    // ------------------------------------------------------------------ get / set by key (editors and the AI tool)

    /** Current value of a property as text ("" when unset). */
    public static String get(UINode n, String key) {
        switch (key) {
            case "id": return n.id;
            case "anchor": return n.anchor;
            case "margin": return n.ml == n.mt && n.mt == n.mr && n.mr == n.mb ? UINode.trim(n.ml) : n.ml + "," + n.mt + "," + n.mr + "," + n.mb;
            case "x": return UINode.trim(n.x);
            case "y": return UINode.trim(n.y);
            case "w": return n.w == null ? "" : n.w;
            case "h": return n.h == null ? "" : n.h;
            case "weight": return UINode.trim(n.weight);
            case "visible": return String.valueOf(n.visible);
            case "enabled": return String.valueOf(n.enabled);
            case "alpha": return UINode.trim(n.alpha);
            case "touchable": return n.touchable == null ? "" : n.touchable;
            default: return n.has(key) ? n.str(key, "") : "";
        }
    }

    /**
     * Sets a property from text, checking the value against its descriptor.
     * @return null when applied, otherwise a message saying what is wrong (and what is allowed)
     */
    public static String set(UINode n, String key, String value) {
        Prop pr = find(n.type, key);
        if (pr == null) return "unknown property '" + key + "' for " + n.type + ". Known: " + names(n.type);
        if (value == null) value = "";
        value = value.trim();
        try {
            switch (pr.kind) {
                case NUMBER: case INT: {
                    if (value.isEmpty()) { unset(n, pr); return null; }
                    float v = Float.parseFloat(value);
                    if (pr.kind == Kind.INT) v = Math.round(v);
                    if (!Float.isNaN(pr.min) && v < pr.min) v = pr.min;
                    if (!Float.isNaN(pr.max) && v > pr.max) v = pr.max;
                    store(n, pr, pr.kind == Kind.INT ? (Object) (int) v : (Object) v);
                    return null;
                }
                case BOOL: {
                    if (value.isEmpty()) { unset(n, pr); return null; }
                    String l = value.toLowerCase(Locale.ROOT);
                    if (!(l.equals("true") || l.equals("false") || l.equals("1") || l.equals("0") || l.equals("yes") || l.equals("no")))
                        return "'" + key + "' must be true or false";
                    store(n, pr, l.equals("true") || l.equals("1") || l.equals("yes"));
                    return null;
                }
                case ENUM: case ANCHOR: {
                    if (value.isEmpty() && Arrays.asList(pr.options).contains("")) { unset(n, pr); return null; }
                    for (String o : pr.options) if (o.equalsIgnoreCase(value)) { store(n, pr, o); return null; }
                    return "'" + key + "' must be one of: " + String.join(", ", pr.options);
                }
                case SIZE: {
                    if (value.isEmpty()) { unset(n, pr); return null; }
                    UINode.Size s = UINode.parseSize(value);
                    if (s.kind == UINode.AUTO) return "'" + key + "' must be a number (px), a percent like 50%, wrap or fill";
                    store(n, pr, s.kind == UINode.PX ? UINode.trim(s.v) : s.toString());
                    return null;
                }
                case MARGIN: {
                    String[] parts = value.isEmpty() ? new String[]{"0"} : value.replace("[", "").replace("]", "").split("[,\\s]+");
                    float[] f = new float[parts.length];
                    for (int i = 0; i < parts.length; i++) f[i] = Float.parseFloat(parts[i]);
                    if (f.length == 1) n.margin(f[0]);
                    else if (f.length == 2) n.margin(f[0], f[1], f[0], f[1]);
                    else if (f.length >= 4) n.margin(f[0], f[1], f[2], f[3]);
                    else return "'margin' needs 1, 2 or 4 numbers";
                    return null;
                }
                case PADDING: {
                    if (value.isEmpty()) { n.props.remove(key); return null; }
                    String[] parts = value.replace("[", "").replace("]", "").split("[,\\s]+");
                    if (parts.length == 1) n.props.addProperty(key, Float.parseFloat(parts[0]));
                    else if (parts.length == 2 || parts.length == 4) {
                        JsonArray a = new JsonArray();
                        for (String s : parts) a.add(Float.parseFloat(s));
                        n.props.add(key, a);
                    } else return "'" + key + "' needs 1, 2 or 4 numbers";
                    return null;
                }
                case COLOR: {
                    if (value.isEmpty()) { unset(n, pr); return null; }
                    boolean hex = value.matches("#?[0-9a-fA-F]{3}|#?[0-9a-fA-F]{6}|#?[0-9a-fA-F]{8}");
                    boolean role = false;
                    for (String r : UITheme.ROLES) if (r.equalsIgnoreCase(value)) { value = r; role = true; }
                    if (!hex && !role) return "'" + key + "' must be #rrggbb, #rrggbbaa or a role: " + String.join(", ", UITheme.ROLES);
                    if (hex && !value.startsWith("#")) value = "#" + value;
                    store(n, pr, value);
                    return null;
                }
                default: {
                    if (key.equals("id")) {
                        if (value.isEmpty()) return "id cannot be empty";
                        n.id = UIDocument.cleanId(value);
                        return null;
                    }
                    if (value.isEmpty() && !pr.key.equals("text")) { unset(n, pr); return null; }
                    store(n, pr, value);
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return "'" + key + "' needs a number, got '" + value + "'";
        }
    }

    private static String names(String type) {
        StringBuilder sb = new StringBuilder();
        for (Prop pr : propsFor(type)) sb.append(sb.length() == 0 ? "" : ", ").append(pr.key);
        return sb.toString();
    }

    private static void unset(UINode n, Prop pr) {
        if (pr.where == NODE) {
            switch (pr.key) {
                case "w": n.w = null; break;
                case "h": n.h = null; break;
                case "touchable": n.touchable = null; break;
                case "x": n.x = 0; break;
                case "y": n.y = 0; break;
                case "weight": n.weight = 0; break;
                case "alpha": n.alpha = 1; break;
                case "visible": n.visible = true; break;
                case "enabled": n.enabled = true; break;
                case "anchor": n.anchor = "topleft"; break;
                default: break;
            }
        } else n.props.remove(pr.key);
    }

    private static void store(UINode n, Prop pr, Object v) {
        if (pr.where == NODE) {
            switch (pr.key) {
                case "anchor": n.anchor = v.toString(); break;
                case "x": n.x = ((Number) v).floatValue(); break;
                case "y": n.y = ((Number) v).floatValue(); break;
                case "w": n.w = v.toString(); break;
                case "h": n.h = v.toString(); break;
                case "weight": n.weight = ((Number) v).floatValue(); break;
                case "alpha": n.alpha = ((Number) v).floatValue(); break;
                case "visible": n.visible = (Boolean) v; break;
                case "enabled": n.enabled = (Boolean) v; break;
                case "touchable": n.touchable = v.toString(); break;
                default: break;
            }
        } else n.set(pr.key, v);
    }

    // ------------------------------------------------------------------ description for docs / AI

    /** Machine readable catalog of all widgets (used by docs and by the AI tool's "describe" action). */
    public static JsonObject describe() {
        JsonObject root = new JsonObject();
        JsonArray types = new JsonArray();
        for (Widget w : WIDGETS.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("type", w.type);
            o.addProperty("name", w.name);
            o.addProperty("icon", w.icon);
            o.addProperty("category", w.category);
            o.addProperty("container", w.container);
            o.addProperty("description", w.desc);
            JsonArray ev = new JsonArray();
            for (String e : w.events) ev.add(e);
            o.add("events", ev);
            JsonArray st = new JsonArray();
            for (String s : w.styles) st.add(s);
            o.add("styles", st);
            JsonArray ps = new JsonArray();
            for (Prop pr : w.props) ps.add(propJson(pr));
            o.add("props", ps);
            types.add(o);
        }
        root.add("types", types);
        JsonArray common = new JsonArray();
        for (Prop pr : COMMON) common.add(propJson(pr));
        root.add("common", common);
        JsonArray presets = new JsonArray();
        for (String s : UITheme.PRESETS) presets.add(s);
        root.add("themePresets", presets);
        return root;
    }

    private static JsonObject propJson(Prop pr) {
        JsonObject o = new JsonObject();
        o.addProperty("key", pr.key);
        o.addProperty("label", pr.label);
        o.addProperty("kind", pr.kind.name().toLowerCase(Locale.ROOT));
        o.addProperty("where", pr.where == NODE ? "node" : "props");
        o.addProperty("default", pr.def);
        o.addProperty("group", pr.group);
        o.addProperty("description", pr.desc);
        if (!Float.isNaN(pr.min)) o.addProperty("min", pr.min);
        if (!Float.isNaN(pr.max)) o.addProperty("max", pr.max);
        if (!Float.isNaN(pr.step)) o.addProperty("step", pr.step);
        if (pr.options.length > 0) {
            JsonArray a = new JsonArray();
            for (String s : pr.options) a.add(s);
            o.add("options", a);
        }
        if (pr.advanced) o.addProperty("advanced", true);
        return o;
    }
}
