package com.star4droid.template.behaviors;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.star4droid.template.Utils.PlayerItem;

/**
 * Small helpers shared by the UI-effect behaviors. The behaviors never rewrite the saved element: {@link Base} remembers the
 * look the element had (scale, opacity, rotation, origin) and gives it back when the behavior is disabled or destroyed.
 */
public final class Fx {
	private Fx() {
	}

	public static final float DEG = (float) (Math.PI / 180.0);

	/** Plays sounds/<name> through the scene; false when the sound does not exist / is not loaded (so callers can stop trying). */
	public static boolean playSound(com.star4droid.template.Items.StageImp stage, String name) {
		if (stage == null || name == null || name.length() == 0) return false;
		try {
			if (name.startsWith("sounds/")) name = name.substring(7);
			else if (name.startsWith("/sounds/")) name = name.substring(8);
			stage.releaseSound(name); // restart from the beginning when it is still playing
			stage.startSound(name);
			return true;
		} catch (RuntimeException ex) {
			return false;
		}
	}

	/** Makes a Text / Progress element receive touches (the generated code makes them click-through when they have no touch event). */
	public static void enableTouch(PlayerItem it) {
		Actor a = it == null ? null : it.getActor();
		if (a != null && a.getTouchable() == Touchable.disabled) a.setTouchable(Touchable.enabled);
	}

	/** Remembered base look + the offsets a behavior has added on top of it. One instance per behavior, no allocation afterwards. */
	public static final class Base {
		public float sx = 1, sy = 1, alpha = 1, r = 1, g = 1, b = 1;
		private float originX, originY;
		private boolean originMoved;
		private float rotOff, offX, offY;

		/** Reads the element's current look as "the saved look". */
		public void capture(PlayerItem it) {
			Actor a = it.getActor();
			sx = a.getScaleX();
			sy = a.getScaleY();
			alpha = a.getColor().a;
			r = a.getColor().r;
			g = a.getColor().g;
			b = a.getColor().b;
			rotOff = 0;
			offX = 0;
			offY = 0;
		}

		/** Text / Progress elements may pivot around their corner: pivot around the center while an effect scales or rotates them. */
		public void centerOrigin(PlayerItem it) {
			Actor a = it.getActor();
			if (originMoved || a.getWidth() <= 0 || a.getHeight() <= 0) return;
			if (a.getOriginX() != 0 || a.getOriginY() != 0) return;
			originX = a.getOriginX();
			originY = a.getOriginY();
			originMoved = true;
			a.setOrigin(a.getWidth() * 0.5f, a.getHeight() * 0.5f);
		}

		/** Size = saved size * factor (x and y can differ). */
		public void scale(PlayerItem it, float fx, float fy) {
			it.getActor().setScale(sx * fx, sy * fy);
		}

		/** Opacity = saved opacity * factor. */
		public void opacity(PlayerItem it, float factor) {
			Actor a = it.getActor();
			a.getColor().a = alpha * factor;
		}

		/** Rotation offset in degrees added to the element's own rotation (physical ones: to the body angle). */
		public void rotation(PlayerItem it, float deg) {
			float delta = deg - rotOff;
			if (delta == 0) return;
			rotOff = deg;
			Body body = it.getBody();
			if (body != null) body.setTransform(body.getPosition(), body.getAngle() + delta * DEG);
			else {
				Actor a = it.getActor();
				a.setRotation(a.getRotation() + delta);
			}
		}

		/** Position offset in px added on top of wherever the element is (other behaviors may move it too). */
		public void offset(Behavior owner, float x, float y) {
			if (x == offX && y == offY) return;
			owner.setCenter(owner.centerX() + x - offX, owner.centerY() + y - offY);
			offX = x;
			offY = y;
		}

		public float offsetX() {
			return offX;
		}

		public float offsetY() {
			return offY;
		}

		/** Gives the saved look back (scale, opacity, tint, rotation, position, pivot). */
		public void restore(Behavior owner) {
			PlayerItem it = owner.item;
			if (it == null || it.getActor() == null) return;
			Actor a = it.getActor();
			rotation(it, 0);
			offset(owner, 0, 0);
			a.setScale(sx, sy);
			a.getColor().set(r, g, b, alpha);
			if (originMoved) {
				a.setOrigin(originX, originY);
				originMoved = false;
			}
		}
	}
}
