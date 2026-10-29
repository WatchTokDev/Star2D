package com.star4droid.star2d.remote;

import static com.star4droid.star2d.remote.TemplateKit.*;
import static com.star4droid.star2d.remote.TemplateLib.*;

import com.google.gson.JsonArray;

import java.util.Arrays;
import java.util.List;

/**
 * Brick breaker: drag to move the paddle, tap to launch the ball, the ball keeps a constant speed (re-normalised every frame) and
 * leaves the paddle at an angle that depends on where it hit. Rows of coloured bricks, lives, win / game over panel.
 */
final class TemplateBreakout extends Tpl {
    static final int MIN_AIR = 180, WALL = 20, TOP_BAND = 96, BALL_R = 14, PADDLE_H = 24, GAP = 8;
    static final String[] ROW_COLORS = {"#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#00ACC1", "#EC407A"};

    @Override
    String id() {
        return "breakout";
    }

    @Override
    String about() {
        return "Brick breaker: drag the paddle, tap to launch, coloured brick rows, lives, win and game over panels.";
    }

    @Override
    List<P> params() {
        return Arrays.asList(
                new P("rows", "5", "brick rows (3..8, fewer on a short screen)"),
                new P("cols", "8", "bricks per row (5..10)"),
                new P("lives", "3", "start lives (1..9)"),
                new P("speed", "380", "ball speed px/s (250..440)"),
                new P("theme", "night", "day | night | sunset"));
    }

    @Override
    String details() {
        return "Elements: Background, WallL/WallR/WallT, Brick1.. (one per brick, tinted by row), Paddle (kinematic, follows the finger), Ball, TapArea (invisible full-screen button that launches the ball), "
                + "ScoreText, LivesText, StartText, End* panel. Tweak with the variables ballSpeed (380), lives. The ball speed is re-set every frame, so ballSpeed is the real speed; bounce angle comes from the hit position on the paddle.";
    }

    @Override
    String[] samples() {
        return new String[]{"{\"rows\":8,\"cols\":10,\"lives\":9,\"speed\":440,\"theme\":\"day\"}", "{\"rows\":3,\"cols\":5,\"lives\":1,\"speed\":250,\"theme\":\"sunset\"}", "{\"rows\":99,\"cols\":0,\"speed\":1}"};
    }

    @Override
    Plan build(Params prm, int w, int h, String scene) {
        int rows = prm.integer("rows", 5, 3, 8);
        int cols = prm.integer("cols", 8, 5, 10);
        int lives = prm.integer("lives", 3, 1, 9);
        int speed = prm.integer("speed", 380, 250, 440);
        String theme = prm.choice("theme", "night", "day", "night", "sunset");
        prm.unknown(params());
        String[] sky = sky(theme);
        Plan p = new Plan(w, h, w > h, scene, sky[1]);

        double bw = (w - 2.0 * WALL - (cols + 1) * GAP) / cols;
        double bh = Math.max(30, Math.min(48, Math.min(bw * 0.42, h * 0.055)));
        double pw = Math.max(120, Math.min(200, w * 0.2));
        double paddleY = Math.max(120, h * 0.12); // bottom edge of the paddle
        double paddleCy = paddleY + PADDLE_H / 2.0, paddleTop = paddleY + PADDLE_H;
        double brickTop = h - TOP_BAND - 50;
        int maxRows = (int) Math.floor((brickTop - paddleTop - MIN_AIR) / (bh + GAP));
        if (rows > maxRows) {
            prm.notes.add("rows: this screen has room for " + maxRows + " rows, made " + maxRows);
            rows = maxRows;
        }

        // ---------------------------------------------------------------- art
        p.image("brick.png", obj("kind", "shape", "shape", "rounded", "width", 64, "height", 32, "radius", 6, "fill", "linear:#FFFFFF,#BDBDBD,90", "stroke", "#666666", "strokeWidth", 2));
        p.image("paddle.png", obj("kind", "shape", "shape", "rounded", "width", (int) pw, "height", PADDLE_H, "radius", 10, "fill", "linear:#B3E5FC,#0288D1,90", "stroke", "#01579B", "strokeWidth", 2));
        p.image("ball.png", obj("kind", "shape", "shape", "circle", "width", BALL_R * 2, "height", BALL_R * 2, "fill", "radial:#FFFFFF,#FFD54F", "stroke", "#B8860B", "strokeWidth", 2));
        p.image("wall.png", obj("kind", "shape", "shape", "rect", "width", 64, "height", 64, "fill", "linear:#546E7A,#263238,90", "stroke", "#10181C", "strokeWidth", 3));
        p.whitePixel();
        p.sound("click.wav", "click").sound("explosion.wav", "hit").sound("hit.wav", "hit").sound("win.wav", "powerup");

        // ---------------------------------------------------------------- elements
        background(p, sky[0], sky[1]);
        p.add(new El("BOX", "WallT").body("STATIC").at(0, h - TOP_BAND).size(w, TOP_BAND).img("/wall.png").p("tileX", Math.max(1, w / 64)).p("tileY", 2).p("restitution", 1).p("friction", 0).z(Z_GROUND).role("wall"));
        p.add(new El("BOX", "WallL").body("STATIC").at(0, 0).size(WALL, h - TOP_BAND).img("/wall.png").p("tileY", Math.max(1, (h - TOP_BAND) / 64)).p("restitution", 1).p("friction", 0).z(Z_GROUND).role("wall"));
        p.add(new El("BOX", "WallR").body("STATIC").at(w - WALL, 0).size(WALL, h - TOP_BAND).img("/wall.png").p("tileY", Math.max(1, (h - TOP_BAND) / 64)).p("restitution", 1).p("friction", 0).z(Z_GROUND).role("wall"));
        int n = 0;
        for (int row = 0; row < rows; row++)
            for (int c = 0; c < cols; c++) {
                n++;
                double x = WALL + GAP + c * (bw + GAP), y = brickTop - (row + 1) * (bh + GAP) + GAP;
                p.add(new El("BOX", "Brick" + n).body("STATIC").at(x, y).size(bw, bh).img("/brick.png").tint(ROW_COLORS[row % ROW_COLORS.length]).p("restitution", 1).p("friction", 0).z(Z_ITEM).role("brick"));
            }
        p.add(new El("BOX", "Paddle").body("KINAMATIC").at(w / 2.0 - pw / 2.0, paddleY).size(pw, PADDLE_H).img("/paddle.png").p("restitution", 1).p("friction", 0).z(Z_ACTOR).role("paddle"));
        p.add(new El("CIRCLE", "Ball").body("DYNAMIC").at(w / 2.0 - BALL_R, paddleTop + 1).p("radius", BALL_R).img("/ball.png").p("restitution", 1).p("friction", 0).p("Gravity Scale", 0)
                .p("Bullet", true).p("Fixed Rotation", true).z(Z_ACTOR).role("ball"));
        p.add(new El("BOX", "TapArea").body("UI").at(0, 0).size(w, h).img("/white.png").tint("#FFFFFF", 0).z(Z_TAP).role("tap"));

        hud(p, "ScoreText", "Score: 0", "Score: 99999", true, 0, 2.5, "#FFFFFF");
        hud(p, "LivesText", "Lives: " + lives, "Lives: 0", false, 0, 2.5, "#FFFFFF");
        double ts = fitScale("TAP TO LAUNCH", w - 2 * MARGIN, 80, 3);
        El start = p.liveText("StartText", "TAP TO LAUNCH", "TAP TO LAUNCH", 0, 0, textHeight(ts), ts, "#FFFFFF", Z_HUD, true, 100);
        start.p("x", (w - start.num("width", 100)) / 2).p("y", paddleTop + 110);
        endOverlay(p, scene, "GAME OVER", "Score: 99999");

        // ---------------------------------------------------------------- variables
        p.var("score", "int", "0").var("lives", "int", String.valueOf(lives)).var("over", "boolean", "false").var("launched", "boolean", "false").var("remaining", "int", String.valueOf(n));
        p.var("ballSpeed", "float", fmt(speed) + "f");

        // ---------------------------------------------------------------- events
        p.on("OnCreate").add(b("setWorldGravity", "strength", 0));

        p.onElement("TapArea", "onTouchStart", seq(ifThen("!over && !launched",
                setVar("launched", "true"), hide("StartText"),
                code("Ball.getBody().setLinearVelocity((random(0,1) == 0 ? -1f : 1f) * 0.4f * ballSpeed * com.star4droid.template.Items.StageImp.WORLD_SCALE, 0.9f * ballSpeed * com.star4droid.template.Items.StageImp.WORLD_SCALE);"))));

        double half = pw / 2 + WALL;
        JsonArray step = new JsonArray();
        step.add(ifThen(r("screen is touched"), goTo("Paddle", max(half, min(w - half, r("touch x"))), fmt(paddleCy))));
        step.add(ifElse("!launched",
                seq(goTo("Ball", xOf("Paddle"), fmt(paddleTop + BALL_R + 1)), b("stopMoving", "body", "Ball")),
                seq(code("com.badlogic.gdx.math.Vector2 v = Ball.getBody().getLinearVelocity();"),
                        ifThen("v.len() > 0.001f",
                                code("v.nor();"),
                                code("if (Math.abs(v.y) < 0.3f) { v.y = v.y >= 0 ? 0.3f : -0.3f; v.nor(); }"),
                                code("Ball.getBody().setLinearVelocity(v.x * ballSpeed * com.star4droid.template.Items.StageImp.WORLD_SCALE, v.y * ballSpeed * com.star4droid.template.Items.StageImp.WORLD_SCALE);")))));
        step.add(ifThen(lt(yOf("Ball"), -30),
                addVar("lives", -1), setText("LivesText", "\"Lives: \" + lives"), sound("hit.wav"),
                setVar("launched", "false"), show("StartText"),
                ifThen("lives <= 0", setVar("over", "true"), showEnd("\"GAME OVER\"", "\"Score: \" + score"))));
        p.on("OnStep").add(ifThen("!over", seq(step)));

        JsonArray begin = p.on("onCollisionStart");
        begin.add(ifThen(pairStarts("Ball", "Brick"),
                pick("br", "Brick"),
                ifThen("br.getActor().isVisible()", dispose("br", 0.02), addVar("score", 10), addVar("remaining", -1), setText("ScoreText", "\"Score: \" + score"), sound("explosion.wav"),
                        ifThen("remaining <= 0 && !over", setVar("over", "true"), b("stopMoving", "body", "Ball"), sound("win.wav"), showEnd("\"YOU WIN!\"", "\"Score: \" + score")))));
        double pwWorld = pw * WORLD_SCALE / 2;
        begin.add(ifThen(pairEquals("Ball", "Paddle"),
                ifThen("Ball.getBodyY() > Paddle.getBodyY()",
                        code("float off = Math.max(-0.9f, Math.min(0.9f, (Ball.getBodyX() - Paddle.getBodyX()) / " + f(pwWorld) + "));"),
                        code("float ang = (float)Math.toRadians(90 - off * 55);"),
                        code("Ball.getBody().setLinearVelocity((float)Math.cos(ang) * ballSpeed * com.star4droid.template.Items.StageImp.WORLD_SCALE, (float)Math.sin(ang) * ballSpeed * com.star4droid.template.Items.StageImp.WORLD_SCALE);"),
                        sound("click.wav"))));

        p.tweak("variables: ballSpeed (" + speed + "), lives (" + lives + ")");
        p.tweak("controls: drag a finger anywhere to move the paddle, tap to launch");
        p.info(n + " bricks (" + rows + " rows x " + cols + "), paddle " + Math.round(pw) + " px wide");
        p.meta.put("kind", "breakout");
        p.meta.put("scrolls", false);
        p.meta.put("rows", rows);
        p.meta.put("cols", cols);
        p.meta.put("bricks", n);
        p.meta.put("speed", speed);
        p.meta.put("paddleTop", paddleTop);
        p.meta.put("paddleW", pw);
        return p;
    }
}
