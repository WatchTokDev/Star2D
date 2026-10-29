package com.star4droid.star2d.icons;

import java.io.File;

/** Saves a rendered icon into a project's images folder (shared by the browser UI and the AI tool). Any thread. */
public final class IconImport {
    private IconImport() {}

    public static final class Result {
        public String name;
        /** The value for an element's image property, e.g. "/icon_heart.png". */
        public String useAs;
        public File file;
        public boolean replaced;
        public int bytes;
    }

    /** The target file for a name (without extension) inside {@code <project>/images}. */
    public static File target(File projectDir, String cleanName) {
        return new File(new File(projectDir, "images"), cleanName + ".png");
    }

    public static boolean exists(File projectDir, String cleanName) {
        return target(projectDir, cleanName).exists();
    }

    /**
     * @param name    any user text; cleaned to letters/digits/underscore. Empty = the icon's default name.
     * @param replace overwrite an existing file; otherwise an existing file makes this throw IllegalStateException
     */
    public static Result importIcon(File projectDir, IconCatalog.Icon icon, String name, IconRenderer.Options o, boolean replace) throws Exception {
        String clean = IconRenderer.cleanName(name, icon);
        File f = target(projectDir, clean);
        boolean existed = f.exists();
        if (existed && !replace)
            throw new IllegalStateException("images/" + clean + ".png already exists. Use replace:true to overwrite it or pick another name.");
        Result r = new Result();
        r.name = clean;
        r.file = f;
        r.replaced = existed;
        r.bytes = IconRenderer.writePng(icon, o, f);
        r.useAs = "/" + clean + ".png";
        return r;
    }
}
