package com.star4droid.star2d.remote;

import static com.star4droid.star2d.remote.TemplateKit.*;
import static com.star4droid.star2d.remote.TemplateLib.*;

import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Side-view jump-and-run. Everything is derived from the real frame (w x h): the level is {@code length} screens long, the staircase
 * height uses the extra height of a portrait screen. The jump physics are fixed (gravity 700, jump 440, speed 240) and every distance
 * of the level is kept inside what that jump can do: step <= 100 px (jump height 138), gap <= 190 px (flat jump reaches 300).
 */
final class TemplatePlatformer extends Tpl {
    // the player and the world, in px
    static final int PW = 48, PH = 64, EW = 48, EH = 40, GH = 96, PT = 24, STEP = 100, COIN_D = 28;
    static final double G = 700, JUMP = 440, SPEED = 240, ESPEED = 70;

    @Override
    String id() {
        return "platformer";
    }

    @Override
    String about() {
        return "Side-view jump & run: scrolling level with pits, platform stairs, coins, patrolling enemies (stomp them), goal, lives, win/lose panel, touch controls.";
    }

    @Override
    List<P> params() {
        return Arrays.asList(
                new P("length", "3", "level length in screens (2..6)"),
                new P("lives", "3", "start lives (1..9)"),
                new P("enemies", "3", "patrolling enemies (0..6)"),
                new P("theme", "day", "day | night | sunset"),
                new P("win_text", "YOU WIN!", "text of the win panel (ASCII, max 14)"));
    }

    @Override
    String details() {
        return "Elements: Background, Cloud1.., Ground1.. and Plat1.. (STATIC), Coin1.. (sensors), Enemy1.. (patrol), Goal, WallL/WallR (invisible), Player, CoinText, LivesText, "
                + "LeftBtn/RightBtn/JumpBtn (+Label), EndPanel/EndTitle/EndSub/RestartBtn. Tweak with the scene variables jumpPower (jump height = jumpPower^2/1400), moveSpeed, "
                + "enemySpeed, lives, spawnX/spawnY. If you raise gravity or lower jumpPower the level may become impossible: height 138 px, step 100 px, gap 190 px.";
    }

    private static final class Seg {
        final double x0, x1;
        final String kind;
        final int index;

        Seg(int index, double x0, double x1, String kind) {
            this.index = index;
            this.x0 = x0;
            this.x1 = x1;
            this.kind = kind;
        }
    }

    private static double round96(double v) {
        return Math.max(96, Math.round(v / 96.0) * 96.0);
    }

    @Override
    String[] samples() {
        return new String[]{"{\"length\":6,\"enemies\":6,\"theme\":\"night\",\"win_text\":\"CONGRATULATIONS!\"}", "{\"length\":2,\"enemies\":0,\"lives\":9,\"theme\":\"sunset\"}", "{\"length\":99,\"theme\":\"x\",\"bogus\":1}"};
    }

    @Override
    Plan build(Params prm, int w, int h, String scene) {
        int length = prm.integer("length", 3, 2, 6);
        int lives = prm.integer("lives", 3, 1, 9);
        int nEnemies = prm.integer("enemies", 3, 0, 6);
        String theme = prm.choice("theme", "day", "day", "night", "sunset");
        String win = prm.text("win_text", "YOU WIN!", 14);
        prm.unknown(params());
        String[] sky = sky(theme);
        Plan p = new Plan(w, h, w > h, scene, sky[1]);

        // ---------------------------------------------------------------- level layout
        Rng r = new Rng(w * 31L + h + length * 7L);
        int K = clampInt((h * 0.55 - GH) / STEP, 2, 5); // steps of a staircase (more in portrait)
        double LW = Math.max(w, 1000) * (double) length;
        ArrayList<Seg> segs = new ArrayList<>();
        double x = round96(Math.max(0.6 * w, 768));
        segs.add(new Seg(1, 0, x, "start"));
        boolean stairs = true;
        while (true) {
            double gap = r.next(120, 190);
            boolean last = x + gap + 640 >= LW;
            double sw;
            String kind;
            if (last) {
                kind = "goal";
                sw = 576;
            } else if (stairs) {
                kind = "stairs";
                sw = round96(Math.max(r.next(480, 640), 130 * K + 170));
            } else {
                kind = "flat";
                sw = round96(r.next(384, 672));
            }
            segs.add(new Seg(segs.size() + 1, x + gap, x + gap + sw, kind));
            x += gap + sw;
            if (last) break;
            stairs = !stairs;
        }
        LW = x;

        // ---------------------------------------------------------------- art
        TemplateArt.put(p, "hero.png", TemplateArt.HERO);
        TemplateArt.put(p, "blob.png", TemplateArt.BLOB);
        TemplateArt.coin(p, "coin.png", COIN_D);
        p.image("goal.png", obj("kind", "shape", "shape", "star", "width", 64, "height", 64, "fill", "radial:#FFF59D,#F5B041", "stroke", "#B9770E", "strokeWidth", 4));
        p.image("ground.png", obj("kind", "shape", "shape", "rect", "width", 96, "height", GH, "fill", "linear:#6DD34A,#4BA533,#8B5E34,#6B4423,90", "stroke", "#3E2A14", "strokeWidth", 3));
        p.image("platform.png", obj("kind", "shape", "shape", "rect", "width", 48, "height", PT, "fill", "linear:#6DD34A,#4BA533,#8B5E34,90", "stroke", "#3E2A14", "strokeWidth", 3));
        p.whitePixel();
        p.sound("jump.wav", "jump").sound("coin.wav", "coin").sound("hit.wav", "hit").sound("win.wav", "powerup");

        // ---------------------------------------------------------------- screen layer
        background(p, sky[0], sky[1]);
        clouds(p, 3, w * 5L + h);

        // ---------------------------------------------------------------- ground, platforms (z 10/11)
        int plat = 0, coin = 0;
        ArrayList<double[]> coinSpots = new ArrayList<>();
        for (Seg s : segs) {
            double sw = s.x1 - s.x0;
            p.add(new El("BOX", "Ground" + s.index).body("STATIC").at(s.x0, 0).size(sw, GH).img("/ground.png").p("tileX", Math.max(1, Math.round(sw / 96))).p("tileY", 1)
                    .p("restitution", 0).p("friction", 0.1).z(Z_GROUND).role("ground"));
            if (s.kind.equals("stairs")) {
                for (int i = 1; i <= K; i++) {
                    double px = s.x0 + 72 + (i - 1) * 130, top = GH + STEP * i;
                    plat++;
                    p.add(new El("BOX", "Plat" + plat).body("STATIC").at(px, top - PT).size(144, PT).img("/platform.png").p("tileX", 3).p("tileY", 1)
                            .p("restitution", 0).p("friction", 0.1).z(Z_GROUND + 1).role("platform"));
                    coinSpots.add(new double[]{px + 72, top + 36});
                }
            } else if (s.kind.equals("flat") || s.kind.equals("start")) {
                int n = s.kind.equals("start") ? 0 : 3;
                for (int i = 0; i < n; i++) coinSpots.add(new double[]{s.x0 + sw / 2 + (i - 1) * 60, GH + 36});
            }
        }
        // coins over the pits (on the way of the jump arc)
        for (int i = 0; i + 1 < segs.size(); i++) {
            double gx = (segs.get(i).x1 + segs.get(i + 1).x0) / 2;
            coinSpots.add(new double[]{gx - 20, GH + 90});
            coinSpots.add(new double[]{gx + 20, GH + 90});
        }
        for (double[] c : coinSpots) {
            if (coin >= 40) break;
            coin++;
            p.add(new El("CIRCLE", "Coin" + coin).body("STATIC").at(c[0] - COIN_D / 2.0, c[1] - COIN_D / 2.0).p("radius", COIN_D / 2.0).img("/coin.png")
                    .p("isSensor", true).z(Z_ITEM).role("coin"));
        }
        // goal
        Seg end = segs.get(segs.size() - 1);
        p.add(new El("BOX", "Goal").body("STATIC").at(end.x0 + 380, GH + 8).size(64, 64).img("/goal.png").p("isSensor", true).z(Z_ITEM + 1).role("goal"));
        // invisible walls at both ends of the level
        p.add(new El("BOX", "WallL").body("STATIC").at(-40, 0).size(40, h * 2).img("/white.png").tint("#FFFFFF", 0).p("restitution", 0).z(Z_GROUND).role("wall").off());
        p.add(new El("BOX", "WallR").body("STATIC").at(LW, 0).size(40, h * 2).img("/white.png").tint("#FFFFFF", 0).p("restitution", 0).z(Z_GROUND).role("wall").off());

        // ---------------------------------------------------------------- enemies (z 25) and the player (z 30)
        ArrayList<Seg> cand = new ArrayList<>();
        for (Seg s : segs) if (s.kind.equals("flat") || s.kind.equals("stairs")) cand.add(s);
        int ne = Math.min(nEnemies, cand.size());
        if (ne < nEnemies) prm.notes.add("enemies: this level has room for " + ne + " (one per ground piece), made " + ne);
        double[][] patrol = new double[ne][2];
        for (int i = 0; i < ne; i++) {
            Seg s = cand.get(i * cand.size() / Math.max(1, ne));
            String n = "Enemy" + (i + 1);
            patrol[i][0] = s.x0 + 30 + EW / 2.0;
            patrol[i][1] = s.x1 - 30 - EW / 2.0;
            double cx = s.x0 + (s.x1 - s.x0) * 0.6;
            p.add(new El("BOX", n).body("DYNAMIC").at(cx - EW / 2.0, GH + 1).size(EW, EH).img("/blob.png").p("restitution", 0).p("friction", 0).p("Fixed Rotation", true)
                    .z(Z_ACTOR - 5).role("enemy"));
        }
        double spawnX = 400, spawnCenterY = GH + PH / 2.0 + 4;
        p.add(new El("BOX", "Player").body("DYNAMIC").at(spawnX - PW / 2.0, GH + 2).size(PW, PH).img("/hero.png").p("restitution", 0).p("friction", 0).p("Fixed Rotation", true)
                .z(Z_ACTOR).role("player"));

        // ---------------------------------------------------------------- HUD and controls
        double hs = 2.5;
        hud(p, "CoinText", "Coins: 0", "Coins: 00", true, 0, hs, sky[2]);
        hud(p, "LivesText", "Lives: " + lives, "Lives: 0", false, 0, hs, sky[2]);
        sideControls(p, "JumpBtn", "JUMP");
        endOverlay(p, scene, win.length() > 9 ? win : "GAME OVER", "Coins: 00");

        // ---------------------------------------------------------------- variables
        p.var("coins", "int", "0").var("lives", "int", String.valueOf(lives)).var("over", "boolean", "false").var("hurt", "boolean", "false").var("dir", "int", "0");
        p.var("jumpPower", "float", fmt(JUMP) + "f").var("moveSpeed", "float", fmt(SPEED) + "f").var("enemySpeed", "float", fmt(ESPEED) + "f").var("invul", "float", "0f");
        p.var("spawnX", "float", fmt(spawnX) + "f").var("spawnY", "float", fmt(spawnCenterY) + "f");
        p.var("feet", "java.util.HashSet<String>", "new java.util.HashSet<String>()").var("dead", "java.util.HashSet<String>", "new java.util.HashSet<String>()");
        for (int i = 1; i <= ne; i++) p.var("eDir" + i, "int", "1");

        // ---------------------------------------------------------------- scene events
        p.on("OnCreate").add(b("setWorldGravity", "strength", fmt(G)));

        JsonArray step = new JsonArray();
        step.add(ifElse("invul > 0", seq(addVar("invul", "-getDelta()"), b("setOpacity", "body", "Player", "alpha", 0.5)), seq(b("setOpacity", "body", "Player", "alpha", 1))));
        step.add(velX("Player", "dir * moveSpeed"));
        step.add(b("setCameraCenter", "x", max(w / 8.0, min(LW * 0.25 - w / 8.0, mul(xOf("Player"), 0.25))), "y", h / 8.0));
        for (int i = 1; i <= ne; i++) {
            String e = "Enemy" + i;
            step.add(ifThen("!dead.contains(\"" + e + "\")",
                    velX(e, "eDir" + i + " * enemySpeed"),
                    ifThen(ge(xOf(e), fmt(patrol[i - 1][1])), setVar("eDir" + i, -1)),
                    ifThen(le(xOf(e), fmt(patrol[i - 1][0])), setVar("eDir" + i, 1))));
        }
        step.add(ifThen(lt(yOf("Player"), -120), setVar("invul", 0), setVar("hurt", "true")));
        step.add(ifThen("hurt",
                setVar("hurt", "false"),
                ifThen("invul <= 0",
                        addVar("lives", -1),
                        setText("LivesText", "\"Lives: \" + lives"),
                        sound("hit.wav"),
                        goTo("Player", "spawnX", "spawnY"),
                        b("stopMoving", "body", "Player"),
                        code("feet.clear();"),
                        setVar("invul", "1.5f"),
                        ifThen("lives <= 0", setVar("over", "true"), showEnd("\"GAME OVER\"", "\"Coins: \" + coins")))));
        p.on("OnStep").add(ifThen("!over", seq(step)));

        // collisions (the physics world is locked here: only flags, velocities, texts, hide + timed destroy)
        JsonArray begin = p.on("onCollisionStart");
        begin.add(ifThen(pairStarts("Player", "Coin"),
                pick("c", "Coin"),
                ifThen("c.getActor().isVisible()", dispose("c", 0.02), addVar("coins", 1), setText("CoinText", "\"Coins: \" + coins"), sound("coin.wav"))));
        double stomp = (PH / 2.0 + EH / 2.0 - 14) * WORLD_SCALE;
        begin.add(ifThen(pairStarts("Player", "Enemy"),
                pick("en", "Enemy"),
                ifThen("!dead.contains(en.getName())",
                        ifElse("Player.getBody().getLinearVelocity().y <= 1f && Player.getBodyY() > en.getBodyY() + " + f(stomp),
                                seq(code("dead.add(en.getName());"), dispose("en", 0.02), velY("Player", 300), sound("hit.wav")),
                                seq(setVar("hurt", "true"))))));
        begin.add(ifThen(pairEquals("Player", "Goal"),
                ifThen("!over", setVar("over", "true"), b("stopMoving", "body", "Player"), sound("win.wav"),
                        showEnd(jstr(win), "\"Coins: \" + coins"))));
        String feetCond = "Player.getBodyY() - " + f(PH / 2.0 * WORLD_SCALE) + " >= o.getBodyY() + (o.getName().startsWith(\"Ground\") ? " + GH + " : " + PT + ") / 2f * " + f(WORLD_SCALE) + " - 0.75f";
        begin.add(ifThen(or(pairStarts("Player", "Ground"), pairStarts("Player", "Plat")),
                code("PlayerItem o = body1.getName().equals(\"Player\") ? body2 : body1;"),
                ifThen(feetCond, code("feet.add(o.getName());"))));
        p.on("onCollisionEnd").add(ifThen(or(pairStarts("Player", "Ground"), pairStarts("Player", "Plat")),
                code("PlayerItem o = body1.getName().equals(\"Player\") ? body2 : body1;"), code("feet.remove(o.getName());")));

        // controls
        p.onElement("LeftBtn", "onTouchStart", seq(setVar("dir", -1)));
        p.onElement("LeftBtn", "onTouchEnd", seq(ifThen("dir == -1", setVar("dir", 0))));
        p.onElement("RightBtn", "onTouchStart", seq(setVar("dir", 1)));
        p.onElement("RightBtn", "onTouchEnd", seq(ifThen("dir == 1", setVar("dir", 0))));
        p.onElement("JumpBtn", "onTouchStart", seq(ifThen(and("!over", "!feet.isEmpty()"), velY("Player", "jumpPower"), sound("jump.wav"), code("feet.clear();"))));

        // ---------------------------------------------------------------- facts for the reply / the checker
        p.tweak("variables: jumpPower (" + fmt(JUMP) + "), moveSpeed (" + fmt(SPEED) + "), enemySpeed (" + fmt(ESPEED) + "), lives (" + lives + "), spawnX/spawnY");
        p.tweak("gravity is the first block of OnCreate (" + fmt(G) + "); jump height = jumpPower^2 / (2 x gravity) = " + fmt(JUMP * JUMP / (2 * G)) + " px");
        p.info("level " + Math.round(LW) + " px long (" + segs.size() + " ground pieces, " + plat + " platforms, " + coin + " coins, " + ne + " enemies)");
        p.meta.put("kind", "platformer");
        p.meta.put("gravity", G);
        p.meta.put("jump", JUMP);
        p.meta.put("speed", SPEED);
        p.meta.put("playerW", PW);
        p.meta.put("playerH", PH);
        p.meta.put("levelW", LW);
        p.meta.put("scrolls", true);
        p.meta.put("enemies", ne);
        return p;
    }
}
