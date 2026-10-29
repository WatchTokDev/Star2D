package com.star4droid.star2d.remote;

import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.ui.uimaker.UiAi;

/** The `ui` tool: screens made in the UI Maker (UI/*.ui) - list, describe, get, create, set, check, event. Logic in {@link UiAi}. */
final class UiTools {
    private UiTools() {}

    static ToolResult ui(JsonObject a) throws Exception {
        final Project p = Gl.run(Proj::project);
        String orientation;
        try {
            orientation = Proj.projectOrientation(null);
        } catch (Throwable t) {
            orientation = "landscape";
        }
        final java.io.File projectDir = new java.io.File(p.getPath());
        UiAi.icons = com.star4droid.star2d.ui.uimaker.UiKitIcons.of(projectDir);
        try {
            String text = UiAi.run(p.getPath(), a, orientation);
            String action = CoreTools.s(a, "action");
            if (action.equals("create") || action.equals("set") || action.equals("delete")) {
                // the UIHOLDERs of open scenes reload by themselves (file stamp); nothing else to refresh
            }
            return ToolResult.text(text);
        } catch (UiAi.Error e) {
            return ToolResult.error(e.getMessage());
        }
    }
}
