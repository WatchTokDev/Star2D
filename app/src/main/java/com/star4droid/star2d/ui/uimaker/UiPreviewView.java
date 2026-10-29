package com.star4droid.star2d.ui.uimaker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.template.ui.UIDocument;
import com.star4droid.template.ui.UINode;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The preview area of the UI Maker: draws the rendered picture of the UI inside a device frame and puts the editing layer on top
 * (selection outline, resize handles, grid, drop indicator). Pure Android canvas code - the picture itself comes from the real
 * renderer ({@link UiPreviewGl}) or, when graphics are not available, a wireframe of the layout.
 *
 * Edit mode: tap selects, drag moves (x/y offset, or reorders inside Linear/Grid), handles resize, two fingers zoom and pan.
 * Test mode: touches go to the real widgets (buttons, sliders, switches, scrolling...).
 */
final class UiPreviewView extends View {
    interface Host {
        UIDocument doc();

        UINode selected();

        void select(UINode n);

        /** A gesture starts changing the document (history is recorded when it ends). */
        void gestureStart();

        /** The document changed during the gesture (render again, keep the gesture). */
        void gestureChanged();

        /** The gesture is over: {@code changed} tells whether the document differs. */
        void gestureEnd(boolean changed);

        boolean testMode();

        /** Snap size in logical px, 0 = off. */
        int grid();

        /** Test mode: type 0/1/2 = down/drag/up in preview pixels. */
        void testTouch(int type, float px, float py);

        void say(String msg);

        /** The person starts dragging a handle of the phone frame (design size). */
        void deviceResizeStart();

        /** The logical size is now {@code w x h} (live while dragging; the view already shows it). Render again, do not refit. */
        void deviceResized(int w, int h);

        /** The size gesture is over: refit the view and refresh everything. */
        void deviceResizeEnd();
    }

    /** Limits of the phone size, logical px. */
    static final int MIN_SIZE = 120, MAX_SIZE = 4096;

    private final Host host;
    private Bitmap bmp;
    private UiRects rects;
    private int lw = 720, lh = 1280;
    private float zoom = 1f, panX, panY;
    private boolean wire;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final Rect src = new Rect();
    private final int slop;
    private final float handleR;

    // gesture state
    private static final int NONE = 0, PAN = 1, DRAG = 2, RESIZE = 3, PINCH = 4, TEST = 5, PENDING = 6, DEVRESIZE = 7;
    private int mode = NONE;
    private float downX, downY, lastX, lastY;
    private UiRects.R startRect;
    private float x0, y0;
    private String w0, h0;
    private int handle;
    private float panX0, panY0;
    // pinch
    private float pinchDist0, pinchZoom0, pinchLX, pinchLY;
    private boolean moved;
    // reorder
    private int dropIndex = -1;
    private UINode dropParent;
    private RectF dropLine;
    private float dragDX, dragDY;
    // phone frame resize: scale and origin are frozen during the gesture so the top-left corner stays where it is on screen
    private boolean frozen;
    private float fScale, fOx, fOy;
    private int devHandle = -1, devStartW, devStartH;
    private boolean devResizing;
    private int bottomInset;
    private Drawable lockIcon;

    UiPreviewView(Context c, Host host) {
        super(c);
        this.host = host;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        handleR = Ui.dp(c, 9);
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ------------------------------------------------------------------ state from the dialog

    void setLogicalSize(int w, int h) {
        if (w != lw || h != lh) {
            lw = w;
            lh = h;
            invalidate();
        }
    }

    int logicalW() {
        return lw;
    }

    int logicalH() {
        return lh;
    }

    void setFrame(Bitmap b, UiRects r) {
        bmp = b;
        rects = r;
        wire = false;
        invalidate();
    }

    /** No picture available: draw the layout as boxes. */
    void setWire(UiRects r) {
        rects = r;
        wire = true;
        invalidate();
    }

    void setRects(UiRects r) {
        rects = r;
        invalidate();
    }

    UiRects rects() {
        return rects;
    }

    float zoom() {
        return zoom;
    }

    void setZoom(float z) {
        zoom = Math.max(0.25f, Math.min(6f, z));
        clampPan();
        invalidate();
    }

    void fit() {
        zoom = 1f;
        panX = 0;
        panY = 0;
        invalidate();
    }

    /** Pixel width the picture should have to look sharp at the current zoom. */
    int wantedPixelWidth() {
        float s = scale();
        int w = Math.round(lw * s);
        return Math.max(160, Math.min(w, Math.min(lw, 1600)));
    }

    /** Space at the bottom that other views cover (the chips strip): the phone is centered in what is left. */
    void setBottomInset(int px) {
        px = Math.max(0, px);
        if (px == bottomInset) return;
        bottomInset = px;
        clampPan();
        invalidate();
    }

    // ------------------------------------------------------------------ geometry

    private float fitScale() {
        float pad = Ui.dp(getContext(), 26); // room for the size handles around the frame
        float aw = Math.max(10, getWidth() - 2 * pad), ah = Math.max(10, getHeight() - bottomInset - 2 * pad);
        return Math.min(aw / lw, ah / lh);
    }

    float scale() {
        return frozen ? fScale : fitScale() * zoom;
    }

    private float ox() {
        return frozen ? fOx : getWidth() / 2f + panX - lw * scale() / 2f;
    }

    private float oy() {
        return frozen ? fOy : (getHeight() - bottomInset) / 2f + panY - lh * scale() / 2f;
    }

    private void freeze() {
        fScale = scale();
        fOx = ox();
        fOy = oy();
        frozen = true;
    }

    private void unfreeze() {
        frozen = false;
    }

    private float toViewX(float lx) {
        return ox() + lx * scale();
    }

    private float toViewY(float ly) {
        return oy() + ly * scale();
    }

    private float toLogX(float vx) {
        return (vx - ox()) / scale();
    }

    private float toLogY(float vy) {
        return (vy - oy()) / scale();
    }

    private void clampPan() {
        float s = scale();
        float maxX = Math.max(0, (lw * s) / 2f + getWidth() / 4f), maxY = Math.max(0, (lh * s) / 2f + (getHeight() - bottomInset) / 4f);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        clampPan();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF101216);
        float s = scale(), ox = ox(), oy = oy();
        RectF dev = new RectF(ox, oy, ox + lw * s, oy + lh * s);
        float fr = Ui.dp(getContext(), 6);
        // device body
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF2B303A);
        c.drawRoundRect(dev.left - fr, dev.top - fr, dev.right + fr, dev.bottom + fr, fr * 2, fr * 2, p);
        p.setColor(0xFF000000);
        c.drawRect(dev, p);
        c.save();
        c.clipRect(dev);
        if (bmp != null && !wire) {
            src.set(0, 0, bmp.getWidth(), bmp.getHeight());
            p.setFilterBitmap(true);
            c.drawBitmap(bmp, src, dev, p);
        } else if (rects != null) {
            drawWire(c, s, ox, oy);
        }
        drawGrid(c, dev, s);
        c.restore();
        if (host.testMode()) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(), 2));
            p.setColor(Ui.SUCCESS);
            c.drawRect(dev, p);
            return;
        }
        drawOverlay(c, s, ox, oy);
    }

    private void drawGrid(Canvas c, RectF dev, float s) {
        int g = host.grid();
        if (g <= 0 || g * s < 6) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x33FFFFFF);
        for (float x = g; x < lw; x += g) c.drawLine(dev.left + x * s, dev.top, dev.left + x * s, dev.bottom, p);
        for (float y = g; y < lh; y += g) c.drawLine(dev.left, dev.top + y * s, dev.right, dev.top + y * s, p);
    }

    private void drawWire(Canvas c, float s, float ox, float oy) {
        UIDocument d = host.doc();
        p.setStrokeWidth(1f);
        for (UiRects.R r : rects.rects.values()) {
            if (!r.shown || r.node.parent == null) continue;
            tmp.set(ox + r.x * s, oy + r.y * s, ox + (r.x + r.w) * s, oy + (r.y + r.h) * s);
            int fill, stroke = 0x88FFFFFF;
            switch (r.node.type) {
                case UINode.BUTTON: fill = 0xFF2F6FD0; break;
                case UINode.LABEL: fill = 0x00000000; break;
                case UINode.PROGRESS: case UINode.SLIDER: fill = 0xFF3A8F55; break;
                case UINode.IMAGE: case UINode.ICON: fill = 0x66AA66CC; break;
                case UINode.CHECKBOX: case UINode.RADIO: case UINode.SWITCH: fill = 0x66FFAA33; break;
                case UINode.TEXTFIELD: fill = 0xFF2B2F36; break;
                default: fill = 0x22FFFFFF; stroke = 0x44FFFFFF; break;
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            c.drawRect(tmp, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(stroke);
            c.drawRect(tmp, p);
            String t = null;
            if (r.node.has("text")) t = r.node.str("text", "");
            else if (r.node.has("hint")) t = r.node.str("hint", "");
            else if (!r.node.isContainer()) t = UiTr.widgetName(r.node.type);
            if (t != null && !t.isEmpty()) {
                text.setColor(0xFFFFFFFF);
                text.setTextSize(Math.max(8f, Math.min(tmp.height() * 0.5f, 22f * s * 2)));
                c.drawText(t, tmp.centerX(), tmp.centerY() + text.getTextSize() * 0.35f, text);
            }
        }
    }

    private void drawOverlay(Canvas c, float s, float ox, float oy) {
        Context ctx = getContext();
        drawDeviceHandles(c, new RectF(ox, oy, ox + lw * s, oy + lh * s));
        UINode sel = host.selected();
        UiRects.R r = rects == null || sel == null ? null : rects.get(sel.id);
        if (r != null && r.shown) {
            float dx = mode == DRAG && dropParent == null ? dragDX : 0, dy = mode == DRAG && dropParent == null ? dragDY : 0;
            // parent outline (dashed) for context
            if (sel.parent != null && sel.parent.parent != null) {
                UiRects.R pr = rects.get(sel.parent.id);
                if (pr != null && pr.shown) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Ui.dp(ctx, 1));
                    p.setColor(0x99FFC107);
                    p.setPathEffect(new DashPathEffect(new float[]{Ui.dp(ctx, 5), Ui.dp(ctx, 4)}, 0));
                    c.drawRect(ox + pr.x * s, oy + pr.y * s, ox + (pr.x + pr.w) * s, oy + (pr.y + pr.h) * s, p);
                    p.setPathEffect(null);
                }
            }
            tmp.set(ox + r.x * s + dx, oy + r.y * s + dy, ox + (r.x + r.w) * s + dx, oy + (r.y + r.h) * s + dy);
            p.setStyle(Paint.Style.FILL);
            p.setColor(sel.locked ? 0x22FFB300 : 0x222196F3);
            c.drawRect(tmp, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(ctx, 2));
            p.setColor(sel.locked ? Ui.WARNING : Ui.ACCENT);
            if (sel.locked) p.setPathEffect(new DashPathEffect(new float[]{Ui.dp(ctx, 7), Ui.dp(ctx, 4)}, 0));
            c.drawRect(tmp, p);
            p.setPathEffect(null);
            if (!sel.locked && (mode != DRAG || dropParent != null)) drawHandles(c, sel, tmp);
            // label chip (a lock mark in front of the id when the element is locked)
            String t = sel.id + "  " + Math.round(r.w) + "x" + Math.round(r.h);
            text.setTextSize(Ui.dp(ctx, 11));
            text.setTextAlign(Paint.Align.LEFT);
            float lockW = sel.locked ? Ui.dp(ctx, 14) : 0;
            float tw = text.measureText(t) + Ui.dp(ctx, 10) + lockW, th = Ui.dp(ctx, 18);
            float cx = Math.max(2, Math.min(getWidth() - tw - 2, tmp.left)), cy = tmp.top - th - 2 < 0 ? tmp.bottom + 2 : tmp.top - th - 2;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.ACCENT_DARK);
            c.drawRoundRect(cx, cy, cx + tw, cy + th, th / 2, th / 2, p);
            if (sel.locked) {
                if (lockIcon == null) {
                    Drawable d = ContextCompat.getDrawable(ctx, R.drawable.ic_s2_lock);
                    if (d != null) {
                        lockIcon = d.mutate();
                        lockIcon.setTint(0xFFFFFFFF);
                    }
                }
                if (lockIcon != null) {
                    int ls = Ui.dp(ctx, 11);
                    int lx = Math.round(cx + Ui.dp(ctx, 5)), ly = Math.round(cy + (th - ls) / 2f);
                    lockIcon.setBounds(lx, ly, lx + ls, ly + ls);
                    lockIcon.draw(c);
                }
            }
            text.setColor(0xFFFFFFFF);
            c.drawText(t, cx + Ui.dp(ctx, 5) + lockW, cy + th * 0.72f, text);
            text.setTextAlign(Paint.Align.CENTER);
        }
        if (mode == DRAG && dropLine != null) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.WARNING);
            c.drawRect(ox + dropLine.left * s, oy + dropLine.top * s, ox + dropLine.right * s, oy + dropLine.bottom * s, p);
        }
    }

    // ---- phone frame handles: 0 = right edge, 1 = bottom edge, 2 = bottom-right corner

    private float devOff() {
        return Ui.dp(getContext(), 6) + Ui.dp(getContext(), 9);
    }

    private float[] devHandlePos(RectF dev, int i) {
        float o = devOff();
        switch (i) {
            case 0: return new float[]{dev.right + o, dev.centerY()};
            case 1: return new float[]{dev.centerX(), dev.bottom + o};
            default: return new float[]{dev.right + o * 0.85f, dev.bottom + o * 0.85f};
        }
    }

    private RectF deviceRect() {
        float s = scale(), ox = ox(), oy = oy();
        return new RectF(ox, oy, ox + lw * s, oy + lh * s);
    }

    private void drawDeviceHandles(Canvas c, RectF dev) {
        Context ctx = getContext();
        float long2 = Ui.dp(ctx, 15), thick = Ui.dp(ctx, 4);
        for (int i = 0; i < 3; i++) {
            if (devResizing && i != devHandle) continue;
            float[] h = devHandlePos(dev, i);
            boolean active = devResizing && i == devHandle;
            p.setStyle(Paint.Style.FILL);
            p.setColor(active ? Ui.WARNING : Ui.ACCENT_DARK);
            if (i == 2) c.drawCircle(h[0], h[1], Ui.dp(ctx, 8), p);
            else if (i == 0) c.drawRoundRect(h[0] - thick, h[1] - long2, h[0] + thick, h[1] + long2, thick, thick, p);
            else c.drawRoundRect(h[0] - long2, h[1] - thick, h[0] + long2, h[1] + thick, thick, thick, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(ctx, 1.5f));
            p.setColor(0xFFFFFFFF);
            if (i == 2) c.drawCircle(h[0], h[1], Ui.dp(ctx, 8), p);
            else if (i == 0) c.drawRoundRect(h[0] - thick, h[1] - long2, h[0] + thick, h[1] + long2, thick, thick, p);
            else c.drawRoundRect(h[0] - long2, h[1] - thick, h[0] + long2, h[1] + thick, thick, thick, p);
        }
        if (devResizing) {
            String t = lw + " x " + lh;
            text.setTextSize(Ui.dp(ctx, 15));
            text.setTextAlign(Paint.Align.CENTER);
            float tw = text.measureText(t) + Ui.dp(ctx, 20), th = Ui.dp(ctx, 28);
            float cx = Math.max(tw / 2 + 2, Math.min(getWidth() - tw / 2 - 2, dev.centerX())), cy = Math.max(th, Math.min(getHeight() - bottomInset - th, dev.centerY()));
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC000000);
            c.drawRoundRect(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2, th / 2, th / 2, p);
            text.setColor(0xFFFFFFFF);
            c.drawText(t, cx, cy + th * 0.18f, text);
        }
    }

    /** Index of the phone-frame handle near the point and its distance, or {-1, large}. */
    private float[] deviceHandleAt(float x, float y) {
        RectF dev = deviceRect();
        float best = Ui.dp(getContext(), 24);
        int hit = -1;
        for (int i = 0; i < 3; i++) {
            float[] h = devHandlePos(dev, i);
            float d = (float) Math.hypot(h[0] - x, h[1] - y);
            if (d < best) {
                best = d;
                hit = i;
            }
        }
        return new float[]{hit, best};
    }

    /** Handle centers of the rectangle: index 0..7 = TL, T, TR, L, R, BL, B, BR. */
    private float[] handlePos(RectF r, int i) {
        float x = i == 0 || i == 3 || i == 5 ? r.left : i == 1 || i == 6 ? r.centerX() : r.right;
        float y = i <= 2 ? r.top : i == 3 || i == 4 ? r.centerY() : r.bottom;
        return new float[]{x, y};
    }

    private static final int[][] HDIR = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private boolean handleAllowed(UINode n, int i) {
        int[] a = UINode.parseAnchor(n.anchor);
        if (HDIR[i][0] != 0 && a[0] == UINode.STRETCH) return false;
        if (HDIR[i][1] != 0 && a[1] == UINode.STRETCH) return false;
        return true;
    }

    private void drawHandles(Canvas c, UINode n, RectF r) {
        for (int i = 0; i < 8; i++) {
            if (!handleAllowed(n, i)) continue;
            float[] h = handlePos(r, i);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(h[0], h[1], handleR * 0.6f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(getContext(), 2));
            p.setColor(Ui.ACCENT);
            c.drawCircle(h[0], h[1], handleR * 0.6f, p);
        }
    }

    // ------------------------------------------------------------------ touch

    private boolean insideDevice(float vx, float vy) {
        float lx = toLogX(vx), ly = toLogY(vy);
        return lx >= 0 && ly >= 0 && lx <= lw && ly <= lh;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked();
        if (e.getPointerCount() >= 2 && (act == MotionEvent.ACTION_POINTER_DOWN || mode != PINCH)) {
            startPinch(e);
            return true;
        }
        if (mode == PINCH) return pinch(e);
        float x = e.getX(), y = e.getY();
        switch (act) {
            case MotionEvent.ACTION_DOWN: return down(x, y);
            case MotionEvent.ACTION_MOVE: return move(x, y);
            case MotionEvent.ACTION_UP: return up(x, y, false);
            case MotionEvent.ACTION_CANCEL: return up(x, y, true);
            default: return true;
        }
    }

    private boolean down(float x, float y) {
        getParent().requestDisallowInterceptTouchEvent(true);
        downX = lastX = x;
        downY = lastY = y;
        moved = false;
        dropParent = null;
        dropLine = null;
        dragDX = dragDY = 0;
        if (host.testMode()) {
            if (insideDevice(x, y)) {
                mode = TEST;
                sendTest(0, x, y);
            } else mode = PAN;
            panX0 = panX;
            panY0 = panY;
            return true;
        }
        UINode sel = host.selected();
        UiRects.R sr = rects == null || sel == null ? null : rects.get(sel.id);
        float[] dh = deviceHandleAt(x, y);
        // resize handle of the selection (not for locked elements)
        if (sr != null && sr.shown && sel.parent != null && !sel.locked) {
            RectF r = new RectF(toViewX(sr.x), toViewY(sr.y), toViewX(sr.x + sr.w), toViewY(sr.y + sr.h));
            float best = handleR * 1.6f;
            int hit = -1;
            for (int i = 0; i < 8; i++) {
                if (!handleAllowed(sel, i)) continue;
                float[] h = handlePos(r, i);
                float d = (float) Math.hypot(h[0] - x, h[1] - y);
                if (d < best) {
                    best = d;
                    hit = i;
                }
            }
            // the closer one wins when a node handle and a phone handle overlap (the phone's gets a little help)
            if (hit >= 0 && !(dh[0] >= 0 && dh[1] * 0.75f < best)) {
                handle = hit;
                beginEdit(sel, sr);
                mode = RESIZE;
                return true;
            }
        }
        if (dh[0] >= 0) {
            devHandle = (int) dh[0];
            devStartW = lw;
            devStartH = lh;
            devResizing = false;
            freeze();
            mode = DEVRESIZE;
            return true;
        }
        UiRects.R hit = rects == null ? null : rects.hit(toLogX(x), toLogY(y));
        if (hit != null) {
            if (hit.node != sel) host.select(hit.node);
            beginEdit(hit.node, hit);
            mode = PENDING;
        } else {
            mode = PAN;
            panX0 = panX;
            panY0 = panY;
        }
        return true;
    }

    private void beginEdit(UINode n, UiRects.R r) {
        startRect = copy(r);
        x0 = n.x;
        y0 = n.y;
        w0 = n.w;
        h0 = n.h;
    }

    private static UiRects.R copy(UiRects.R r) {
        UiRects.R c = new UiRects.R();
        c.x = r.x;
        c.y = r.y;
        c.w = r.w;
        c.h = r.h;
        c.shown = r.shown;
        c.node = r.node;
        c.scrolled = r.scrolled;
        return c;
    }

    private boolean move(float x, float y) {
        float dx = x - downX, dy = y - downY;
        if (!moved && Math.hypot(dx, dy) > slop) moved = true;
        switch (mode) {
            case TEST:
                sendTest(1, x, y);
                break;
            case PAN:
                if (moved && (zoom > 1.02f || host.testMode() || true)) {
                    panX = panX0 + dx;
                    panY = panY0 + dy;
                    clampPan();
                    invalidate();
                }
                break;
            case PENDING:
                if (moved) {
                    UINode n = host.selected();
                    if (n == null || n.parent == null || n.locked) {
                        mode = NONE;
                        break;
                    }
                    mode = DRAG;
                    host.gestureStart();
                    dragMove(x, y);
                }
                break;
            case DRAG:
                dragMove(x, y);
                break;
            case DEVRESIZE:
                if (moved) {
                    if (!devResizing) {
                        devResizing = true;
                        host.deviceResizeStart();
                    }
                    deviceResizeMove(x, y);
                }
                break;
            case RESIZE:
                if (moved) {
                    if (!resizing) {
                        host.gestureStart();
                        resizing = true;
                    }
                    resizeMove(x, y);
                }
                break;
            default:
                break;
        }
        lastX = x;
        lastY = y;
        return true;
    }

    private boolean resizing;

    private boolean up(float x, float y, boolean cancel) {
        int m = mode;
        mode = NONE;
        if (m == TEST) {
            sendTest(2, x, y);
            return true;
        }
        if (m == DRAG) {
            boolean changed = false;
            UINode n = host.selected();
            if (!cancel && n != null) {
                if (dropParent != null && dropIndex >= 0) {
                    int cur = n.indexInParent();
                    int idx = dropIndex;
                    if (!(dropParent == n.parent && (idx == cur || idx == cur + 1))) {
                        String err = n.moveTo(dropParent, idx);
                        changed = err == null;
                    }
                } else changed = n.x != x0 || n.y != y0;
            } else if (n != null) {
                n.x = x0;
                n.y = y0;
            }
            dropParent = null;
            dropLine = null;
            dragDX = dragDY = 0;
            host.gestureEnd(changed);
            invalidate();
            return true;
        }
        if (m == DEVRESIZE) {
            finishDeviceResize(cancel);
            return true;
        }
        if (m == RESIZE) {
            if (resizing) {
                resizing = false;
                UINode n = host.selected();
                if (cancel && n != null) {
                    n.x = x0;
                    n.y = y0;
                    n.w = w0;
                    n.h = h0;
                }
                host.gestureEnd(!cancel);
            }
            invalidate();
            return true;
        }
        if (m == PENDING && !moved && !cancel) {
            // plain tap: selection was done on down
            invalidate();
        } else if (m == PAN && !moved && !cancel && !host.testMode()) {
            host.select(null);
        }
        return true;
    }

    private void sendTest(int type, float vx, float vy) {
        float lx = toLogX(vx), ly = toLogY(vy);
        float k = bmp == null || lw == 0 ? 1f : bmp.getWidth() / (float) lw;
        host.testTouch(type, lx * k, ly * k);
    }

    // ------------------------------------------------------------------ move / resize math

    private float snap(float v) {
        int g = host.grid();
        return g > 0 ? Math.round(v / g) * g : v;
    }

    private int snapSize(float v) {
        int g = host.grid();
        int r = Math.round(g > 0 ? Math.round(v / g) * (float) g : v);
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, r));
    }

    private void deviceResizeMove(float x, float y) {
        float s = fScale;
        float dx = (x - downX) / s, dy = (y - downY) / s;
        int nw = devHandle == 1 ? devStartW : snapSize(devStartW + dx);
        int nh = devHandle == 0 ? devStartH : snapSize(devStartH + dy);
        if (nw == lw && nh == lh) return;
        setLogicalSize(nw, nh);
        host.deviceResized(nw, nh);
    }

    private void finishDeviceResize(boolean cancel) {
        boolean was = devResizing;
        devResizing = false;
        if (cancel && was && (lw != devStartW || lh != devStartH)) {
            setLogicalSize(devStartW, devStartH);
            host.deviceResized(devStartW, devStartH);
        }
        unfreeze();
        if (was) host.deviceResizeEnd();
        invalidate();
    }

    private void dragMove(float x, float y) {
        UINode n = host.selected();
        if (n == null || n.parent == null) return;
        float s = scale();
        float dxL = (x - downX) / s, dyL = (y - downY) / s;
        UINode parent = n.parent;
        boolean managed = parent.type.equals(UINode.LINEAR) || parent.type.equals(UINode.GRID);
        if (managed) {
            dragDX = x - downX;
            dragDY = y - downY;
            updateDrop(n, parent, toLogX(x), toLogY(y));
            invalidate();
            return;
        }
        dropParent = null;
        dropLine = null;
        n.x = snap(x0 + dxL);
        n.y = snap(y0 - dyL);
        dragDX = (n.x - x0) * s;
        dragDY = -(n.y - y0) * s;
        host.gestureChanged();
        invalidate();
    }

    /** Finds where in a Linear / Grid the dragged node would land: before or after the sibling nearest to the finger. */
    private void updateDrop(UINode n, UINode parent, float lx, float ly) {
        dropParent = parent;
        dropIndex = -1;
        dropLine = null;
        if (rects == null) return;
        boolean hor = parent.type.equals(UINode.LINEAR) && parent.str("orientation", "vertical").toLowerCase(Locale.ROOT).startsWith("h");
        boolean grid = parent.type.equals(UINode.GRID);
        ArrayList<UiRects.R> sib = new ArrayList<>();
        for (UINode c : parent.children) {
            if (c == n) continue;
            UiRects.R r = rects.get(c.id);
            if (r != null && r.shown) sib.add(r);
        }
        if (sib.isEmpty()) {
            dropIndex = 0;
            return;
        }
        UiRects.R best = null;
        float bd = Float.MAX_VALUE;
        for (UiRects.R r : sib) {
            float cx = r.x + r.w / 2, cy = r.y + r.h / 2;
            float d = (cx - lx) * (cx - lx) + (cy - ly) * (cy - ly);
            if (d < bd) {
                bd = d;
                best = r;
            }
        }
        boolean after;
        if (grid) after = lx > best.x + best.w / 2;
        else if (hor) after = lx > best.x + best.w / 2;
        else after = ly > best.y + best.h / 2;
        int idx = best.node.indexInParent() + (after ? 1 : 0);
        dropIndex = idx;
        float t = Math.max(2f, 3f / scale());
        if (hor || grid) {
            float xx = after ? best.x + best.w : best.x;
            dropLine = new RectF(xx - t / 2, best.y, xx + t / 2, best.y + best.h);
        } else {
            float yy = after ? best.y + best.h : best.y;
            dropLine = new RectF(best.x, yy - t / 2, best.x + best.w, yy + t / 2);
        }
    }

    private void resizeMove(float x, float y) {
        UINode n = host.selected();
        if (n == null || startRect == null) return;
        float s = scale();
        float dx = (x - downX) / s, dy = (y - downY) / s;
        float L = startRect.x, T = startRect.y, R = startRect.x + startRect.w, B = startRect.y + startRect.h;
        float L2 = L, R2 = R, T2 = T, B2 = B;
        int hx = HDIR[handle][0], hy = HDIR[handle][1];
        if (hx < 0) L2 = snap(L + dx);
        if (hx > 0) R2 = snap(R + dx);
        if (hy < 0) T2 = snap(T + dy);
        if (hy > 0) B2 = snap(B + dy);
        float min = 8f;
        if (R2 - L2 < min) {
            if (hx < 0) L2 = R2 - min;
            else R2 = L2 + min;
        }
        if (B2 - T2 < min) {
            if (hy < 0) T2 = B2 - min;
            else B2 = T2 + min;
        }
        int[] a = UINode.parseAnchor(n.anchor);
        if (hx != 0) {
            n.w = fmt(R2 - L2);
            if (a[0] == UINode.LEFT) n.x = x0 + (L2 - L);
            else if (a[0] == UINode.RIGHT) n.x = x0 + (R2 - R);
            else n.x = x0 + ((L2 + R2) / 2 - (L + R) / 2);
        }
        if (hy != 0) {
            n.h = fmt(B2 - T2);
            if (a[1] == UINode.TOP) n.y = y0 - (T2 - T);
            else if (a[1] == UINode.BOTTOM) n.y = y0 - (B2 - B);
            else n.y = y0 - ((T2 + B2) / 2 - (T + B) / 2);
        }
        host.gestureChanged();
    }

    private static String fmt(float v) {
        float r = Math.round(v * 10f) / 10f;
        return r == (int) r ? String.valueOf((int) r) : String.valueOf(r);
    }

    // ------------------------------------------------------------------ pinch

    private void startPinch(MotionEvent e) {
        if (mode == DRAG || mode == RESIZE) {
            UINode n = host.selected();
            if (n != null) {
                n.x = x0;
                n.y = y0;
                n.w = w0;
                n.h = h0;
            }
            dropParent = null;
            dropLine = null;
            if (resizing) {
                resizing = false;
                host.gestureEnd(false);
            } else host.gestureEnd(false);
        }
        if (mode == DEVRESIZE) finishDeviceResize(true);
        if (mode == TEST) sendTest(2, lastX, lastY);
        mode = PINCH;
        float cx = (e.getX(0) + e.getX(1)) / 2f, cy = (e.getY(0) + e.getY(1)) / 2f;
        pinchDist0 = (float) Math.max(1, Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)));
        pinchZoom0 = zoom;
        pinchLX = toLogX(cx);
        pinchLY = toLogY(cy);
        getParent().requestDisallowInterceptTouchEvent(true);
        invalidate();
    }

    private boolean pinch(MotionEvent e) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            mode = NONE;
            return true;
        }
        if (act == MotionEvent.ACTION_POINTER_UP && e.getPointerCount() <= 2) {
            mode = NONE;
            return true;
        }
        if (e.getPointerCount() < 2) return true;
        float cx = (e.getX(0) + e.getX(1)) / 2f, cy = (e.getY(0) + e.getY(1)) / 2f;
        float dist = (float) Math.max(1, Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)));
        zoom = Math.max(0.25f, Math.min(6f, pinchZoom0 * dist / pinchDist0));
        float s = scale();
        // keep the logical point under the fingers' centre
        float wantOx = cx - pinchLX * s, wantOy = cy - pinchLY * s;
        panX = wantOx - (getWidth() / 2f - lw * s / 2f);
        panY = wantOy - (getHeight() / 2f - lh * s / 2f);
        clampPan();
        invalidate();
        if (onZoom != null) onZoom.run();
        return true;
    }

    private Runnable onZoom;

    void setOnZoom(Runnable r) {
        onZoom = r;
    }
}
