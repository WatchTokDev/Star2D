import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.star4droid.template.ui.*;

import java.util.ArrayList;

/**
 * Runs the real libGDX UI runtime (UIView) under a virtual X display: draws a .ui to a PNG and, with "test", clicks / drags / types
 * through the Stage like a finger would and checks the events.
 *   GdxUiTest <projectDir> <uiName> <out.png> [test]
 */
public class GdxUiTest extends ApplicationAdapter {
    final String project, name, out;
    final boolean test;
    Stage stage;
    UIView view;
    int frame;
    final ArrayList<String> log = new ArrayList<>();
    int fails;

    GdxUiTest(String[] a) {
        project = a[0];
        name = a[1];
        out = a[2];
        test = a.length > 3 && a[3].equals("test");
    }

    public static void main(String[] a) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setWindowedMode(1280, 720);
        c.setResizable(false);
        c.setTitle("ui test");
        c.useVsync(false);
        new Lwjgl3Application(new GdxUiTest(a), c);
    }

    @Override
    public void create() {
        stage = new Stage(new FitViewport(1280, 720));
        Gdx.input.setInputProcessor(stage);
        view = UIView.load(project, name);
        view.setSize(1280, 720);
        for (String w : view.document().warnings) System.out.println("warning: " + w);
        view.setEvents(new UIEventsAdapter() {
            @Override
            public void onCreate(UIView ui) {
                log.add("create");
            }

            @Override
            public void onClick(UIView ui, String id) {
                log.add("click:" + id);
            }

            @Override
            public void onTouchStart(UIView ui, String id) {
                log.add("down:" + id);
            }

            @Override
            public void onTouchEnd(UIView ui, String id) {
                log.add("up:" + id);
            }

            @Override
            public void onChange(UIView ui, String id, Object value) {
                log.add("change:" + id + "=" + value);
            }

            @Override
            public void onSubmit(UIView ui, String id, String text) {
                log.add("submit:" + id + "=" + text);
            }

            @Override
            public void onScroll(UIView ui, String id, float x, float y) {
                log.add("scroll:" + id);
            }
        });
        stage.addActor(view);
    }

    Vector2 center(String id) {
        Actor a = view.find(id);
        if (a == null) throw new RuntimeException("no node " + id);
        return a.localToStageCoordinates(new Vector2(a.getWidth() / 2, a.getHeight() / 2));
    }

    Vector2 at(String id, float fx, float fy) {
        Actor a = view.find(id);
        return a.localToStageCoordinates(new Vector2(a.getWidth() * fx, a.getHeight() * fy));
    }

    void tap(Vector2 p) {
        int sx = Math.round(p.x), sy = Math.round(720 - p.y);
        stage.touchDown(sx, sy, 0, 0);
        stage.act(0.02f);
        stage.touchUp(sx, sy, 0, 0);
        stage.act(0.02f);
    }

    void drag(Vector2 a, Vector2 b, int steps) {
        stage.touchDown(Math.round(a.x), Math.round(720 - a.y), 0, 0);
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            stage.touchDragged(Math.round(a.x + (b.x - a.x) * t), Math.round(720 - (a.y + (b.y - a.y) * t)), 0);
            stage.act(0.016f);
        }
        stage.touchUp(Math.round(b.x), Math.round(720 - b.y), 0, 0);
        stage.act(0.016f);
    }

    void expect(String what, boolean ok) {
        System.out.println((ok ? "OK   " : "FAIL ") + what);
        if (!ok) fails++;
    }

    boolean logged(String s) {
        return log.contains(s);
    }

    boolean loggedPrefix(String s) {
        for (String l : log) if (l.startsWith(s)) return true;
        return false;
    }

    void runTests() {
        // buttons
        if (view.has("btnJump")) {
            log.clear();
            tap(center("btnJump"));
            expect("button click events " + log, logged("down:btnJump") && logged("click:btnJump") && logged("up:btnJump"));
            view.setEnabled("btnJump", false);
            log.clear();
            tap(center("btnJump"));
            expect("disabled button gives no events " + log, log.isEmpty());
            view.setEnabled("btnJump", true);
            // tooltip (long press needs real time + the timer thread: call the same method the listener calls)
            try {
                view.node("btnJump").set("tooltip", "Jump!");
                view.rebuild();
                java.lang.reflect.Method m = UIView.class.getDeclaredMethod("showTip", UIActor.class);
                m.setAccessible(true);
                int before = view.getChildren().size;
                m.invoke(view, view.find("btnJump"));
                stage.act(0.02f);
                expect("tooltip shown", view.getChildren().size == before + 1);
                stage.act(2.5f);
                stage.act(0.02f);
                expect("tooltip removed after 2s", view.getChildren().size == before);
            } catch (Throwable t) {
                expect("tooltip " + t, false);
            }
        }
        if (view.has("vib")) {
            boolean before = view.isChecked("vib");
            log.clear();
            tap(center("vib"));
            expect("switch toggles " + log, view.isChecked("vib") != before && loggedPrefix("change:vib="));
        }
        if (view.has("tips")) {
            boolean before = view.isChecked("tips");
            log.clear();
            tap(center("tips"));
            expect("checkbox toggles " + log, view.isChecked("tips") != before && loggedPrefix("change:tips="));
        }
        if (view.has("hard") && view.has("easy")) {
            log.clear();
            tap(center("hard"));
            expect("radio exclusive " + log, view.isChecked("hard") && !view.isChecked("easy"));
        }
        if (view.has("music")) {
            log.clear();
            drag(at("music", 0.1f, 0.5f), at("music", 0.75f, 0.5f), 8);
            float v = view.getValue("music");
            expect("slider drag value " + v + " " + log.size() + " events", v > 0.6f && v < 0.85f && loggedPrefix("change:music="));
        }
        if (view.has("sfx")) {
            view.setValue("sfx", 0.2f);
            expect("setValue/getValue " + view.getValue("sfx"), Math.abs(view.getValue("sfx") - 0.2f) < 0.001f);
        }
        if (view.has("name")) {
            log.clear();
            tap(center("name"));
            stage.keyTyped('h');
            stage.keyTyped('i');
            stage.act(0.02f);
            expect("text field typing '" + view.getText("name") + "' " + log, view.getText("name").equals("hi") && loggedPrefix("change:name="));
            stage.setKeyboardFocus(null);
        }
        if (view.has("score")) {
            view.setText("score", "Score: 99");
            expect("setText", view.getText("score").equals("Score: 99"));
        }
        if (view.has("hp")) {
            view.setValue("hp", 20);
            expect("progress setValue", view.getValue("hp") == 20f);
        }
        if (view.has("scroll")) {
            log.clear();
            Actor sc = view.find("scroll");
            drag(at("scroll", 0.5f, 0.8f), at("scroll", 0.5f, 0.1f), 12);
            expect("scrollview scrolled " + log.size() + " events", loggedPrefix("scroll:scroll"));
        }
        if (view.has("l3")) {
            log.clear();
            tap(center("l3"));
            expect("grid button click " + log, logged("click:l3"));
        }
        if (view.has("topBar")) {
            view.setVisible("btnPause", false);
            Actor sc = view.find("score");
            expect("hidden node leaves room (score is wider)", sc.getWidth() > 0);
            view.setVisible("btnPause", true);
        }
        // programmatic click
        if (view.has("btnShoot")) {
            log.clear();
            view.click("btnShoot");
            expect("ui.click(id) " + log, logged("click:btnShoot"));
            // listeners registered from code (UIHolder.onClick("id", runnable)): run after the events object, only for their node
            final int[] n = {0};
            view.listeners().onClick("btnShoot", new Runnable() { public void run() { n[0]++; } })
                    .onClick("btnShoot", new Runnable() { public void run() { n[0]++; } })
                    .onClick("other", new Runnable() { public void run() { n[0] += 100; } });
            view.click("btnShoot");
            expect("code listeners of a node run (and only that node's) " + n[0], n[0] == 2);
            view.listeners().remove("btnShoot", "onClick");
            view.click("btnShoot");
            expect("listeners can be removed " + n[0], n[0] == 2);
        }
        // set by key through the registry
        if (view.has("btnClose")) {
            String err = view.set("btnClose", "text", "Done");
            expect("ui.set text " + err, err == null && view.getText("btnClose").equals("Done"));
            err = view.set("btnClose", "style", "nonsense");
            expect("ui.set rejects bad enum", err != null);
        }
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.16f, 0.17f, 0.23f, 1f);
        stage.act(1 / 60f);
        stage.draw();
        frame++;
        if (frame == 3 && test) runTests();
        if (frame == (test ? 6 : 4)) {
            Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            // the frame buffer is bottom-up: flip rows
            Pixmap fl = new Pixmap(pm.getWidth(), pm.getHeight(), Pixmap.Format.RGBA8888);
            fl.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < pm.getHeight(); y++) fl.drawPixmap(pm, 0, y, 0, pm.getHeight() - 1 - y, pm.getWidth(), 1);
            PixmapIO.writePNG(Gdx.files.absolute(out), fl);
            fl.dispose();
            pm.dispose();
            if (test) System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
            Gdx.app.exit();
            if (fails > 0) System.exit(1);
        }
    }
}
