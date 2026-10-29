package com.star4droid.template.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;

/** A Group that can hide what lies outside its bounds (scissor clip) and also ignores touches there. */
public class ClipGroup extends Group {
    private boolean clip;

    public void setClip(boolean clip) {
        this.clip = clip;
    }

    public boolean isClip() {
        return clip;
    }

    @Override
    protected void drawChildren(Batch batch, float parentAlpha) {
        if (clip) {
            if (clipBegin(0, 0, getWidth(), getHeight())) {
                super.drawChildren(batch, parentAlpha);
                batch.flush();
                clipEnd();
            }
        } else super.drawChildren(batch, parentAlpha);
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        if (clip && (x < 0 || y < 0 || x >= getWidth() || y >= getHeight())) return null;
        return super.hit(x, y, touchable);
    }
}
