package com.star4droid.star2d.remote;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;

import java.util.Collection;
import java.util.List;

/**
 * Public doors of the remote tools for the IN-APP assistant (the one that uses the user's own API key, ui/ai/*):
 * the same frame sentence, z policy, bounds warnings and tools the connector AIs get.
 */
public final class AiBridge {
    private AiBridge() {}

    /** "scene 'x': PORTRAIT, 720 wide x 1280 tall (x 0..720, y 0..1280, origin bottom-left, y up)". GL thread. */
    public static String frame(LibgdxEditor ed, Project p, String scene) {
        try {
            return SceneInfo.describe(ed, p, scene);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Role-based z for a just-added element when the caller gave none ("" = nothing changed). GL thread. */
    public static String afterAdd(TestApp app, LibgdxEditor ed, Actor actor, JsonObject props) {
        try {
            return ZPolicy.applyOnAdd(app, ed, actor, props == null ? new JsonObject() : props);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Warnings about elements outside the scene frame / absurd sizes (null names = all). GL thread. */
    public static List<String> bounds(LibgdxEditor ed, Collection<String> names) {
        return Warnings.bounds(ed, names);
    }

    /**
     * Runs a connector tool for the in-app assistant and returns its text (all text blocks joined; errors are prefixed).
     * WORKER thread only (never the GL thread: the tools post to it and wait).
     */
    public static String runTool(String tool, JsonObject args) {
        ToolResult r = RemoteTools.runLocal(tool, args);
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : r.content) {
            try {
                JsonObject o = e.getAsJsonObject();
                if (o.has("text")) sb.append(sb.length() > 0 ? "\n" : "").append(o.get("text").getAsString());
                else if (o.has("data")) sb.append(sb.length() > 0 ? "\n" : "").append("[image omitted]");
            } catch (Exception ignored) {
            }
        }
        String t = sb.toString();
        return r.isError ? "Error: " + t : t;
    }
}
