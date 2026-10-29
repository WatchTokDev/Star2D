package com.star4droid.star2d.remote;

import static com.star4droid.star2d.remote.TemplateKit.*;
import static com.star4droid.star2d.remote.TemplateLib.*;

import com.google.gson.JsonArray;

import java.util.Arrays;
import java.util.List;

/**
 * Flappy-style one-tap game: tap anywhere to flap, pipes scroll in from the right, the score counts passed pipes, best score is saved.
 * A pool of pipe pairs is recycled (no spawning at run time). The gap centre can move only as much as the bird can climb in the
 * time between two pipes, so every pipe pair is passable.
 */
final class TemplateFlappy extends Tpl {
    static final int BW = TemplateArt.BIRD.w(), BH = TemplateArt.BIRD.h(), PIPE_W = 110, GH = 96;
    static final double GRAVITY = 1100, FLAP = 380;

    @Override
    String id() {
        return "flappy";
    }

    @Override
    String about() {
        return "One-tap flapper: tap anywhere to flap, endless pipes, score + saved best score, game over panel.";
    }

    @Override
    List<P> params() {
        return Arrays.asList(
                new P("gap", "300", "opening between the pipes in px (220..420)"),
                new P("speed", "200", "pipe speed px/s (120..300)"),
                new P("theme", "day", "day | night | sunset"));
    }

    @Override
    String details() {
        return "Elements: Background, Cloud1.., Ground, Ceiling (invisible), Bird, PipeTop1../PipeBot1.. (kinematic, recycled), TapArea (invisible full-screen button), ScoreText, StartText, End* panel. "
                + "Tweak with the variables flapPower (380), pipeSpeed (200), gap (300); one hit ends the game. The pipes do not move until the first tap.";
    }

    @Override
    String[] samples() {
        return new String[]{"{\"gap\":420,\"speed\":300,\"theme\":\"night\"}", "{\"gap\":220,\"speed\":120,\"theme\":\"sunset\"}", "{\"gap\":1,\"speed\":9999}"};
    }

    @Override
    Plan build(Params prm, int w, int h, String scene) {
        int gap = prm.integer("gap", 300, 220, 420);
        int speed = prm.integer("speed", 200, 120, 300);
        String theme = prm.choice("theme", "day", "day", "night", "sunset");
        prm.unknown(params());
        String[] sky = sky(theme);
        Plan p = new Plan(w, h, w > h, scene, sky[1]);
        Rng r = new Rng(w * 19L + h + gap);

        double spacing = Math.max(360, Math.min(520, w * 0.55));
        int pairs = (int) Math.ceil((w + 2.0 * PIPE_W) / spacing) + 1;
        double cMin = GH + gap / 2.0 + 50, cMax = h - gap / 2.0 - 50;
        // the bird can climb about FLAP px per tap, one tap every 0.4 s: limit the move of the opening between two pipes
        double travel = spacing / speed;
        double canClimb = (FLAP * FLAP / (2 * GRAVITY)) * Math.max(1, Math.floor(travel / 0.4));
        cMax = Math.min(cMax, cMin + canClimb / 1.3);
        if (cMax < cMin + 20) cMax = cMin + 20;
        double birdX = Math.round(w * 0.3);
        double firstPipeX = w + 80;

        // ---------------------------------------------------------------- art
        TemplateArt.put(p, "bird.png", TemplateArt.BIRD);
        p.image("pipe.png", obj("kind", "shape", "shape", "rect", "width", PIPE_W, "height", 256, "fill", "linear:#2E8B2E,#8EE05A,#2E8B2E,0", "stroke", "#1E5E1E", "strokeWidth", 4));
        p.image("ground.png", obj("kind", "shape", "shape", "rect", "width", 96, "height", GH, "fill", "linear:#8DDB57,#5FAF3A,#D9B26B,#B8894A,90", "stroke", "#6B4A22", "strokeWidth", 3));
        p.whitePixel();
        p.sound("jump.wav", "blip").sound("coin.wav", "coin").sound("hit.wav", "hit");

        // ---------------------------------------------------------------- screen layer + world
        background(p, sky[0], sky[1]);
        clouds(p, 3, w * 7L + h);
        p.add(new El("BOX", "Ground").body("STATIC").at(0, 0).size(w, GH).img("/ground.png").p("tileX", Math.max(1, Math.round(w / 96.0))).p("restitution", 0).z(Z_GROUND + 2).role("ground"));
        p.add(new El("BOX", "Ceiling").body("STATIC").at(0, h).size(w, 40).img("/white.png").tint("#FFFFFF", 0).p("restitution", 0).z(Z_GROUND).role("wall").off());
        double[] gc = new double[pairs];
        for (int i = 0; i < pairs; i++) {
            gc[i] = r.next((int) cMin, (int) cMax);
            double x = firstPipeX + i * spacing;
            p.add(new El("BOX", "PipeTop" + (i + 1)).body("KINAMATIC").at(x, gc[i] + gap / 2.0).size(PIPE_W, h).img("/pipe.png").p("restitution", 0).z(Z_GROUND + 1).role("pipe"));
            p.add(new El("BOX", "PipeBot" + (i + 1)).body("KINAMATIC").at(x, gc[i] - gap / 2.0 - h).size(PIPE_W, h).img("/pipe.png").p("restitution", 0).z(Z_GROUND + 1).role("pipe"));
        }
        p.add(new El("BOX", "Bird").body("DYNAMIC").at(birdX - BW / 2.0, h * 0.55).size(BW, BH).img("/bird.png").p("Collider Width", BW - 8).p("Collider Height", BH - 6)
                .p("restitution", 0).p("friction", 0).p("Fixed Rotation", true).p("Gravity Scale", 0).z(Z_ACTOR).role("player"));
        p.add(new El("BOX", "TapArea").body("UI").at(0, 0).size(w, h).img("/white.png").tint("#FFFFFF", 0).z(Z_TAP).role("tap"));

        // ---------------------------------------------------------------- HUD
        double ss = 5;
        double sh = textHeight(ss);
        El score = p.liveText("ScoreText", "0", "000", 0, 0, sh, ss, "#FFFFFF", Z_HUD, true, 120);
        score.p("x", (w - score.num("width", 120)) / 2).p("y", h - MARGIN - sh - 20);
        double ts = fitScale("TAP TO START", w - 2 * MARGIN, 80, 3);
        El start = p.liveText("StartText", "TAP TO START", "TAP TO START", 0, 0, textHeight(ts), ts, "#FFFFFF", Z_HUD, true, 100);
        start.p("x", (w - start.num("width", 100)) / 2).p("y", h * 0.40);
        endOverlay(p, scene, "GAME OVER", "Score: 99999   Best: 99999");

        // ---------------------------------------------------------------- variables
        p.var("score", "int", "0").var("over", "boolean", "false").var("started", "boolean", "false");
        p.var("flapPower", "float", fmt(FLAP) + "f").var("pipeSpeed", "float", fmt(speed) + "f").var("gap", "float", fmt(gap) + "f");
        for (int i = 1; i <= pairs; i++) p.var("gc" + i, "int", String.valueOf((int) gc[i - 1])).var("pass" + i, "boolean", "false");

        // ---------------------------------------------------------------- events
        p.on("OnCreate").add(b("setWorldGravity", "strength", fmt(GRAVITY)));

        JsonArray go = new JsonArray();
        for (int i = 1; i <= pairs; i++) {
            go.add(velX("PipeTop" + i, "-pipeSpeed"));
            go.add(velX("PipeBot" + i, "-pipeSpeed"));
        }
        p.onElement("TapArea", "onTouchStart", seq(ifThen("!over",
                ifThen("!started", setVar("started", "true"), b("setGravityScaleOf", "body", "Bird", "scale", 1), go, hide("StartText")),
                velY("Bird", "flapPower"), sound("jump.wav"))));

        JsonArray step = new JsonArray();
        for (int i = 1; i <= pairs; i++) {
            int prev = i == 1 ? pairs : i - 1;
            String t = "PipeTop" + i, bo = "PipeBot" + i;
            step.add(ifThen(lt(xOf(t), -PIPE_W),
                    setVar("gc" + i, r("random ___ to ___", (int) cMin, (int) cMax)),
                    goTo(t, plus(xOf("PipeTop" + prev), fmt(spacing)), "gc" + i + " + gap / 2f + " + fmt(h / 2.0)),
                    goTo(bo, xOf(t), "gc" + i + " - gap / 2f - " + fmt(h / 2.0)),
                    setVar("pass" + i, "false")));
            step.add(ifThen(and("!pass" + i, lt(xOf(t), fmt(birdX - PIPE_W / 2.0))),
                    setVar("pass" + i, "true"), addVar("score", 1), setText("ScoreText", "String.valueOf(score)"), sound("coin.wav")));
        }
        p.on("OnStep").add(ifThen("started && !over", seq(step)));

        JsonArray stop = new JsonArray();
        for (int i = 1; i <= pairs; i++) {
            stop.add(b("stopMoving", "body", "PipeTop" + i));
            stop.add(b("stopMoving", "body", "PipeBot" + i));
        }
        String hit = "(" + pairStarts("Bird", "Pipe") + ") || (" + pairEquals("Bird", "Ground") + ") || (" + pairEquals("Bird", "Ceiling") + ")";
        p.on("onCollisionStart").add(ifThen("!over && (" + hit + ")",
                setVar("over", "true"), stop, sound("hit.wav"),
                ifThen("score > getNumber(\"best\")", b("saveData", "key", "best", "value", "score")),
                showEnd("\"GAME OVER\"", "\"Score: \" + score + \"   Best: \" + getNumber(\"best\")")));

        p.tweak("variables: flapPower (380), pipeSpeed (" + speed + "), gap (" + gap + ")");
        p.tweak("the pipes start moving with the first tap; best score is saved with saveData('best')");
        p.info(pairs + " pipe pairs recycled, spacing " + Math.round(spacing) + " px, opening centre moves at most " + Math.round(cMax - cMin) + " px");
        p.meta.put("kind", "flappy");
        p.meta.put("scrolls", true);
        p.meta.put("gravity", GRAVITY);
        p.meta.put("flap", FLAP);
        p.meta.put("gap", gap);
        p.meta.put("speed", speed);
        p.meta.put("spacing", spacing);
        p.meta.put("cMin", cMin);
        p.meta.put("cMax", cMax);
        p.meta.put("pairs", pairs);
        p.meta.put("groundTop", GH);
        return p;
    }
}
