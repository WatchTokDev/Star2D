import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.uimaker.*;
import com.star4droid.template.ui.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Renders every UiKit screen (portrait + landscape) with the real runtime under Xvfb. args: projectDir outDir [screen] [theme] */
public class KitRenderTest extends ApplicationAdapter {
    final String proj, out, only, theme;
    int fails;

    KitRenderTest(String[] a) {
        proj = a[0];
        out = a[1];
        only = a.length > 2 ? a[2] : "";
        theme = a.length > 3 ? a[3] : "jelly";
    }

    public static void main(String[] a) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(800, 600);
        c.setResizable(false);
        c.useVsync(false);
        new Lwjgl3Application(new KitRenderTest(a), c);
    }

    @Override
    public void create() {
        try {
            run();
        } catch (Throwable t) {
            t.printStackTrace();
            fails++;
        }
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        Gdx.app.exit();
        System.exit(fails == 0 ? 0 : 1);
    }

    void expect(String what, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + what);
        if (!ok) fails++;
    }

    /** The built-in click actions on the real runtime. */
    void actions(UiKit.Icons icons) throws Exception {
        UIDocument shop = UiKit.build("shop", "shop", JsonParser.parseString("{tabs:['Cars','Boosts']}").getAsJsonObject(), 720, 1280, icons, new ArrayList<>());
        UIView v = new UIView(proj, shop);
        v.setSize(720, 1280);
        expect("shop: first page visible", v.isVisible("page_cars") && !v.isVisible("page_boosts"));
        v.click("tab_boosts");
        expect("shop: tab switches page", !v.isVisible("page_cars") && v.isVisible("page_boosts"));
        expect("shop: tab highlight", "primary".equals(v.node("tab_boosts").str("style", "")) && "ghost".equals(v.node("tab_cars").str("style", "")));
        UIDocument hud = UiKit.build("hud", "hud", null, 1280, 720, icons, new ArrayList<>());
        UIView h = new UIView(proj, hud);
        h.setSize(1280, 720);
        expect("hud: pause hidden", !h.isVisible("pause_overlay"));
        h.click("pause_button");
        expect("hud: pause shown", h.isVisible("pause_overlay"));
        h.click("resume");
        expect("hud: resume hides", !h.isVisible("pause_overlay"));
        UIDocument st = UiKit.build("settings", "settings", null, 720, 1280, icons, new ArrayList<>());
        UIView sv = new UIView(proj, st);
        sv.click("quality_low");
        expect("settings: choice select", "primary".equals(sv.node("quality_low").str("style", "")) && "ghost".equals(sv.node("quality_high").str("style", "")) && sv.isVisible("quality_high"));
        // open / back between files
        UIDocument other = UiKit.build("dialog", "dialog", null, 720, 1280, icons, new ArrayList<>());
        other.write(new File(proj + "/UI/dialog.ui"));
        UIDocument menu = UiKit.build("main_menu", "main_menu", JsonParser.parseString("{buttons:[{text:'Play',open:'dialog'}]}").getAsJsonObject(), 720, 1280, icons, new ArrayList<>());
        UIView m = new UIView(proj, menu);
        m.click("play");
        expect("open: other ui loaded", m.has("dialog_card"));
        m.runAction("back", null);
        expect("back: menu again", m.has("play"));
    }

    void run() throws Exception {
        actions(id -> new File(proj + "/images/ui_" + id.replace('-', '_') + ".png").exists() ? "/ui_" + id.replace('-', '_') + ".png" : "");
        UiPreviewGl gl = new UiPreviewGl(proj);
        UiKit.Icons icons = id -> new File(proj + "/images/ui_" + id.replace('-', '_') + ".png").exists() ? "/ui_" + id.replace('-', '_') + ".png" : "";
        for (String s : UiKit.SCREENS.keySet()) {
            if (!only.isEmpty() && !only.equals(s) && !only.equals("all")) continue;
            for (int o = 0; o < 2; o++) {
                int lw = o == 0 ? 720 : 1280, lh = o == 0 ? 1280 : 720;
                ArrayList<String> notes = new ArrayList<>();
                JsonObject spec = new JsonObject();
                if (s.equals("custom")) spec = JsonParser.parseString("{title:'Profile',currency:'990',sections:[{part:'card',title:'Player',text:'Level 12 explorer'},{part:'stat_bars',bars:[{label:'XP',value:70},{label:'Wins',value:40}]},{part:'list',rows:[{title:'Achievements',icon:'trophy'},{title:'Friends',icon:'account-group',value:'12'}]},{part:'buttons',row:true,buttons:['Edit','Share']}]}").getAsJsonObject();
                UIDocument d = UiKit.build(s, s, spec, lw, lh, icons, notes);
                d.theme.preset = theme;
                List<String> v = d.validate();
                List<String> lint = UiLint.check(d, lw, lh, UILayout.APPROX);
                File f = new File(out, s + "_" + (o == 0 ? "p" : "l") + "_" + theme + ".png");
                UiPreviewGl.Frame fr = gl.render(d.toJsonString(), lw, lh, lw / 2, lh / 2, 0xFF000000, false, 0.016f);
                if (fr.error != null || fr.argb == null) {
                    System.out.println("FAIL " + s + " render " + fr.error);
                    fails++;
                    continue;
                }
                BufferedImage bi = new BufferedImage(lw / 2, lh / 2, BufferedImage.TYPE_INT_ARGB);
                bi.setRGB(0, 0, lw / 2, lh / 2, fr.argb, 0, lw / 2);
                f.getParentFile().mkdirs();
                ImageIO.write(bi, "png", f);
                System.out.println("OK   " + s + " " + lw + "x" + lh + " nodes=" + d.root.countNodes() + (v.isEmpty() ? "" : " validate=" + v) + (lint.isEmpty() ? "" : " lint=" + lint));
            }
        }
    }
}
