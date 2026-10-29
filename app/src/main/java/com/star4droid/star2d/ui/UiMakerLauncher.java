package com.star4droid.star2d.ui;

import android.content.Context;

/**
 * Entry point used by the "ui" property (UIHolder) to open the UI Maker for a
 * {@code UI/<name>.ui} file of the current project.
 *
 * <p>Opens {@link com.star4droid.star2d.ui.uimaker.UiMakerDialog} (set {@link #impl} to replace it). Contract: when the user saves, the file is written (UIDocument.write) and
 * {@code onSaved} is run on the UI thread, so the UIHolder in the editor reloads.</p>
 */
public final class UiMakerLauncher {
    private UiMakerLauncher() {}

    /** Hook for H2: set to a real implementation instead of editing this class. */
    public interface Impl {
        void open(Context c, String projectPath, String uiName, Runnable onSaved);
    }

    public static Impl impl;

    public static void open(Context c, String projectPath, String uiName, Runnable onSaved) {
        if (impl != null) {
            impl.open(c, projectPath, uiName, onSaved);
            return;
        }
        com.star4droid.star2d.ui.uimaker.UiMakerDialog.open(c, projectPath, uiName, onSaved);
    }

    /** Folder that holds the .ui files of a project. */
    public static java.io.File uiDir(String projectPath) {
        return new java.io.File(projectPath, "UI");
    }

    /** Names (without .ui) of the UI files in the project, sorted. */
    public static java.util.ArrayList<String> list(String projectPath) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        java.io.File[] fs = uiDir(projectPath).listFiles();
        if (fs != null)
            for (java.io.File f : fs)
                if (f.isFile() && f.getName().toLowerCase().endsWith(".ui"))
                    out.add(f.getName().substring(0, f.getName().length() - 3));
        java.util.Collections.sort(out);
        return out;
    }
}
