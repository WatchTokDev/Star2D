package com.star4droid.star2d.ui.uimaker;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.badlogic.gdx.Gdx;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders the preview of the UI Maker on the editor's GL thread (see {@link UiPreviewGl}) and delivers bitmaps on the UI thread.
 *
 * Requests are coalesced: while a frame is being drawn newer requests replace each other, so dragging never builds a queue.
 * When the editor has no graphics (or the GL thread does not answer) {@link #available()} is false and the view draws a wireframe.
 */
final class PreviewEngine {
    interface Listener {
        /** UI thread. {@code f.rects} are the node rectangles measured by the real renderer. */
        void onFrame(Bitmap bmp, UiPreviewGl.Frame f);

        void onError(String message);
    }

    private static final class Req {
        String json;
        int lw, lh, pw, ph, bg;
        boolean keep;
        float delta;
    }

    private final String project;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private Req pending;
    private boolean inFlight;
    private long lastRequest;
    private long lastFrame;
    private UiPreviewGl gl; // GL thread only
    private final AtomicBoolean disposed = new AtomicBoolean();

    PreviewEngine(String projectPath, Listener l) {
        this.project = projectPath;
        this.listener = l;
    }

    /** True when the GL thread exists and has answered recently (or nothing was asked yet). */
    boolean available() {
        if (Gdx.app == null) return false;
        long now = System.currentTimeMillis();
        return !(inFlight && now - lastRequest > 2500 && lastFrame < lastRequest);
    }

    /** Asks for a frame. {@code keep}: keep the live widget state when the document is unchanged (test mode). */
    void request(String json, int lw, int lh, int pw, int ph, int bg, boolean keep, float delta) {
        if (disposed.get() || Gdx.app == null) return;
        Req r = new Req();
        r.json = json;
        r.lw = lw;
        r.lh = lh;
        r.pw = Math.max(16, pw);
        r.ph = Math.max(16, ph);
        r.bg = bg;
        r.keep = keep;
        r.delta = delta;
        boolean start;
        synchronized (lock) {
            if (pending != null && pending.keep && keep) r.delta += pending.delta; // do not lose animation time
            pending = r;
            start = !inFlight;
            if (start) inFlight = true;
        }
        lastRequest = System.currentTimeMillis();
        if (start) post();
    }

    /** Forwards a touch to the preview stage (test mode). type: 0 down, 1 drag, 2 up; px/py in preview pixels. */
    void touch(final int type, final float px, final float py) {
        if (disposed.get() || Gdx.app == null) return;
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gl != null) gl.touch(type, px, py);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void post() {
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                Req r;
                synchronized (lock) {
                    r = pending;
                    pending = null;
                }
                if (r == null) {
                    synchronized (lock) {
                        inFlight = false;
                    }
                    return;
                }
                UiPreviewGl.Frame f = null;
                Bitmap bmp = null;
                String err = null;
                try {
                    if (disposed.get()) return;
                    if (gl == null) gl = new UiPreviewGl(project);
                    f = gl.render(r.json, r.lw, r.lh, r.pw, r.ph, r.bg, r.keep, r.delta);
                    if (f.error != null) err = f.error;
                    else bmp = Bitmap.createBitmap(f.argb, f.pw, f.ph, Bitmap.Config.ARGB_8888);
                } catch (Throwable t) {
                    err = t.toString();
                }
                lastFrame = System.currentTimeMillis();
                final UiPreviewGl.Frame ff = f;
                final Bitmap bb = bmp;
                final String ee = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (disposed.get()) return;
                        if (ee != null) listener.onError(ee);
                        else listener.onFrame(bb, ff);
                    }
                });
                boolean more;
                synchronized (lock) {
                    more = pending != null;
                    if (!more) inFlight = false;
                }
                if (more) post();
            }
        });
    }

    void dispose() {
        if (!disposed.compareAndSet(false, true)) return;
        if (Gdx.app == null) return;
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (gl != null) gl.dispose();
                } catch (Throwable ignored) {
                }
                gl = null;
            }
        });
    }
}
