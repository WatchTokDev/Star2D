package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Shake", icon = "vibrate", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "The element trembles for a moment: when the scene starts, when it is touched or from a block. Good for wrong answers, hits and buttons.")
public class Shake extends Behavior {
	public enum Trigger { ON_START, ON_TOUCH, MANUAL }

	public enum Axis { BOTH, HORIZONTAL, VERTICAL }

	@Param(desc = "When it shakes. MANUAL = only when the shake block runs.")
	public Trigger trigger = Trigger.ON_TOUCH;
	@Param(desc = "Which direction it moves.")
	public Axis axis = Axis.BOTH;
	@Param(desc = "How far it moves from its place.", unit = "px", min = 0, max = 500, step = 1)
	public float intensity = 8;
	@Param(desc = "How long one shake lasts.", unit = "s", min = 0.05, max = 30, step = 0.05)
	public float duration = 0.4f;
	@Param(desc = "Direction changes per second: bigger = nervous, smaller = swaying.", unit = "Hz", min = 1, max = 120, step = 1)
	public float frequency = 30;
	@Param(desc = "Also turns a little back and forth (0 = no turning).", unit = "deg", min = 0, max = 90, step = 0.5)
	public float rotation = 0;
	@Param(desc = "The shake weakens until it stops.")
	public boolean decay = true;
	@Param(desc = "Keep shaking until the stop block runs (duration is ignored).")
	public boolean loop = false;

	private final Fx.Base base = new Fx.Base();
	private boolean playing;
	private float t, power = 1;
	private int seed = 12345, lastStep;
	private float ax, ay, ar, bx, by, br;

	private float rnd() {
		seed = seed * 1664525 + 1013904223;
		return ((seed >>> 8) & 0xFFFF) / 32767.5f - 1f;
	}

	public void onStart() {
		base.capture(item);
		seed ^= item.hashCode();
		if (trigger == Trigger.ON_TOUCH) Fx.enableTouch(item);
		if (trigger == Trigger.ON_START) shake();
	}

	public void onEnable() {
		if (item != null) base.capture(item);
	}

	public void onDisable() {
		playing = false;
		base.restore(this);
	}

	public void onDestroy() {
		playing = false;
	}

	public void onTouchStart(InputEvent e) {
		if (trigger == Trigger.ON_TOUCH) shake();
	}

	@Block(label = "shake", desc = "Shakes the element once.", labelAr = "اهتز", descAr = "يهز العنصر مرة واحدة.", category = "Shake")
	public void shake() {
		playing = true;
		t = 0;
		power = 1;
		lastStep = -1;
		ax = ay = ar = bx = by = br = 0;
	}

	@Block(label = "shake with strength", desc = "Shakes the element; strength is the distance in px.", labelAr = "اهتز بقوة", descAr = "يهز العنصر؛ القوة هي المسافة بالبكسل.", category = "Shake", args = {"strength"})
	public void shakeWith(float strength) {
		intensity = strength;
		shake();
	}

	@Block(label = "stop shaking", desc = "Stops the shake at once.", labelAr = "أوقف الاهتزاز", descAr = "يوقف الاهتزاز فوراً.", category = "Shake")
	public void stop() {
		playing = false;
		base.offset(this, 0, 0);
		base.rotation(item, 0);
	}

	@Block(label = "is shaking", desc = "True while the element shakes.", labelAr = "يهتز الآن", descAr = "صحيح أثناء الاهتزاز.", category = "Shake")
	public boolean isShaking() {
		return playing;
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt;
		float u = loop ? 0 : t / Math.max(0.001f, duration);
		if (u >= 1f) {
			stop();
			fire("onDone");
			return;
		}
		float step = t * frequency;
		int si = (int) step;
		float frac = step - si;
		if (si != lastStep) {
			lastStep = si;
			ax = bx;
			ay = by;
			ar = br;
			bx = axis == Axis.VERTICAL ? 0 : rnd();
			by = axis == Axis.HORIZONTAL ? 0 : rnd();
			br = rnd();
		}
		power = decay && !loop ? 1 - u : 1;
		float k = intensity * power;
		base.offset(this, (ax + (bx - ax) * frac) * k, (ay + (by - ay) * frac) * k);
		if (rotation != 0) base.rotation(item, (ar + (br - ar) * frac) * rotation * power);
	}
}
