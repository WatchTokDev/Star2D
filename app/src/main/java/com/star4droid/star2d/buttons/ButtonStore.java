package com.star4droid.star2d.buttons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Saves/loads button images in a project's images folder. No GL or UI dependencies (used by the panel and by the AI tool). */
public final class ButtonStore {
    private ButtonStore() {}

    public static final String META_KEY = "star2d.button";

    public static final class Saved {
        /** Value for an element's image property, e.g. "/btn_play.9.png". */
        public String useAs;
        public String pressedUseAs;
        public boolean stretchable;
        public boolean replaced;
        public int width, height;
        public int minW, minH;
        public int contentW, contentH;
        public long bytes;
        /** Only when requested: the picture on a neutral grey background, for the AI to look at. */
        public byte[] previewPng;
        public final List<String> warnings = new ArrayList<>();
    }

    /** Engine rule for names: letters, digits and underscore. Returns an error message or null. */
    public static String checkName(String name) {
        if (name == null || name.trim().isEmpty()) return "name is empty";
        if (name.length() > 48) return "name is too long (max 48)";
        for (char ch : name.toCharArray())
            if ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890_".indexOf(ch) < 0)
                return "name may only contain letters, digits and _ (got '" + ch + "')";
        return null;
    }

    /** The existing file of a button called {@code name} (.9.png or .png), or null. */
    public static File existing(File imagesDir, String name) {
        File a = new File(imagesDir, name + ".9.png");
        if (a.isFile()) return a;
        File b = new File(imagesDir, name + ".png");
        return b.isFile() ? b : null;
    }

    /** The settings a saved button was made with (stored inside the PNG), or null for any other image. */
    public static ButtonSpec loadSpec(File png) {
        try {
            String json = PngMeta.readText(png, META_KEY);
            if (json == null) return null;
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return ButtonSpec.fromJson(o, new ArrayList<>());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Renders and writes {@code name}.9.png (or .png for fixed shapes) and, when asked, {@code name}_pressed.
     * Throws IllegalStateException when the name exists and {@code replace} is false, IllegalArgumentException for a bad name.
     */
    public static Saved save(File imagesDir, String name, ButtonSpec spec, boolean withPressed, boolean replace) throws Exception {
        return save(imagesDir, name, spec, withPressed, replace, false);
    }

    public static Saved save(File imagesDir, String name, ButtonSpec spec, boolean withPressed, boolean replace, boolean wantPreview) throws Exception {
        String err = checkName(name);
        if (err != null) throw new IllegalArgumentException(err);
        imagesDir.mkdirs();
        File old = existing(imagesDir, name);
        if (old != null && !replace) throw new IllegalStateException("'" + name + "' already exists (" + old.getName() + "). Pass replace:true to overwrite it or choose another name.");

        Saved out = new Saved();
        ButtonRenderer.Result r = ButtonRenderer.render(spec);
        String ext = r.stretchable ? ".9.png" : ".png";
        File target = new File(imagesDir, name + ext);
        byte[] png = NinePatchWriter.encode(r, spec);
        write(target, png);
        out.useAs = "/" + target.getName();
        out.replaced = old != null;
        out.stretchable = r.stretchable;
        out.width = spec.width;
        out.height = spec.height;
        out.minW = r.minW;
        out.minH = r.minH;
        out.contentW = r.cx1 - r.cx0;
        out.contentH = r.cy1 - r.cy0;
        out.bytes = png.length;
        if (wantPreview) out.previewPng = previewPng(r.bitmap);
        r.bitmap.recycle();

        File other = new File(imagesDir, name + (r.stretchable ? ".png" : ".9.png"));
        if (other.isFile() && PngMeta.readText(other, META_KEY) != null) other.delete(); // stale variant of the same button

        if (withPressed) {
            ButtonSpec ps = spec.pressedVariant();
            ButtonRenderer.Result pr = ButtonRenderer.render(ps);
            File pt = new File(imagesDir, name + "_pressed" + ext);
            write(pt, NinePatchWriter.encode(pr, ps));
            pr.bitmap.recycle();
            out.pressedUseAs = "/" + pt.getName();
        } else if (old != null) {
            // replacing a button: a leftover pressed image of ours would no longer match it
            for (String e : new String[]{"_pressed.9.png", "_pressed.png"}) {
                File stale = new File(imagesDir, name + e);
                if (stale.isFile() && PngMeta.readText(stale, META_KEY) != null) stale.delete();
            }
        }
        return out;
    }

    /** The button on a mid-grey background (transparent PNGs look wrong in most viewers). */
    public static byte[] previewPng(android.graphics.Bitmap b) {
        android.graphics.Bitmap bg = android.graphics.Bitmap.createBitmap(b.getWidth(), b.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bg);
        c.drawColor(0xFF5A5F66);
        c.drawBitmap(b, 0, 0, null);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        bg.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
        bg.recycle();
        return bo.toByteArray();
    }

    private static void write(File f, byte[] data) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
    }
}
