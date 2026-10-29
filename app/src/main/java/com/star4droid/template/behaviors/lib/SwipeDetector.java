package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Swipe detector", icon = "gesture", category = "Input", targets = BehaviorInfo.Target.BOTH,
		events = {"onSwipeLeft()", "onSwipeRight()", "onSwipeUp()", "onSwipeDown()"},
		desc = "Detects a finger swipe left, right, up or down, on the element or anywhere on the screen. For page flipping, menus and swipe-controlled games.")
public class SwipeDetector extends Behavior {
	@Param(desc = "Only swipes that start on this element count. Off = a swipe anywhere on the screen.")
	public boolean onlyOnElement = false;
	@Param(desc = "How far the finger must travel (screen pixels).", unit = "px", min = 10, max = 2000, step = 5)
	public float minDistance = 80;
	@Param(desc = "A swipe must be done within this time; slower drags are ignored (0 = no limit).", unit = "s", min = 0, max = 10, step = 0.05)
	public float maxTime = 1f;
	@Param(desc = "React as soon as the distance is reached, without waiting for the finger to lift.")
	public boolean fireWhileMoving = false;

	private boolean down, ignored, fired, startedOnMe;
	private float sx, sy, lx, ly, t;
	private String last = "";

	public void onStart() {
		if (onlyOnElement) Fx.enableTouch(item);
	}

	public void onEnable() {
		if (item != null && onlyOnElement) Fx.enableTouch(item);
		down = false;
		ignored = false;
		startedOnMe = false;
	}

	public void onDisable() {
		down = false;
	}

	public void onTouchStart(InputEvent e) {
		startedOnMe = true;
	}

	@Block(label = "last swipe", desc = "left, right, up or down (empty before the first swipe).", labelAr = "آخر سحب", descAr = "left أو right أو up أو down (فارغ قبل أول سحب).", category = "Swipe")
	public String getLastSwipe() {
		return last;
	}

	@Block(label = "set swipe distance", desc = "Minimum distance of a swipe in screen pixels.", labelAr = "اضبط مسافة السحب", descAr = "أقل مسافة للسحب بالبكسل.", category = "Swipe", args = {"distance"})
	public void setMinDistance(float value) {
		minDistance = value;
	}

	private void check() {
		float dx = lx - sx, dy = ly - sy;
		float adx = Math.abs(dx), ady = Math.abs(dy);
		if (Math.max(adx, ady) < minDistance) return;
		if (maxTime > 0 && t > maxTime) return;
		fired = true;
		if (adx >= ady) {
			if (dx > 0) {
				last = "right";
				fire("onSwipeRight");
			} else {
				last = "left";
				fire("onSwipeLeft");
			}
		} else if (dy > 0) { // the screen's y grows downwards
			last = "down";
			fire("onSwipeDown");
		} else {
			last = "up";
			fire("onSwipeUp");
		}
	}

	public void onUpdate(float dt) {
		boolean touched = Gdx.input.isTouched(0);
		if (touched) {
			lx = Gdx.input.getX(0);
			ly = Gdx.input.getY(0);
			if (!down && !ignored) {
				if (!onlyOnElement || startedOnMe) {
					down = true;
					fired = false;
					sx = lx;
					sy = ly;
					t = 0;
				} else ignored = true;
			}
			if (down) {
				t += dt;
				if (fireWhileMoving && !fired) check();
			}
		} else {
			if (down && !fired) check();
			down = false;
			ignored = false;
			startedOnMe = false;
		}
	}
}
