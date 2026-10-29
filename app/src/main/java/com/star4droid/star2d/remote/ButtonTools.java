package com.star4droid.star2d.remote;

import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.buttons.ButtonPresets;
import com.star4droid.star2d.buttons.ButtonSpec;
import com.star4droid.star2d.buttons.ButtonStore;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.util.ArrayList;

/** The `buttons` tool: list the looks, create/replace a 9-patch button image, read back the settings of a saved one. */
final class ButtonTools {
    private ButtonTools() {}

    private static final String[] TOOL_KEYS = {"action", "name", "replace", "pressed", "with_pressed", "preview"};

    static ToolResult buttons(JsonObject a) throws Exception {
        String action = CoreTools.s(a, "action");
        if (action.isEmpty()) action = CoreTools.s(a, "name").isEmpty() ? "list" : "create";
        switch (action) {
            case "list": return list();
            case "get": return get(a);
            case "create": return create(a);
            default: return ToolResult.error("Unknown action '" + action + "'. Use list | create | get.");
        }
    }

    private static ToolResult list() {
        JsonObject o = ButtonPresets.catalog();
        JsonArray keys = new JsonArray();
        for (String k : ButtonSpec.KEYS) keys.add(k);
        o.add("params", keys);
        o.addProperty("how", "create {name, preset, color?, shape?, ...any param}. Anything you do not pass keeps the preset default. Colors: #RRGGBB or a name. "
                + "Stretchable shapes (rect, rounded, pill, chamfer) are saved as name.9.png: put it in a BOX image and give the BOX ANY width/height (keep it >= min_size). "
                + "Fixed shapes (circle, blob, triangle_*) are saved as name.png: keep the element aspect 1:1. Buttons have NO text: put a TEXT element on top.");
        return ToolResult.text(o.toString());
    }

    private static ToolResult get(JsonObject a) throws Exception {
        String name = CoreTools.s(a, "name");
        String err = ButtonStore.checkName(name);
        if (err != null) return ToolResult.error(err);
        File images = imagesDir();
        File f = ButtonStore.existing(images, name);
        if (f == null) return ToolResult.error("No button image named '" + name + "' in images/.");
        ButtonSpec s = ButtonStore.loadSpec(f);
        JsonObject o = new JsonObject();
        o.addProperty("use_as", "/" + f.getName());
        if (s == null) o.addProperty("note", "This image was not made by the button maker (no stored settings).");
        else o.add("settings", s.toJson());
        return ToolResult.text(o.toString());
    }

    private static ToolResult create(JsonObject a) throws Exception {
        String name = CoreTools.s(a, "name");
        if (name.isEmpty()) return ToolResult.error("name is required (letters, digits, _), e.g. btn_play");
        JsonObject params = new JsonObject();
        for (String k : a.keySet()) {
            boolean tool = false;
            for (String t : TOOL_KEYS) if (t.equals(k)) tool = true;
            if (!tool) params.add(k, a.get(k));
        }
        ArrayList<String> warnings = new ArrayList<>();
        ButtonSpec spec;
        try {
            spec = ButtonSpec.fromJson(params, warnings);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage() + ". Call buttons {action:'list'} for the presets, shapes and parameters.");
        }
        boolean replace = CoreTools.b(a, "replace", false);
        boolean pressed = CoreTools.b(a, "pressed", CoreTools.b(a, "with_pressed", true));
        boolean preview = CoreTools.b(a, "preview", false);
        File images = imagesDir();
        ButtonStore.Saved sv;
        try {
            sv = ButtonStore.save(images, name, spec, pressed, replace, preview);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
        try {
            Gl.run(() -> Bridge.app().applyExternalChanges(Bridge.app().openSceneFingerprints()));
        } catch (Throwable ignored) {
        }

        JsonObject o = new JsonObject();
        o.addProperty("use_as", sv.useAs);
        if (sv.pressedUseAs != null) {
            o.addProperty("pressed_use_as", sv.pressedUseAs);
            o.addProperty("recipe", "on the BOX: onTouchStart -> setImage(" + "'" + sv.pressedUseAs + "'), onTouchEnd -> setImage('" + sv.useAs + "') (read_docs buttons)");
        }
        o.addProperty("stretchable", sv.stretchable);
        o.addProperty("base_size", sv.width + "x" + sv.height);
        if (sv.stretchable) {
            o.addProperty("min_size", sv.minW + "x" + sv.minH);
            o.addProperty("note", "9-patch: set the BOX image to use_as and give the BOX any width/height >= min_size (corners, outline, shadow and lip keep their size). Looks best near base_size; a much taller button stretches the gradient. Label area inside: " + sv.contentW + "x" + sv.contentH + " px at base size.");
        } else {
            o.addProperty("note", "Fixed shape saved as a normal PNG: give the BOX the same aspect ratio (about " + sv.width + "x" + sv.height + " or a uniform scale of it). Label area: " + sv.contentW + "x" + sv.contentH + " px at base size.");
        }
        if (sv.replaced) o.addProperty("replaced", true);
        if (!warnings.isEmpty()) {
            JsonArray w = new JsonArray();
            for (String x : warnings) w.add(x);
            o.add("warnings", w);
        }
        ToolResult r = ToolResult.text(o.toString());
        if (sv.previewPng != null) r.addImage(Base64.encodeToString(sv.previewPng, Base64.NO_WRAP), "image/png");
        return r;
    }

    private static File imagesDir() throws Exception {
        Project p = Gl.run(Proj::project);
        return new File(p.getImagesPath());
    }
}
