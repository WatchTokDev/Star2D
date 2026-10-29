package com.star4droid.star2d.remote;

import static com.star4droid.star2d.remote.TemplateKit.*;
import static com.star4droid.star2d.remote.TemplateLib.*;

import com.google.gson.JsonArray;

import java.util.Arrays;
import java.util.List;

/**
 * Vertical space shooter ("Star Blaster"): drag a finger to steer the ship, it fires by itself, aliens fall from the top and get
 * faster. Works in portrait and landscape (the ship always sits at the bottom, aliens spawn across the whole width).
 */
final class TemplateShooter extends Tpl {
    static final int SW = TemplateArt.SHIP.w(), SH = TemplateArt.SHIP.h(), AW = TemplateArt.ALIEN.w(), AH = TemplateArt.ALIEN.h(), BW = 10, BH = 28, SHIP_Y = 130;
    static final double BULLET_SPEED = 420, ALIEN_SPEED = 110, ALIEN_MAX = 220, SHIP_SPEED = 400;

    @Override
    String id() {
        return "space_shooter";
    }

    @Override
    String about() {
        return "Vertical space shooter: drag to steer, auto-fire, aliens fall faster and faster, score + best score, lives, game over panel.";
    }

    @Override
    List<P> params() {
        return Arrays.asList(
                new P("lives", "3", "start lives (1..9)"),
                new P("spawn", "1", "seconds between aliens (0.4..3)"),
                new P("fire", "0.3", "seconds between shots (0.15..1)"),
                new P("theme", "space", "space | nebula | mars"));
    }

    @Override
    String details() {
        return "Elements: Background, Stars, Base (sensor at the bottom edge: an alien reaching it costs a life), Ship (kinematic, follows your finger), BulletT / AlienT (off-screen templates that are cloned), "
                + "ScoreText, LivesText, End* panel. Aliens that touch the ship or reach the bottom cost a life. Tweak with the variables bulletSpeed (420), alienSpeed (starts 110, +12 every 10 s up to 220), shipSpeed (400), lives; "
                + "the two timers in OnCreate hold the spawn / fire intervals.";
    }

    @Override
    String[] samples() {
        return new String[]{"{\"lives\":9,\"spawn\":0.4,\"fire\":0.15,\"theme\":\"nebula\"}", "{\"lives\":1,\"spawn\":3,\"fire\":1,\"theme\":\"mars\"}", "{\"spawn\":0,\"fire\":99,\"theme\":\"x\"}"};
    }

    @Override
    Plan build(Params prm, int w, int h, String scene) {
        int lives = prm.integer("lives", 3, 1, 9);
        double spawn = prm.num("spawn", 1, 0.4, 3);
        double fire = prm.num("fire", 0.3, 0.15, 1);
        String theme = prm.choice("theme", "space", "space", "nebula", "mars");
        prm.unknown(params());
        String top, bottom;
        switch (theme) {
            case "nebula": top = "#1B0B3A"; bottom = "#5B2A86"; break;
            case "mars": top = "#2A0D0D"; bottom = "#8A3A1E"; break;
            default: top = "#050816"; bottom = "#1B2A5C"; break;
        }
        Plan p = new Plan(w, h, w > h, scene, top);

        // ---------------------------------------------------------------- art
        TemplateArt.put(p, "ship.png", TemplateArt.SHIP);
        TemplateArt.put(p, "alien.png", TemplateArt.ALIEN);
        p.image("bullet.png", obj("kind", "shape", "shape", "rounded", "width", BW, "height", BH, "radius", 5, "fill", "linear:#FFFFFF,#FFD54F,90"));
        TemplateArt.starfield(p, "stars.png", w, h, w * 13L + h);
        p.whitePixel();
        p.sound("explosion.wav", "explosion").sound("hit.wav", "hit");

        // ---------------------------------------------------------------- elements
        background(p, top, bottom);
        p.add(new El("BOX", "Stars").body("UI").at(0, 0).size(w, h).img("/stars.png").z(Z_BG + 1).role("decor"));
        p.add(new El("BOX", "Base").body("STATIC").at(0, 0).size(w, 16).img("/white.png").tint("#FFFFFF", 0).p("isSensor", true).z(Z_GROUND).role("wall"));
        // off-screen templates (cloned by spawnCopy); the Collision text lists TEMPLATE names that must not collide with each other
        p.add(new El("BOX", "BulletT").body("DYNAMIC").at(-300, -300).size(BW, BH).img("/bullet.png").p("Gravity Scale", 0).p("Bullet", true).p("restitution", 0)
                .p("Collision", "(Ship)(BulletT)").z(Z_ACTOR - 2).role("bullet").off());
        p.add(new El("BOX", "AlienT").body("DYNAMIC").at(-300, -100).size(AW, AH).img("/alien.png").p("Gravity Scale", 0).p("Fixed Rotation", true).p("restitution", 0).p("friction", 0)
                .p("Collision", "(AlienT)").z(Z_ACTOR - 5).role("enemy").off());
        p.add(new El("BOX", "Ship").body("KINAMATIC").at(w / 2.0 - SW / 2.0, SHIP_Y - SH / 2.0).size(SW, SH).img("/ship.png").p("restitution", 0).p("Collision", "(BulletT)").z(Z_ACTOR).role("player"));
        hud(p, "ScoreText", "Score: 0", "Score: 99999", true, 0, 2.5, "#FFFFFF");
        hud(p, "LivesText", "Lives: " + lives, "Lives: 0", false, 0, 2.5, "#FFFFFF");
        endOverlay(p, scene, "GAME OVER", "Score: 99999   Best: 99999");

        // ---------------------------------------------------------------- variables
        p.var("score", "int", "0").var("lives", "int", String.valueOf(lives)).var("over", "boolean", "false").var("shots", "int", "0").var("spawned", "int", "0");
        p.var("bulletSpeed", "float", fmt(BULLET_SPEED) + "f").var("alienSpeed", "float", fmt(ALIEN_SPEED) + "f").var("shipSpeed", "float", fmt(SHIP_SPEED) + "f");

        // ---------------------------------------------------------------- scene events
        JsonArray create = p.on("OnCreate");
        create.add(b("setWorldGravity", "strength", 0));
        create.add(every(fmt(fire), ifThen("!over",
                addVar("shots", 1),
                b("spawnCopy", "body", "BulletT", "var", "b", "newName", "\"bullet\" + shots", "x", xOf("Ship"), "y", plus(yOf("Ship"), SH / 2.0 + BH / 2.0)),
                velY("b", "bulletSpeed"),
                lifetime("b", fmt((h / BULLET_SPEED) + 1)))));
        create.add(every(fmt(spawn), ifThen("!over",
                addVar("spawned", 1),
                b("spawnCopy", "body", "AlienT", "var", "e", "newName", "\"alien\" + spawned", "x", r("random ___ to ___", AW, w - AW), "y", h + 40),
                velY("e", "-alienSpeed"),
                lifetime("e", "(" + (h + 120) + ") / alienSpeed + 1"))));
        create.add(every(10, ifThen("!over", setVar("alienSpeed", "Math.min(" + f(ALIEN_MAX) + ", alienSpeed + 12f)"))));

        JsonArray step = new JsonArray();
        step.add(ifElse(r("screen is touched"),
                seq(velX("Ship", max("-shipSpeed", min("shipSpeed", mul(minus(r("touch x"), xOf("Ship")), 8))))),
                seq(velX("Ship", 0))));
        step.add(goTo("Ship", max(SW / 2.0 + 8, min(w - SW / 2.0 - 8, xOf("Ship"))), SHIP_Y));
        p.on("OnStep").add(ifThen("!over", seq(step)));

        String endSub = "\"Score: \" + score + \"   Best: \" + getNumber(\"best\")";
        JsonArray lose = seq(addVar("lives", -1), setText("LivesText", "\"Lives: \" + lives"), sound("hit.wav"),
                ifThen("lives <= 0 && !over", setVar("over", "true"),
                        ifThen("score > getNumber(\"best\")", b("saveData", "key", "best", "value", "score")),
                        b("stopMoving", "body", "Ship"),
                        showEnd("\"GAME OVER\"", endSub)));
        JsonArray begin = p.on("onCollisionStart");
        begin.add(ifThen(pairPrefixes("bullet", "alien"),
                code("final PlayerItem bl = body1.getName().startsWith(\"bullet\") ? body1 : body2;"),
                code("final PlayerItem al = bl == body1 ? body2 : body1;"),
                ifThen("bl.getActor().isVisible() && al.getActor().isVisible()", dispose("bl", 0.02), dispose("al", 0.02), addVar("score", 10),
                        setText("ScoreText", "\"Score: \" + score"), sound("explosion.wav"))));
        begin.add(ifThen(or(pairStarts("Ship", "alien"), pairStarts("Base", "alien")),
                pick("al", "alien"),
                ifThen("al.getActor().isVisible()", dispose("al", 0.02), lose)));

        p.tweak("variables: bulletSpeed (420), alienSpeed (110, rises to 220), shipSpeed (400), lives (" + lives + "); spawn/fire intervals are in the two timers of OnCreate (" + fmt(spawn) + " s / " + fmt(fire) + " s)");
        p.tweak("controls: drag a finger anywhere, the ship follows its x; it shoots by itself");
        p.info("ship at the bottom, aliens come from the top across the whole " + w + " px width");
        p.meta.put("kind", "space_shooter");
        p.meta.put("scrolls", false);
        p.meta.put("fire", fire);
        p.meta.put("spawn", spawn);
        p.meta.put("bulletSpeed", BULLET_SPEED);
        return p;
    }
}
