package com.star4droid.star2d.ui.uimaker;

import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UINode;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Ready-made screens for the UI Maker's "Templates" row and the AI tool (`ui create {template}`). Each one is a single container that
 * can be inserted into any document; all of them work in portrait and landscape because they only use anchors, margins and layouts.
 * Pure Java.
 */
public final class UiTemplates {
    private UiTemplates() {}

    /** name -> {title, description}. */
    public static final LinkedHashMap<String, String[]> INFO = new LinkedHashMap<>();

    static {
        INFO.put("hud", new String[]{"HUD", "Health bar top left, pause button top right with the score under it."});
        INFO.put("pause_menu", new String[]{"Pause menu", "Dimmed screen with a card: Resume, Restart, Settings, Quit."});
        INFO.put("settings_panel", new String[]{"Settings panel", "Music and sound sliders, vibration switch, hints checkbox, Back button."});
        INFO.put("dialog_box", new String[]{"Dialog box", "Title, message and Cancel / OK buttons in a centered card."});
        INFO.put("controls", new String[]{"Controls", "Left / right buttons at the bottom left and jump / fire buttons at the bottom right."});
        INFO.put("main_menu", new String[]{"Main menu", "Big title with Play, Settings and Quit buttons."});
        INFO.put("game_over", new String[]{"Game over", "Dimmed screen with the score and Retry / Menu buttons."});
        INFO.put("level_select", new String[]{"Level select", "Title and a scrolling grid of 12 level buttons."});
    }

    public static ArrayList<String> names() {
        return new ArrayList<>(INFO.keySet());
    }

    public static boolean exists(String name) {
        return name != null && INFO.containsKey(name.toLowerCase().replace(' ', '_').replace('-', '_'));
    }

    /** A new document that contains the template (the container stretches over the whole screen). */
    public static UIDocument document(String uiName, String template, String themePreset) {
        UIDocument d = UiOps.newDocument(uiName, 1280, 720);
        if (themePreset != null && !themePreset.isEmpty()) d.theme.preset = themePreset;
        if (template != null && !template.isEmpty()) insert(d, d.root, template);
        return d;
    }

    /** Inserts the template as the last child of {@code parent} (ids made unique). Returns the node or null for an unknown name. */
    public static UINode insert(UIDocument doc, UINode parent, String template) {
        UINode n = make(template);
        if (n == null) return null;
        if (parent == null || !parent.isContainer()) parent = doc.root;
        doc.makeIdsUnique(n);
        parent.addChild(n);
        return n;
    }

    // ------------------------------------------------------------------ helpers

    private static UINode node(String type, String id) {
        return UINode.create(type, id);
    }

    private static UINode label(String id, String text, int size) {
        UINode n = node(UINode.LABEL, id);
        n.set("text", text);
        n.set("fontSize", size);
        return n;
    }

    private static UINode button(String id, String text, String style, String w, String h) {
        UINode n = node(UINode.BUTTON, id);
        n.set("text", text);
        n.set("style", style);
        n.w = w;
        n.h = h;
        return n;
    }

    private static UINode stretch(String id, String type) {
        UINode n = node(type, id);
        n.anchor = "stretch";
        return n;
    }

    private static UINode dim(String id) {
        UINode n = stretch(id, UINode.STACK);
        n.set("bgColor", "#000000aa");
        n.touchable = "enabled"; // swallows touches behind the menu
        return n;
    }

    private static UINode card(String id, String w, String h) {
        UINode n = node(UINode.PANEL, id);
        n.anchor = "center";
        n.w = w;
        n.h = h;
        return n;
    }

    private static UINode column(String id, int spacing, int pad) {
        UINode n = node(UINode.LINEAR, id);
        n.anchor = "stretch";
        n.set("orientation", "vertical");
        n.set("spacing", spacing);
        n.set("padding", pad);
        n.set("align", "center");
        return n;
    }

    private static UINode row(String id, int spacing) {
        UINode n = node(UINode.LINEAR, id);
        n.set("orientation", "horizontal");
        n.set("spacing", spacing);
        n.set("padding", 0);
        n.set("align", "center");
        n.w = "fill";
        n.h = "wrap";
        return n;
    }

    // ------------------------------------------------------------------ templates

    /** Builds a template container, or null for an unknown name. */
    public static UINode make(String name) {
        if (name == null) return null;
        switch (name.toLowerCase().trim().replace(' ', '_').replace('-', '_')) {
            case "hud": return hud();
            case "pause_menu": case "pause": return pause();
            case "settings_panel": case "settings": return settings();
            case "dialog_box": case "dialog": return dialog();
            case "controls": case "joystick": case "joystick_actions": return controls();
            case "main_menu": case "menu": return mainMenu();
            case "game_over": return gameOver();
            case "level_select": case "levels": return levels();
            default: return null;
        }
    }

    private static UINode hud() {
        UINode hud = stretch("hud", UINode.STACK);
        hud.set("padding", 16);
        UINode hp = node(UINode.LINEAR, "health_box");
        hp.anchor = "topleft";
        hp.set("orientation", "horizontal");
        hp.set("spacing", 10);
        hp.set("padding", 0);
        hp.set("align", "center");
        hp.w = "wrap";
        hp.h = "wrap";
        hp.addChild(label("health_label", "HP", 28));
        UINode bar = node(UINode.PROGRESS, "health");
        bar.set("value", 100);
        bar.set("max", 100);
        bar.set("showText", true);
        bar.set("textFormat", "value");
        bar.w = "220";
        bar.h = "32";
        hp.addChild(bar);
        hud.addChild(hp);
        UINode score = label("score", "Score: 0", 34);
        score.anchor = "topright";
        score.mt = 92;
        hud.addChild(score);
        UINode pause = button("pause", "II", "secondary", "72", "72");
        pause.anchor = "topright";
        hud.addChild(pause);
        return hud;
    }

    private static UINode pause() {
        UINode dim = dim("pause_menu");
        UINode card = card("pause_card", "460", "520");
        UINode col = column("pause_column", 16, 28);
        col.addChild(label("pause_title", "Paused", 48));
        col.addChild(button("resume", "Resume", "primary", "fill", "76"));
        col.addChild(button("restart", "Restart", "secondary", "fill", "76"));
        col.addChild(button("settings", "Settings", "secondary", "fill", "76"));
        col.addChild(button("quit", "Quit", "danger", "fill", "76"));
        card.addChild(col);
        dim.addChild(card);
        return dim;
    }

    private static UINode settings() {
        UINode card = card("settings_panel", "560", "520");
        UINode col = column("settings_column", 18, 28);
        col.set("align", "stretch");
        col.addChild(label("settings_title", "Settings", 44));

        UINode music = row("music_row", 14);
        music.addChild(label("music_label", "Music", 28));
        UINode ms = node(UINode.SLIDER, "music");
        ms.w = "fill";
        ms.h = "48";
        ms.weight = 1;
        music.addChild(ms);
        col.addChild(music);

        UINode sound = row("sound_row", 14);
        sound.addChild(label("sound_label", "Sound", 28));
        UINode ss = node(UINode.SLIDER, "sound");
        ss.w = "fill";
        ss.h = "48";
        ss.weight = 1;
        sound.addChild(ss);
        col.addChild(sound);

        UINode vib = row("vibration_row", 14);
        UINode sw = node(UINode.SWITCH, "vibration");
        sw.set("checked", true);
        sw.w = "84";
        sw.h = "48";
        vib.addChild(sw);
        vib.addChild(label("vibration_label", "Vibration", 28));
        col.addChild(vib);

        UINode hints = node(UINode.CHECKBOX, "hints");
        hints.set("text", "Show hints");
        hints.set("checked", true);
        col.addChild(hints);

        col.addChild(button("back", "Back", "primary", "fill", "70"));
        card.addChild(col);
        return card;
    }

    private static UINode dialog() {
        UINode dim = dim("dialog_box");
        UINode card = card("dialog_card", "520", "320");
        UINode col = column("dialog_column", 16, 28);
        col.set("align", "stretch");
        UINode title = label("dialog_title", "Are you sure?", 40);
        title.set("textAlign", "center");
        title.w = "fill";
        col.addChild(title);
        UINode msg = label("dialog_message", "This cannot be undone.", 28);
        msg.set("wrap", true);
        msg.set("textAlign", "center");
        msg.w = "fill";
        msg.weight = 1;
        col.addChild(msg);
        UINode buttons = row("dialog_buttons", 16);
        UINode no = button("cancel", "Cancel", "secondary", "fill", "70");
        no.weight = 1;
        UINode yes = button("ok", "OK", "primary", "fill", "70");
        yes.weight = 1;
        buttons.addChild(no);
        buttons.addChild(yes);
        col.addChild(buttons);
        card.addChild(col);
        dim.addChild(card);
        return dim;
    }

    private static UINode controls() {
        UINode st = stretch("controls", UINode.STACK);
        st.set("padding", 24);
        UINode dpad = node(UINode.LINEAR, "move_buttons");
        dpad.anchor = "bottomleft";
        dpad.set("orientation", "horizontal");
        dpad.set("spacing", 20);
        dpad.set("padding", 0);
        dpad.w = "wrap";
        dpad.h = "wrap";
        dpad.addChild(button("left", "<", "secondary", "110", "110"));
        dpad.addChild(button("right", ">", "secondary", "110", "110"));
        st.addChild(dpad);
        UINode act = node(UINode.LINEAR, "action_buttons");
        act.anchor = "bottomright";
        act.set("orientation", "horizontal");
        act.set("spacing", 20);
        act.set("padding", 0);
        act.set("align", "end");
        act.w = "wrap";
        act.h = "wrap";
        act.addChild(button("fire", "B", "accent", "110", "110"));
        act.addChild(button("jump", "A", "primary", "130", "130"));
        st.addChild(act);
        return st;
    }

    private static UINode mainMenu() {
        UINode st = stretch("main_menu", UINode.STACK);
        UINode col = node(UINode.LINEAR, "menu_column");
        col.anchor = "center";
        col.set("orientation", "vertical");
        col.set("spacing", 20);
        col.set("padding", 0);
        col.set("align", "center");
        col.w = "420";
        col.h = "wrap";
        UINode title = label("game_title", "My Game", 72);
        title.set("shadow", true);
        col.addChild(title);
        col.addChild(button("play", "Play", "primary", "fill", "84"));
        col.addChild(button("options", "Settings", "secondary", "fill", "84"));
        col.addChild(button("exit", "Quit", "danger", "fill", "84"));
        st.addChild(col);
        return st;
    }

    private static UINode gameOver() {
        UINode dim = dim("game_over");
        UINode col = node(UINode.LINEAR, "over_column");
        col.anchor = "center";
        col.set("orientation", "vertical");
        col.set("spacing", 20);
        col.set("padding", 0);
        col.set("align", "center");
        col.w = "420";
        col.h = "wrap";
        col.addChild(label("over_title", "Game Over", 72));
        col.addChild(label("final_score", "Score: 0", 40));
        col.addChild(button("retry", "Retry", "primary", "fill", "80"));
        col.addChild(button("to_menu", "Menu", "secondary", "fill", "80"));
        dim.addChild(col);
        return dim;
    }

    private static UINode levels() {
        UINode st = stretch("level_select", UINode.STACK);
        st.set("padding", 24);
        UINode title = label("levels_title", "Select level", 48);
        title.anchor = "top";
        st.addChild(title);
        UINode sc = node(UINode.SCROLL, "levels_scroll");
        sc.anchor = "stretch";
        sc.mt = 90;
        sc.set("direction", "vertical");
        UINode grid = node(UINode.GRID, "levels_grid");
        grid.set("columns", 4);
        grid.set("spacing", 16);
        grid.set("padding", 8);
        grid.w = "fill";
        grid.h = "wrap";
        for (int i = 1; i <= 12; i++) {
            UINode b = button("level" + i, String.valueOf(i), i <= 3 ? "success" : "secondary", "fill", "96");
            grid.addChild(b);
        }
        sc.addChild(grid);
        st.addChild(sc);
        return st;
    }
}
