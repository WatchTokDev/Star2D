package com.star4droid.star2d.ui.uimaker;

/** The app side of {@link UiKit.Icons}: library icons rendered into the project's images folder (Android). */
public final class UiKitIcons {
    private UiKitIcons() {}

    /** Library icons for the UI skills: rendered once into images/ui_<id>.png (white, tinted by the widgets). */
    public static UiKit.Icons of(final java.io.File projectDir) {
        return id -> {
            String clean = "ui_" + id.replace('-', '_');
            if (com.star4droid.star2d.icons.IconImport.exists(projectDir, clean)) return "/" + clean + ".png";
            com.star4droid.star2d.icons.IconCatalog cat = com.star4droid.star2d.icons.IconCatalog.get();
            com.star4droid.star2d.icons.IconCatalog.Icon ic = cat == null ? null : cat.byId(id);
            if (ic == null) return "";
            try {
                com.star4droid.star2d.icons.IconRenderer.Options o = new com.star4droid.star2d.icons.IconRenderer.Options();
                o.size = 128;
                o.color = 0xFFFFFFFF;
                return com.star4droid.star2d.icons.IconImport.importIcon(projectDir, ic, clean, o, true).useAs;
            } catch (Throwable t) {
                return "";
            }
        };
    }

}
