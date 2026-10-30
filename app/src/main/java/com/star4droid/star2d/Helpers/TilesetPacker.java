package com.star4droid.star2d.Helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Export option "Merge images into tilesets": packs the small images of a project COPY into a few big pages
 * (images/_atlas_N.png) and writes files/_atlas.json, read by the game (template AtlasIndex).
 * Fewer textures = faster loading and drawing; identical pictures are stored once. The original project is never touched.
 */
public class TilesetPacker {
    public interface Log { void on(String s); }

    static final int PAGE = 2048, MAX_IMAGE = 512, PAD = 1;

    private static final class Img {
        String rel; int w, h; Bitmap bmp; String hash;
        int page, x, y;
        Img same; // identical picture already placed
    }

    /** @return number of merged images (0 = nothing done). */
    public static int pack(File project, Log log) throws Exception {
        File images = new File(project, "images");
        ArrayList<File> files = new ArrayList<>();
        walk(images, files);
        ArrayList<Img> list = new ArrayList<>();
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".9.png") || n.startsWith("_atlas_") || !(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))) continue;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0 || o.outWidth > MAX_IMAGE || o.outHeight <= 0 || o.outHeight > MAX_IMAGE) continue;
            Img im = new Img();
            im.rel = f.getAbsolutePath().substring(images.getAbsolutePath().length() + 1).replace('\\', '/');
            im.w = o.outWidth;
            im.h = o.outHeight;
            list.add(im);
        }
        if (list.size() < 2) return 0;

        // identical pictures are stored once
        HashMap<String, Img> seen = new HashMap<>();
        ArrayList<Img> unique = new ArrayList<>();
        for (Img im : list) {
            im.bmp = BitmapFactory.decodeFile(new File(images, im.rel).getAbsolutePath());
            if (im.bmp == null) { im.w = 0; continue; }
            int[] px = new int[im.w * im.h];
            im.bmp.getPixels(px, 0, im.w, 0, 0, im.w, im.h);
            im.hash = im.w + "x" + im.h + ":" + Arrays.hashCode(px);
            Img other = seen.get(im.hash);
            if (other != null && same(other.bmp, im.bmp)) { im.same = other; im.bmp.recycle(); im.bmp = null; }
            else { seen.put(im.hash, im); unique.add(im); }
        }
        unique.sort(new Comparator<Img>() {
            @Override public int compare(Img a, Img b) { return b.h != a.h ? Integer.compare(b.h, a.h) : Integer.compare(b.w, a.w); }
        });

        // shelf packing
        int page = 0, x = 0, y = 0, shelf = 0;
        for (Img im : unique) {
            int cw = im.w + PAD * 2, ch = im.h + PAD * 2;
            if (x + cw > PAGE) { x = 0; y += shelf; shelf = 0; }
            if (y + ch > PAGE) { page++; x = 0; y = 0; shelf = 0; }
            im.page = page; im.x = x + PAD; im.y = y + PAD;
            x += cw;
            shelf = Math.max(shelf, ch);
        }
        int pages = page + 1;
        int[] usedH = new int[pages], usedW = new int[pages];
        for (Img im : unique) {
            usedW[im.page] = Math.max(usedW[im.page], im.x + im.w + PAD);
            usedH[im.page] = Math.max(usedH[im.page], im.y + im.h + PAD);
        }
        ArrayList<String> pageNames = new ArrayList<>();
        Paint paint = new Paint();
        for (int p = 0; p < pages; p++) {
            Bitmap pg = Bitmap.createBitmap(pow2(usedW[p]), pow2(usedH[p]), Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(pg);
            for (Img im : unique) if (im.page == p) {
                cv.drawBitmap(im.bmp, im.x, im.y, paint);
                // 1px edge copy: no bleeding between neighbours when drawn scaled / filtered
                cv.drawBitmap(im.bmp, new Rect(0, 0, im.w, 1), new Rect(im.x, im.y - 1, im.x + im.w, im.y), paint);
                cv.drawBitmap(im.bmp, new Rect(0, im.h - 1, im.w, im.h), new Rect(im.x, im.y + im.h, im.x + im.w, im.y + im.h + 1), paint);
                cv.drawBitmap(im.bmp, new Rect(0, 0, 1, im.h), new Rect(im.x - 1, im.y, im.x, im.y + im.h), paint);
                cv.drawBitmap(im.bmp, new Rect(im.w - 1, 0, im.w, im.h), new Rect(im.x + im.w, im.y, im.x + im.w + 1, im.y + im.h), paint);
            }
            String name = "_atlas_" + p + ".png";
            try (FileOutputStream out = new FileOutputStream(new File(images, name))) {
                pg.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            pg.recycle();
            pageNames.add(name);
        }
        LinkedHashMap<String, int[]> index = new LinkedHashMap<>();
        for (Img im : list) {
            if (im.w == 0) continue;
            Img src = im.same != null ? im.same : im;
            index.put(im.rel, new int[]{src.page, src.x, src.y, src.w, src.h});
            new File(images, im.rel).delete();
        }
        for (Img im : unique) if (im.bmp != null) im.bmp.recycle();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("pages", pageNames);
        json.put("images", index);
        File files2 = new File(project, "files");
        files2.mkdirs();
        try (java.io.FileWriter w = new java.io.FileWriter(new File(files2, "_atlas.json"))) {
            w.write(new Gson().toJson(json));
        }
        if (log != null) log.on("Merged " + index.size() + " images into " + pages + " page(s)");
        return index.size();
    }

    private static boolean same(Bitmap a, Bitmap b) {
        return a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight() && a.sameAs(b);
    }

    private static int pow2(int v) {
        int p = 1;
        while (p < v) p <<= 1;
        return Math.min(p, PAGE);
    }

    private static void walk(File d, ArrayList<File> out) {
        File[] l = d.listFiles();
        if (l == null) return;
        for (File f : l) if (f.isDirectory()) walk(f, out); else out.add(f);
    }
}
