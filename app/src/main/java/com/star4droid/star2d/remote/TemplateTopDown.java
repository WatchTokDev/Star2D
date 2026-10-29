package com.star4droid.star2d.remote;

import static com.star4droid.star2d.remote.TemplateKit.*;
import static com.star4droid.star2d.remote.TemplateLib.*;

import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Top-down arena ("Gem Hunter"): joystick moves the hero, collect all gems, slow chasers cost a life, win panel / game over panel.
 * One screen, no camera. Crates are placed with generous passages (>= 100 px) so nothing can be walled in; the checker flood-fills it.
 */
final class TemplateTopDown extends Tpl {
    static final int HERO = 50, FOE = 50, CRATE = 64, WALL = 32, TOP_BAND = 96, GEM_R = 16, JOY = 280;

    @Override
    String id() {
        return "top_down";
    }

    @Override
    String about() {
        return "Top-down arena: joystick hero collects gems, crates as obstacles, chasing enemies, lives, win/lose panel. One screen, any orientation.";
    }

    @Override
    List<P> params() {
        return Arrays.asList(
                new P("gems", "8", "gems to collect (3..20)"),
                new P("enemies", "2", "chasing enemies (0..5)"),
                new P("lives", "3", "start lives (1..9)"),
                new P("theme", "grass", "grass | dungeon | desert"),
                new P("win_text", "YOU WIN!", "text of the win panel (ASCII, max 14)"));
    }

    @Override
    String details() {
        return "Elements: Background (floor), WallL/WallR/WallT/WallB, Crate1.., Gem1.. (sensors), Foe1.., Player, joy (JOYSTICK, bottom-left), GemText, LivesText, End* panel. "
                + "Tweak with the scene variables heroSpeed (220), foeSpeed (110; foes start chasing 2 s after the start and after every respawn), lives, spawnX/spawnY. "
                + "Chasers go straight at the hero (no path finding): they can get stuck behind a crate, that is intended.";
    }

    @Override
    String[] samples() {
        return new String[]{"{\"gems\":20,\"enemies\":5,\"theme\":\"dungeon\",\"lives\":9}", "{\"gems\":3,\"enemies\":0,\"theme\":\"desert\"}", "{\"gems\":99,\"enemies\":-4,\"theme\":\"x\"}"};
    }

    private static boolean hit(double ax, double ay, double aw, double ah, double bx, double by, double bw, double bh, double pad) {
        return ax < bx + bw + pad && ax + aw + pad > bx && ay < by + bh + pad && ay + ah + pad > by;
    }

    @Override
    Plan build(Params prm, int w, int h, String scene) {
        int nGems = prm.integer("gems", 8, 3, 20);
        int nFoes = prm.integer("enemies", 2, 0, 5);
        int lives = prm.integer("lives", 3, 1, 9);
        String theme = prm.choice("theme", "grass", "grass", "dungeon", "desert");
        String win = prm.text("win_text", "YOU WIN!", 14);
        prm.unknown(params());
        String floorA, floorB, floorC, wallTop, wallBottom, hud;
        switch (theme) {
            case "dungeon": floorA = "#3A3F55"; floorB = "#323649"; floorC = "#454B66"; wallTop = "#7A6FA8"; wallBottom = "#4A3F73"; hud = "#FFFFFF"; break;
            case "desert": floorA = "#E6C874"; floorB = "#DDBB63"; floorC = "#EED58C"; wallTop = "#B7793F"; wallBottom = "#85501F"; hud = "#FFFFFF"; break;
            default: floorA = "#5FAE4F"; floorB = "#559F46"; floorC = "#6DBB5C"; wallTop = "#8D6E63"; wallBottom = "#5D4037"; hud = "#FFFFFF"; break;
        }
        Plan p = new Plan(w, h, w > h, scene, floorA);
        Rng r = new Rng(w * 17L + h * 3L + nGems);

        // ---------------------------------------------------------------- arena
        double ax0 = WALL, ax1 = w - WALL, ay0 = WALL, ay1 = h - TOP_BAND;
        double spawnX = w / 2.0, spawnY = (ay0 + ay1) / 2.0;
        double[] joyKeepOut = {0, 0, 36 + JOY + 20, 36 + JOY + 20};

        ArrayList<double[]> crates = new ArrayList<>();
        int wantCrates = clampInt(w * h / 110000.0, 4, 10);
        for (int tries = 0; tries < 600 && crates.size() < wantCrates; tries++) {
            double cx = r.next((int) ax0 + 110, (int) (ax1 - 110 - CRATE)), cy = r.next((int) ay0 + 110, (int) (ay1 - 110 - CRATE));
            boolean ok = !hit(cx, cy, CRATE, CRATE, joyKeepOut[0], joyKeepOut[1], joyKeepOut[2], joyKeepOut[3], 30);
            ok &= Math.hypot(cx + CRATE / 2.0 - spawnX, cy + CRATE / 2.0 - spawnY) > 170;
            for (double[] o : crates) ok &= !hit(cx, cy, CRATE, CRATE, o[0], o[1], CRATE, CRATE, 110);
            if (ok) crates.add(new double[]{cx, cy});
        }
        ArrayList<double[]> gems = new ArrayList<>();
        for (int tries = 0; tries < 4000 && gems.size() < nGems; tries++) {
            double gx = r.next((int) ax0 + 60, (int) ax1 - 60), gy = r.next((int) ay0 + 60, (int) ay1 - 60);
            boolean ok = !hit(gx - GEM_R, gy - GEM_R, 2 * GEM_R, 2 * GEM_R, joyKeepOut[0], joyKeepOut[1], joyKeepOut[2], joyKeepOut[3], 0);
            ok &= Math.hypot(gx - spawnX, gy - spawnY) > 150;
            for (double[] c : crates) ok &= !hit(gx - GEM_R, gy - GEM_R, 2 * GEM_R, 2 * GEM_R, c[0], c[1], CRATE, CRATE, 45);
            for (double[] o : gems) ok &= Math.hypot(gx - o[0], gy - o[1]) > 100;
            if (ok) gems.add(new double[]{gx, gy});
        }
        if (gems.size() < nGems) {
            prm.notes.add("gems: this screen has room for " + gems.size() + ", made " + gems.size());
            nGems = gems.size();
        }
        double[][] foeStart = {{ax1 - 80, ay1 - 80}, {ax0 + 80, ay1 - 80}, {ax1 - 80, ay0 + 80}, {w / 2.0, ay1 - 80}, {ax1 - 80, spawnY}};
        ArrayList<double[]> foes = new ArrayList<>();
        for (int i = 0; i < nFoes; i++) {
            double[] f = foeStart[i];
            // keep clear of crates
            for (double[] c : crates) if (hit(f[0] - FOE / 2.0, f[1] - FOE / 2.0, FOE, FOE, c[0], c[1], CRATE, CRATE, 30)) f = new double[]{f[0], Math.min(ay1 - 60, c[1] + CRATE + 70)};
            foes.add(f);
        }

        // ---------------------------------------------------------------- art
        int tile = 16;
        LinkedHashMap<Character, String> pal = new LinkedHashMap<>();
        pal.put('1', floorA);
        pal.put('2', floorB);
        pal.put('3', floorC);
        p.image("floor.png", obj("kind", "tiles", "rows", arr("1121", "1111", "3111", "1213"), "palette", obj("1", argb(floorA, 1), "2", argb(floorB, 1), "3", argb(floorC, 1)), "scale", tile, "width", w, "height", h));
        p.image("wall.png", obj("kind", "shape", "shape", "rect", "width", 64, "height", 64, "fill", "linear:" + wallTop + "," + wallBottom + ",90", "stroke", darker(wallBottom, 0.4), "strokeWidth", 3));
        p.image("crate.png", obj("kind", "shape", "shape", "rounded", "width", CRATE, "height", CRATE, "radius", 8, "fill", "linear:#CE9556,#8D5A2B,90", "stroke", "#4A2C0A", "strokeWidth", 4));
        p.image("gem.png", obj("kind", "shape", "shape", "star", "width", GEM_R * 2, "height", GEM_R * 2, "fill", "radial:#D9F8FF,#18B7D6", "stroke", "#0B6B80", "strokeWidth", 2));
        TemplateArt.put(p, "hero.png", TemplateArt.TOP_HERO);
        TemplateArt.put(p, "foe.png", TemplateArt.TOP_FOE);
        p.whitePixel();
        p.sound("coin.wav", "coin").sound("hit.wav", "hit").sound("win.wav", "powerup");

        // ---------------------------------------------------------------- elements
        p.add(new El("BOX", "Background").body("UI").at(0, 0).size(w, h).img("/floor.png").z(Z_BG).role("background"));
        p.add(new El("BOX", "WallB").body("STATIC").at(0, 0).size(w, WALL).img("/wall.png").p("tileX", Math.max(1, w / 64)).p("restitution", 0).z(Z_GROUND).role("wall"));
        p.add(new El("BOX", "WallT").body("STATIC").at(0, h - TOP_BAND).size(w, TOP_BAND).img("/wall.png").p("tileX", Math.max(1, w / 64)).p("tileY", 2).p("restitution", 0).z(Z_GROUND).role("wall"));
        p.add(new El("BOX", "WallL").body("STATIC").at(0, WALL).size(WALL, h - TOP_BAND - WALL).img("/wall.png").p("tileY", Math.max(1, (h - TOP_BAND - WALL) / 64)).p("restitution", 0).z(Z_GROUND).role("wall"));
        p.add(new El("BOX", "WallR").body("STATIC").at(w - WALL, WALL).size(WALL, h - TOP_BAND - WALL).img("/wall.png").p("tileY", Math.max(1, (h - TOP_BAND - WALL) / 64)).p("restitution", 0).z(Z_GROUND).role("wall"));
        for (int i = 0; i < crates.size(); i++) {
            double[] c = crates.get(i);
            p.add(new El("BOX", "Crate" + (i + 1)).body("STATIC").at(c[0], c[1]).size(CRATE, CRATE).img("/crate.png").p("restitution", 0).z(Z_GROUND + 2).role("ground"));
        }
        for (int i = 0; i < gems.size(); i++) {
            double[] g = gems.get(i);
            p.add(new El("CIRCLE", "Gem" + (i + 1)).body("STATIC").at(g[0] - GEM_R, g[1] - GEM_R).p("radius", GEM_R).img("/gem.png").p("isSensor", true).z(Z_ITEM).role("item"));
        }
        for (int i = 0; i < foes.size(); i++) {
            double[] f = foes.get(i);
            p.add(new El("BOX", "Foe" + (i + 1)).body("DYNAMIC").at(f[0] - FOE / 2.0, f[1] - FOE / 2.0).size(FOE, FOE).img("/foe.png").p("restitution", 0).p("friction", 0).p("Fixed Rotation", true)
                    .p("Gravity Scale", 0).z(Z_ACTOR - 5).role("enemy"));
        }
        p.add(new El("BOX", "Player").body("DYNAMIC").at(spawnX - HERO / 2.0, spawnY - HERO / 2.0).size(HERO, HERO).img("/hero.png").p("restitution", 0).p("friction", 0).p("Fixed Rotation", true)
                .p("Gravity Scale", 0).z(Z_ACTOR).role("player"));

        // ---------------------------------------------------------------- HUD, joystick, end panel
        hud(p, "GemText", "Gems: 0/" + nGems, "Gems: 00/00", true, 0, 2.5, hud);
        hud(p, "LivesText", "Lives: " + lives, "Lives: 0", false, 0, 2.5, hud);
        p.add(new El("JOYSTICK", "joy").at(36, 36).size(JOY, JOY).z(Z_JOY).role("joystick"));
        endOverlay(p, scene, win.length() > 9 ? win : "GAME OVER", "Gems: 00/00");

        // ---------------------------------------------------------------- variables
        p.var("gems", "int", "0").var("total", "int", String.valueOf(nGems)).var("lives", "int", String.valueOf(lives));
        p.var("over", "boolean", "false").var("hurt", "boolean", "false").var("chase", "boolean", "false").var("invul", "float", "0f");
        p.var("heroSpeed", "float", "220f").var("foeSpeed", "float", "110f").var("spawnX", "float", fmt(spawnX) + "f").var("spawnY", "float", fmt(spawnY) + "f");

        // ---------------------------------------------------------------- events
        p.on("OnCreate").add(b("setWorldGravity", "strength", 0));
        p.on("OnCreate").add(after(2, setVar("chase", "true")));

        JsonArray step = new JsonArray();
        step.add(ifElse("invul > 0", seq(addVar("invul", "-getDelta()"), b("setOpacity", "body", "Player", "alpha", 0.5)), seq(b("setOpacity", "body", "Player", "alpha", 1))));
        step.add(b("moveWithJoystick", "body", "Player", "joystick", "joy", "speed", "heroSpeed"));
        JsonArray chase = new JsonArray();
        for (int i = 1; i <= foes.size(); i++) chase.add(b("moveToward", "body", "Foe" + i, "target", "Player", "speed", "foeSpeed"));
        if (chase.size() > 0) step.add(ifThen("chase", chase));
        JsonArray back = new JsonArray();
        for (int i = 1; i <= foes.size(); i++) {
            back.add(goTo("Foe" + i, fmt(foes.get(i - 1)[0]), fmt(foes.get(i - 1)[1])));
            back.add(b("stopMoving", "body", "Foe" + i));
        }
        step.add(ifThen("hurt",
                setVar("hurt", "false"),
                ifThen("invul <= 0",
                        addVar("lives", -1),
                        setText("LivesText", "\"Lives: \" + lives"),
                        sound("hit.wav"),
                        goTo("Player", "spawnX", "spawnY"),
                        b("stopMoving", "body", "Player"),
                        back,
                        setVar("chase", "false"),
                        after(1.5, setVar("chase", "true")),
                        setVar("invul", "1.5f"),
                        ifThen("lives <= 0", setVar("over", "true"), showEnd("\"GAME OVER\"", "\"Gems: \" + gems + \"/\" + total")))));
        p.on("OnStep").add(ifThen("!over", seq(step)));

        JsonArray begin = p.on("onCollisionStart");
        begin.add(ifThen(pairStarts("Player", "Gem"),
                pick("g", "Gem"),
                ifThen("g.getActor().isVisible()", dispose("g", 0.02), addVar("gems", 1), setText("GemText", "\"Gems: \" + gems + \"/\" + total"), sound("coin.wav"),
                        ifThen("gems >= total && !over", setVar("over", "true"), b("stopMoving", "body", "Player"), sound("win.wav"), showEnd(jstr(win), "\"Gems: \" + gems + \"/\" + total")))));
        begin.add(ifThen(pairStarts("Player", "Foe"), setVar("hurt", "true")));

        p.tweak("variables: heroSpeed (220), foeSpeed (110), lives (" + lives + "), spawnX/spawnY");
        p.tweak("gravity is 0 (first block of OnCreate); the chasers wait 2 s at the start and 1.5 s after each hit");
        p.info(nGems + " gems, " + foes.size() + " enemies, " + crates.size() + " crates, joystick bottom-left");
        p.meta.put("kind", "top_down");
        p.meta.put("scrolls", false);
        p.meta.put("heroSize", HERO);
        p.meta.put("wall", WALL);
        p.meta.put("topBand", TOP_BAND);
        p.meta.put("foes", foes.size());
        p.meta.put("gems", nGems);
        return p;
    }
}
