package com.star4droid.star2d.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/** Animated night-sky background: twinkling stars, shooting stars and rising hearts. */
public class StarfieldView extends View {
    private static final int STARS = 70, HEARTS = 9;
    private final Paint bg = new Paint(), star = new Paint(Paint.ANTI_ALIAS_FLAG), heart = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] sx = new float[STARS], sy = new float[STARS], sr = new float[STARS], sp = new float[STARS], ss = new float[STARS];
    private final float[] hx = new float[HEARTS], hy = new float[HEARTS], hs = new float[HEARTS], hv = new float[HEARTS], hp = new float[HEARTS];
    private final Random rnd = new Random(7);
    private ValueAnimator anim;
    private long last = 0;
    private float time = 0f;
    // shooting star
    private float shX, shY, shLife = -1f, nextShoot = 2.5f;

    public StarfieldView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < STARS; i++) {
            sx[i] = rnd.nextFloat();
            sy[i] = rnd.nextFloat();
            sr[i] = (0.6f + rnd.nextFloat() * 1.6f) * d;
            sp[i] = rnd.nextFloat() * 6.28f;
            ss[i] = 0.8f + rnd.nextFloat() * 2.2f;
        }
        for (int i = 0; i < HEARTS; i++) {
            hx[i] = rnd.nextFloat();
            hy[i] = rnd.nextFloat();
            hs[i] = (10 + rnd.nextFloat() * 18) * d;
            hv[i] = 0.015f + rnd.nextFloat() * 0.03f;
            hp[i] = rnd.nextFloat() * 6.28f;
        }
        star.setColor(0xFFFFFFFF);
        heart.setTextAlign(Paint.Align.CENTER);
        heart.setColor(0xFFFF5C8D);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h, new int[]{0xFF070B1F, 0xFF16103A, 0xFF2B1248}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            time += dt;
            step(dt);
            invalidate();
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    public void pause() { if (anim != null) anim.pause(); }
    public void resume() { last = 0; if (anim != null) anim.resume(); }

    private void step(float dt) {
        for (int i = 0; i < HEARTS; i++) {
            hy[i] -= hv[i] * dt;
            if (hy[i] < -0.08f) { hy[i] = 1.08f; hx[i] = rnd.nextFloat(); }
        }
        nextShoot -= dt;
        if (shLife < 0 && nextShoot <= 0) {
            shLife = 0f; shX = 0.2f + rnd.nextFloat() * 0.8f; shY = rnd.nextFloat() * 0.35f;
            nextShoot = 4f + rnd.nextFloat() * 5f;
        }
        if (shLife >= 0) { shLife += dt; if (shLife > 0.9f) shLife = -1f; }
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bg);
        for (int i = 0; i < STARS; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(time * ss[i] + sp[i]);
            star.setAlpha((int) (60 + 195 * tw));
            c.drawCircle(sx[i] * w, sy[i] * h, sr[i] * (0.7f + 0.5f * tw), star);
        }
        if (shLife >= 0) {
            float t = shLife / 0.9f;
            float x = shX * w - t * w * 0.35f, y = shY * h + t * h * 0.18f;
            star.setAlpha((int) (255 * (1 - t)));
            star.setStrokeWidth(2.2f * getResources().getDisplayMetrics().density);
            c.drawLine(x, y, x + w * 0.09f, y - h * 0.03f, star);
        }
        for (int i = 0; i < HEARTS; i++) {
            float sway = (float) Math.sin(time * 0.9f + hp[i]) * 0.025f;
            float a = 0.18f + 0.12f * (float) Math.sin(time * 1.3f + hp[i]);
            heart.setAlpha((int) (255 * a));
            heart.setTextSize(hs[i]);
            c.drawText("♥", (hx[i] + sway) * w, hy[i] * h, heart);
        }
        star.setAlpha(255);
    }
}
