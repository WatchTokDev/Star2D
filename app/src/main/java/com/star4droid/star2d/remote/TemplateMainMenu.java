package com.star4droid.star2d.remote;

import static com.star4droid.star2d.remote.TemplateKit.*;
import static com.star4droid.star2d.remote.TemplateLib.*;

import java.util.Arrays;
import java.util.List;

/**
 * Title screen: big title, a Play button that opens another scene, optional Quit button. No physics.
 * The game always starts at scene1, so make the menu scene1 (the reply says it).
 */
final class TemplateMainMenu extends Tpl {
    @Override
    String id() {
        return "main_menu";
    }

    @Override
    String about() {
        return "Title screen: game title, PLAY button that opens another scene, optional QUIT button.";
    }

    @Override
    List<P> params() {
        return Arrays.asList(
                new P("title", "MY GAME", "title text (ASCII, up to 20 chars)"),
                new P("subtitle", "Tap play to start", "small line under the title (ASCII, up to 30 chars, empty = none)"),
                new P("play_scene", "scene2", "scene opened by PLAY (must exist when the game runs)"),
                new P("quit", "1", "1 = show a QUIT button, 0 = no"),
                new P("theme", "night", "day | night | sunset"));
    }

    @Override
    String details() {
        return "Elements: Background, Cloud1.. (day) or a star layer, TitleText, SubText, PlayBtn (+PlayBtnLabel), QuitBtn (+QuitBtnLabel). "
                + "PLAY runs openScene(play_scene). The project starts at scene1, so apply this to scene1 (or move the menu there). Create the play scene first or afterwards, but before running the game.";
    }

    @Override
    String[] samples() {
        return new String[]{"{\"title\":\"SUPER LONG GAME TITLE HERE\",\"subtitle\":\"\",\"quit\":0,\"theme\":\"day\"}", "{\"title\":\"Hi\",\"play_scene\":\"level_1\",\"theme\":\"sunset\"}", "{\"quit\":\"x\",\"title\":\"\\u0645\\u0631\\u062d\\u0628\\u0627\"}"};
    }

    @Override
    Plan build(Params prm, int w, int h, String scene) {
        String title = prm.text("title", "MY GAME", 20);
        String sub = prm.text("subtitle", "Tap play to start", 30);
        String playScene = prm.sceneName("play_scene", "scene2");
        int quit = prm.integer("quit", 1, 0, 1);
        String theme = prm.choice("theme", "night", "day", "night", "sunset");
        prm.unknown(params());
        String[] sky = sky(theme);
        Plan p = new Plan(w, h, w > h, scene, sky[1]);

        p.whitePixel();
        p.sound("click.wav", "click");
        background(p, sky[0], sky[1]);
        if (theme.equals("day")) clouds(p, 4, w * 5L + h);
        else {
            TemplateArt.starfield(p, "stars.png", w, h, w * 11L + h);
            p.add(new El("BOX", "Stars").body("UI").at(0, 0).size(w, h).img("/stars.png").z(Z_BG + 1).role("decor"));
        }

        // title, as big as the screen allows
        double ts = fitScale(title, w - 2 * MARGIN, 160, 5);
        double th = textHeight(ts);
        double titleY = h * (w > h ? 0.62 : 0.66);
        El t = p.liveText("TitleText", title, title, 0, titleY, th, ts, "#FFFFFF", Z_HUD, true, 100);
        t.p("x", (w - t.num("width", 100)) / 2);
        double bottomOfText = titleY;
        if (!sub.isEmpty()) {
            double ss = 1.8, sh = textHeight(ss);
            El s = p.liveText("SubText", sub, sub, 0, titleY - sh - 16, sh, ss, "#E0E6F5", Z_HUD, true, 100);
            s.p("x", (w - s.num("width", 100)) / 2);
            bottomOfText = titleY - sh - 16;
        }
        double bw = Math.min(360, w - 2 * MARGIN), bh = 100;
        double playY = Math.min(bottomOfText - 40 - bh, h * 0.4);
        playY = Math.max(playY, quit == 1 ? MARGIN + bh + 30 : MARGIN);
        p.button("PlayBtn", "PLAY", (w - bw) / 2, playY, bw, bh, "#3DDC84", "#1E9E5A", "#FFFFFF", 3, Z_CTRL);
        p.onElement("PlayBtn", "onClick", seq(sound("click.wav"), openScene(playScene)));
        if (quit == 1) {
            double qh = 84, qw = Math.min(280, w - 2 * MARGIN);
            p.button("QuitBtn", "QUIT", (w - qw) / 2, playY - qh - 30, qw, qh, "#E57373", "#B23A3A", "#FFFFFF", 2.5, Z_CTRL);
            p.onElement("QuitBtn", "onClick", seq(sound("click.wav"), code("finish();")));
        }

        p.tweak("PLAY opens scene '" + playScene + "' (create it before running the game)");
        p.tweak("the game starts at scene1: this menu must be scene1");
        p.info("title '" + title + "'" + (quit == 1 ? ", with QUIT" : ""));
        p.meta.put("kind", "main_menu");
        p.meta.put("scrolls", false);
        return p;
    }
}
