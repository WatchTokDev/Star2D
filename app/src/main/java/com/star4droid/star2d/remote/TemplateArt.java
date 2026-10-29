package com.star4droid.star2d.remote;

import java.util.LinkedHashMap;

/**
 * Small pixel-art sprites and shape recipes shared by the game templates (pure Java; Gson only through TemplateKit).
 * Every sprite is drawn with the {@code create_image} tool (kind pixels / shape / gradient), so a template needs no uploaded files.
 * The pixel size of a sprite = rows x (columns) x scale; the elements that use it are made exactly that big (no stretching).
 */
final class TemplateArt {
    private TemplateArt() {}

    static final class Sprite {
        final String[] rows;
        final LinkedHashMap<Character, String> pal = new LinkedHashMap<>();
        final int scale;

        Sprite(int scale, String[] rows, Object... pal) {
            this.rows = rows;
            this.scale = scale;
            for (int i = 0; i + 1 < pal.length; i += 2) this.pal.put((Character) pal[i], (String) pal[i + 1]);
        }

        int w() {
            int w = 0;
            for (String r : rows) w = Math.max(w, r.length());
            return w * scale;
        }

        int h() {
            return rows.length * scale;
        }
    }

    /** Player of the platformer: 12 x 16 pixels, scale 4 = 48 x 64. Faces forward. */
    static final Sprite HERO = new Sprite(4, new String[]{
            "...111111...",
            "..11111111..",
            "..22222222..",
            "..22322322..",
            "..22222222..",
            "...222222...",
            "..44444444..",
            ".4444444444.",
            ".4444444444.",
            ".2444444442.",
            "..44444444..",
            "..55555555..",
            "..55555555..",
            "..555..555..",
            "..555..555..",
            ".6666..6666."},
            '1', "#C0392B", '2', "#F5CBA7", '3', "#222222", '4', "#2E86C1", '5', "#1B4F72", '6', "#4A2C0A");

    /** Walking enemy: 12 x 10 pixels, scale 4 = 48 x 40. */
    static final Sprite BLOB = new Sprite(4, new String[]{
            "....1111....",
            "..11111111..",
            ".1111111111.",
            ".1331111331.",
            ".1341111341.",
            "111111111111",
            "111111111111",
            "122222222221",
            ".1111111111.",
            "..11....11.."},
            '1', "#E74C3C", '2', "#C0392B", '3', "#FFFFFF", '4', "#111111");

    /** Player ship, pointing up: 13 x 14 pixels, scale 5 = 65 x 70. */
    static final Sprite SHIP = new Sprite(5, new String[]{
            "......1......",
            ".....111.....",
            ".....111.....",
            "....12221....",
            "....12321....",
            "...1222221...",
            "..122232221..",
            "..122222221..",
            ".12222222221.",
            "1122.222.2211",
            "112..222..211",
            "11...444...11",
            ".....454.....",
            "......5......"},
            '1', "#2C3E50", '2', "#5DADE2", '3', "#F7DC6F", '4', "#F39C12", '5', "#E74C3C");

    /** Invader, pointing down: 11 x 8 pixels, scale 5 = 55 x 40. */
    static final Sprite ALIEN = new Sprite(5, new String[]{
            "..1.....1..",
            "...1...1...",
            "..1111111..",
            ".112111211.",
            "11111111111",
            "1.1111111.1",
            "1.1.....1.1",
            "...11.11..."},
            '1', "#58D68D", '2', "#1B2631");

    /** Bird for flappy, facing right: 12 x 8 pixels, scale 4 = 48 x 32. */
    static final Sprite BIRD = new Sprite(4, new String[]{
            "...111111...",
            "..12222221..",
            ".1222223331.",
            "122222234311",
            "162222222555",
            ".1662222555.",
            "..11222221..",
            "....11111..."},
            '1', "#7D4E00", '2', "#F4D03F", '3', "#FFFFFF", '4', "#111111", '5', "#E67E22", '6', "#F39C12");

    /** Player of the top-down game seen from above: 10 x 10, scale 5 = 50 x 50. */
    static final Sprite TOP_HERO = new Sprite(5, new String[]{
            "...1111...",
            "..122221..",
            ".12222221.",
            "1223223221",
            "1222222221",
            "1233333221",
            "1223333221",
            ".12222221.",
            "..122221..",
            "...1111..."},
            '1', "#154360", '2', "#3498DB", '3', "#F5CBA7");

    /** Chaser enemy for the top-down game: 10 x 10, scale 5 = 50 x 50. */
    static final Sprite TOP_FOE = new Sprite(5, new String[]{
            "...1111...",
            "..122221..",
            ".12222221.",
            "1223223221",
            "1222222221",
            "1244444221",
            "1224242221",
            ".12222221.",
            "..122221..",
            "...1111..."},
            '1', "#4A235A", '2', "#9B59B6", '3', "#FFFFFF", '4', "#111111");

    /** Registers the sprite as an image of the plan under {@code file}. */
    static void put(TemplateKit.Plan p, String file, Sprite s) {
        p.pixels(file, s.rows, s.pal, s.scale);
    }

    /** Coin: gold disc with a ring, d x d px. */
    static void coin(TemplateKit.Plan p, String file, int d) {
        p.image(file, TemplateKit.obj("kind", "shape", "shape", "circle", "width", d, "height", d, "fill", "radial:#FFF59D,#F1C40F", "stroke", "#B7950B", "strokeWidth", Math.max(2, d / 8)));
    }

    /** Soft cloud, wide ellipse. */
    static void cloud(TemplateKit.Plan p, String file, int w, int h) {
        p.image(file, TemplateKit.obj("kind", "shape", "shape", "ellipse", "width", w, "height", h, "fill", "#FFFFFF"));
    }

    /** Star field covering the whole screen: deterministic pixel dots. Returns the image size actually produced (w x h px). */
    static void starfield(TemplateKit.Plan p, String file, int w, int h, long seed) {
        int cell = 4;
        int cols = Math.max(8, w / cell), rows = Math.max(8, h / cell);
        TemplateKit.Rng rng = new TemplateKit.Rng(seed);
        String[] r = new String[rows];
        for (int y = 0; y < rows; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < cols; x++) {
                char c = '.';
                if (rng.permille(7)) c = '1';
                else if (rng.permille(14)) c = '2';
                sb.append(c);
            }
            r[y] = sb.toString();
        }
        LinkedHashMap<Character, String> pal = new LinkedHashMap<>();
        pal.put('1', "#FFFFFF");
        pal.put('2', "#6F86C0");
        p.pixels(file, r, pal, cell);
    }
}
