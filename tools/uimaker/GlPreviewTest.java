import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.star4droid.star2d.ui.uimaker.*;
import com.star4droid.template.ui.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.*;
import javax.imageio.ImageIO;

/** Real offscreen render of the UI Maker preview (UiPreviewGl) under Xvfb. args: outDir */
public class GlPreviewTest extends ApplicationAdapter {
    final String out;
    int fails;

    GlPreviewTest(String[] a) {
        out = a[0];
    }

    public static void main(String[] a) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(1000, 700);
        c.setResizable(false);
        c.useVsync(false);
        new Lwjgl3Application(new GlPreviewTest(a), c);
    }

    void expect(String what, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + what);
        if (!ok) fails++;
    }

    static void png(int[] argb, int w, int h, File f) throws Exception {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, w, h, argb, 0, w);
        f.getParentFile().mkdirs();
        ImageIO.write(bi, "png", f);
    }

    static int distinct(int[] px) {
        HashSet<Integer> s = new HashSet<>();
        for (int p : px) {
            s.add(p);
            if (s.size() > 50) break;
        }
        return s.size();
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

    void run() throws Exception {
        UiPreviewGl gl = new UiPreviewGl("/tmp/uimaker_proj");
        IntBuffer vb0 = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
        Gdx.gl.glViewport(10, 20, 300, 200);
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, vb0);
        int[] before = {vb0.get(0), vb0.get(1), vb0.get(2), vb0.get(3)};

        for (String t : UiTemplates.names()) {
            for (int o = 0; o < 2; o++) {
                int lw = o == 0 ? 720 : 1280, lh = o == 0 ? 1280 : 720; int pw = lw / 2, ph = lh / 2;
                UIDocument d = UiTemplates.document(t, t, "jelly");
                UiPreviewGl.Frame f = gl.render(d.toJsonString(), lw, lh, pw, ph, 0xFF1C2230, false, 0.016f);
                expect(t + " renders " + lw + "x" + lh + " at " + pw + "x" + ph + (f.error != null ? " (" + f.error + ")" : ""), f.error == null && f.argb != null);
                if (f.argb == null) continue;
                expect(t + " has content (" + distinct(f.argb) + " colors)", distinct(f.argb) > 6);
                png(f.argb, pw, ph, new File(out, t + (o == 0 ? "_p" : "_l") + ".png"));
                expect(t + " rects for all nodes", f.rects.size() == d.allNodes().size());
                // the GL rects must agree with the wireframe rects within a tolerance for sized nodes
                UiRects glr = UiRects.fromMap(d, lw, lh, f.rects);
                int bad = 0;
                for (UINode n : d.allNodes()) {
                    UiRects.R r = glr.get(n.id);
                    if (r == null || !r.shown) { bad++; continue; }
                    if (n.parent != null && (r.x < -400 || r.y < -400 || r.x > lw + 4000)) bad++;
                }
                expect(t + " rect sanity", bad == 0);
            }
        }

        // viewport restored
        gl.render(UiTemplates.document("v", "hud", "dark").toJsonString(), 720, 1280, 360, 640, 0xFF000000, false, 0.016f);
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, vb0);
        expect("viewport restored", vb0.get(0) == before[0] && vb0.get(1) == before[1] && vb0.get(2) == before[2] && vb0.get(3) == before[3]);

        // scaled render (preview smaller than logical)
        UiPreviewGl.Frame half = gl.render(UiTemplates.document("v", "main_menu", "candy").toJsonString(), 720, 1280, 360, 640, 0xFF202020, false, 0.016f);
        png(half.argb, 360, 640, new File(out, "main_menu_half.png"));
        expect("half size has content", half.error == null && distinct(half.argb) > 6);
        UiRects hr = UiRects.fromMap(UiTemplates.document("v", "main_menu", "candy"), 720, 1280, half.rects);
        UiRects.R play = hr.get("play");
        expect("play button rect in logical px", play != null && play.shown && play.w > 100 && play.x >= 0 && play.x + play.w <= 720 + 1);

        // themes
        for (String th : UITheme.PRESETS) {
            UIDocument d = UiTemplates.document("t", "settings_panel", th);
            UiPreviewGl.Frame f = gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF303030, false, 0.016f);
            png(f.argb, 360, 640, new File(out, "theme_" + th + ".png"));
            expect("theme " + th, f.error == null && distinct(f.argb) > 6);
        }

        // test mode: touch a button, move a slider, toggle a switch
        UIDocument d = UiTemplates.document("t", "settings_panel", "jelly");
        UiPreviewGl.Frame f = gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, true, 0.016f);
        UIView v = gl.view();
        final ArrayList<String> log = new ArrayList<>();
        v.setEvents(new UIEventsAdapter() {
            public void onClick(UIView ui, String id) { log.add("click:" + id); }
            public void onChange(UIView ui, String id, Object value) { log.add("change:" + id); }
        });
        UiRects r = UiRects.fromMap(d, 720, 1280, f.rects);
        UiRects.R back = r.get("back");
        expect("back found", back != null && back.shown);
        float bx = back.x + back.w / 2, by = back.y + back.h / 2; // logical
        UiRects.R hit0 = r.hit(bx, by);
        UiRects.R hit = r.hit(bx, by);
        expect("hit test finds back", hit != null && hit.node.id.equals("back"));
        gl.touch(0, bx / 2, by / 2);
        gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, true, 0.02f);
        gl.touch(2, bx / 2, by / 2);
        gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, true, 0.02f);
        expect("test mode click reached the UI " + log, log.contains("click:back"));

        UiRects.R sw = r.get("vibration");
        log.clear();
        gl.touch(0, (sw.x + sw.w / 2) / 2, (sw.y + sw.h / 2) / 2);
        gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, true, 0.02f);
        gl.touch(2, (sw.x + sw.w / 2) / 2, (sw.y + sw.h / 2) / 2);
        gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, true, 0.02f);
        expect("switch toggled " + log, log.contains("change:vibration"));

        UiRects.R sl = r.get("music");
        log.clear();
        float sy = sl.y + sl.h / 2;
        gl.touch(0, (sl.x + 10) / 2, sy / 2);
        for (int i = 1; i <= 8; i++) {
            gl.touch(1, (sl.x + 10 + i * (sl.w - 20) / 8f) / 2, sy / 2);
            gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, true, 0.02f);
        }
        gl.touch(2, (sl.x + sl.w - 10) / 2, sy / 2);
        gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, true, 0.02f);
        expect("slider dragged " + log + " value=" + v.getValue("music"), log.contains("change:music") && v.getValue("music") > 0.8f);

        // keepState false rebuilds (value back to the document's)
        gl.render(d.toJsonString(), 720, 1280, 360, 640, 0xFF202020, false, 0.02f);
        expect("rebuild resets live values", Math.abs(gl.view().getValue("music") - 0.5f) < 0.01f);

        // scroll view
        UIDocument lv = UiTemplates.document("lv", "level_select", "flat_3d");
        UiPreviewGl.Frame lf = gl.render(lv.toJsonString(), 640, 360, 640, 360, 0xFF202020, true, 0.02f);
        png(lf.argb, 640, 360, new File(out, "level_select_before.png"));
        UiRects lr = UiRects.fromMap(lv, 640, 360, lf.rects);
        UiRects.R sc = lr.get("levels_scroll");
        gl.touch(0, sc.x + sc.w / 2, sc.y + sc.h - 20);
        for (int i = 1; i <= 10; i++) {
            gl.touch(1, sc.x + sc.w / 2, sc.y + sc.h - 20 - i * 30);
            gl.render(lv.toJsonString(), 640, 360, 640, 360, 0xFF202020, true, 0.02f);
        }
        gl.touch(2, sc.x + sc.w / 2, sc.y + 20);
        UiPreviewGl.Frame af = gl.render(lv.toJsonString(), 640, 360, 640, 360, 0xFF202020, true, 0.02f);
        png(af.argb, 640, 360, new File(out, "level_select_scrolled.png"));
        float[] l12a = lf.rects.get("level1"), l12b = af.rects.get("level1");
        expect("scroll moved the content (" + l12a[1] + " -> " + l12b[1] + ")", Math.abs(l12a[1] - l12b[1]) > 1);

        gl.dispose();
    }
}
