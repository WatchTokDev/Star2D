package com.star4droid.star2d.ui.uimaker;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UINode;
import com.star4droid.template.ui.UIView;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;

/**
 * Draws a .ui with the REAL runtime (UIView on a Stage) into an off-screen frame buffer and hands back the pixels plus the position of
 * every node. This is what the UI Maker shows as its preview, so the picture is exactly what the game will draw.
 *
 * Pure libGDX (no android.*): the Maker calls it on the GL thread of the editor (Gdx.app.postRunnable); tools/test_uimaker.py runs it
 * under Xvfb to check it.
 *
 * Every method must be called on the GL thread. The previous viewport / framebuffer of the caller is restored.
 */
public final class UiPreviewGl {
    /** Result of one {@link #render}. */
    public static final class Frame {
        /** ARGB pixels, top row first, pw x ph. */
        public int[] argb;
        public int pw, ph;
        /** Logical size of the UI (the layout is computed for it). */
        public int lw, lh;
        /** id -> {x, y(top), w, h, shown(1/0)} in logical px, y down. */
        public final LinkedHashMap<String, float[]> rects = new LinkedHashMap<>();
        public String error;
    }

    /** Viewport whose pointer conversion works in "preview pixels", independent of the real window. */
    private static final class PVP extends Viewport {
        int lw = 1, lh = 1, pw = 1, ph = 1, winH = 1;

        PVP() {
            setCamera(new OrthographicCamera());
        }

        void configure(int lw, int lh, int pw, int ph) {
            this.lw = lw;
            this.lh = lh;
            this.pw = pw;
            this.ph = ph;
            this.winH = Math.max(1, Gdx.graphics.getHeight());
            setWorldSize(lw, lh);
            setScreenBounds(0, 0, pw, ph);
            OrthographicCamera c = (OrthographicCamera) getCamera();
            c.setToOrtho(false, lw, lh);
            c.update();
        }

        @Override
        public void update(int screenWidth, int screenHeight, boolean centerCamera) {
            // fixed: the size is set by configure()
        }

        @Override
        public Vector2 unproject(Vector2 s) {
            // s.y is in "window" coordinates: (winH - ph) + previewY, see UiPreviewGl.screenY
            float py = s.y - (winH - ph);
            float x = s.x * lw / pw;
            float yDown = py * lh / ph;
            s.set(x, lh - yDown);
            return s;
        }
    }

    private final String project;
    private Stage stage;
    private PVP vp;
    private UIView view;
    private FrameBuffer fb;
    private String loadedJson = "";
    private int lw, lh, pw, ph;
    private final IntBuffer saved = BufferUtils.newIntBuffer(16);

    public UiPreviewGl(String projectPath) {
        this.project = projectPath == null ? "" : projectPath;
    }

    /**
     * Draws the document.
     * @param json         the document as JSON text (a private copy is parsed, the caller's objects are never touched)
     * @param keepState    true: when the document did not change keep the live widgets (test mode: slider moved, switch toggled)
     * @param bg           ARGB background behind the UI
     */
    public Frame render(String json, int lw, int lh, int pw, int ph, int bg, boolean keepState, float delta) {
        Frame f = new Frame();
        f.lw = lw;
        f.lh = lh;
        f.pw = pw;
        f.ph = ph;
        try {
            ensure(lw, lh, pw, ph);
            boolean same = keepState && json.equals(loadedJson) && view != null;
            if (!same) {
                UIDocument d = UIDocument.parse(json);
                if (view == null) {
                    view = new UIView(project, d);
                    view.setPlaceholders(true);
                    view.setSize(lw, lh);
                    stage.addActor(view);
                    view.setDocument(d);
                } else {
                    view.setPlaceholders(true);
                    view.setSize(lw, lh);
                    view.setDocument(d);
                }
                loadedJson = json;
            } else if (view.getWidth() != lw || view.getHeight() != lh) {
                view.setSize(lw, lh);
            }
            if (view.getWidth() != lw || view.getHeight() != lh) view.setSize(lw, lh);
            view.relayout();
            draw(f, bg, delta, !same ? 2 : 1);
            collect(f);
        } catch (Throwable t) {
            f.error = t.toString();
            restore();
        }
        return f;
    }

    private void ensure(int lw, int lh, int pw, int ph) {
        if (stage == null) {
            vp = new PVP();
            vp.configure(lw, lh, pw, ph);
            stage = new Stage(vp);
        }
        vp.configure(lw, lh, pw, ph);
        if (fb == null || this.pw != pw || this.ph != ph) {
            if (fb != null) fb.dispose();
            fb = new FrameBuffer(Pixmap.Format.RGBA8888, pw, ph, false);
        }
        this.lw = lw;
        this.lh = lh;
        this.pw = pw;
        this.ph = ph;
    }

    private void draw(Frame f, int bg, float delta, int acts) {
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, saved);
        int vx = saved.get(0), vy = saved.get(1), vw = saved.get(2), vh = saved.get(3);
        boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        fb.begin();
        try {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glViewport(0, 0, pw, ph);
            Gdx.gl.glClearColor(((bg >> 16) & 255) / 255f, ((bg >> 8) & 255) / 255f, (bg & 255) / 255f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            for (int i = 0; i < acts; i++) stage.act(i == 0 ? Math.max(0f, delta) : 1f / 60f);
            stage.draw();
            Pixmap pm = ScreenUtils.getFrameBufferPixmap(0, 0, pw, ph);
            ByteBuffer b = pm.getPixels();
            int[] out = new int[pw * ph];
            for (int y = 0; y < ph; y++) {
                int srcRow = ph - 1 - y;
                for (int x = 0; x < pw; x++) {
                    int i = (srcRow * pw + x) * 4;
                    out[y * pw + x] = 0xFF000000 | ((b.get(i) & 255) << 16) | ((b.get(i + 1) & 255) << 8) | (b.get(i + 2) & 255);
                }
            }
            pm.dispose();
            f.argb = out;
        } finally {
            fb.end();
            Gdx.gl.glViewport(vx, vy, vw, vh);
            if (scissor) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        }
    }

    private void restore() {
        try {
            Gdx.gl.glViewport(saved.get(0), saved.get(1), saved.get(2), saved.get(3));
        } catch (Throwable ignored) {
        }
    }

    private void collect(Frame f) {
        final Vector2 a = new Vector2(), b = new Vector2();
        for (UINode n : view.document().allNodes()) {
            com.star4droid.template.ui.UIActor act = view.find(n.id);
            if (act == null) continue;
            boolean shown = true;
            for (Actor p = act; p != null && p != view; p = p.getParent()) if (!p.isVisible()) shown = false;
            a.set(0, 0);
            b.set(act.getWidth(), act.getHeight());
            act.localToAscendantCoordinates(view, a);
            act.localToAscendantCoordinates(view, b);
            float x0 = Math.min(a.x, b.x), x1 = Math.max(a.x, b.x), y0 = Math.min(a.y, b.y), y1 = Math.max(a.y, b.y);
            f.rects.put(n.id, new float[]{x0, lh - y1, x1 - x0, y1 - y0, shown ? 1f : 0f});
        }
    }

    // ------------------------------------------------------------------ test mode input

    /** Y value to pass to the stage for a preview pixel y (see {@link PVP#unproject}). */
    private int screenY(float py) {
        return Math.round((Math.max(1, Gdx.graphics.getHeight()) - ph) + py);
    }

    /** 0 = down, 1 = drag, 2 = up. Coordinates in preview pixels (top-left origin). Returns whether the UI used the touch. */
    public boolean touch(int type, float px, float py) {
        if (stage == null || view == null) return false;
        vp.configure(lw, lh, pw, ph);
        int sx = Math.round(px), sy = screenY(py);
        switch (type) {
            case 0: return stage.touchDown(sx, sy, 0, Input.Buttons.LEFT);
            case 1: return stage.touchDragged(sx, sy, 0);
            default: return stage.touchUp(sx, sy, 0, Input.Buttons.LEFT);
        }
    }

    /** Live value / state of a node in the running preview (test mode checks). */
    public UIView view() {
        return view;
    }

    public void dispose() {
        try {
            if (view != null) view.dispose();
        } catch (Throwable ignored) {
        }
        if (stage != null) stage.dispose();
        if (fb != null) fb.dispose();
        stage = null;
        fb = null;
        view = null;
        loadedJson = "";
    }
}
