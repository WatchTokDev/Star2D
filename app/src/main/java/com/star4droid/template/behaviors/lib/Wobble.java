package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Wobble", icon = "sine-wave", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "The element rocks left and right like a toy (and can squash a little). Forever, when touched or from a block. Good for idle icons and attention.")
public class Wobble extends Behavior {
	public enum Trigger { ON_START, ON_TOUCH, MANUAL }

	@Param(desc = "When it wobbles. MANUAL = only when the wobble block runs.")
	public Trigger trigger = Trigger.ON_START;
	@Param(desc = "How far it turns each way.", unit = "deg", min = 0, max = 90, step = 0.5)
	public float angle = 8;
	@Param(desc = "Full swings per second.", unit = "Hz", min = 0.1, max = 20, step = 0.1)
	public float speed = 2;
	@Param(desc = "Wobble for this long, then stop. 0 = until stopped.", unit = "s", min = 0, max = 600, step = 0.1)
	public float duration = 0;
	@Param(desc = "Each swing is smaller than the last (needs a duration).")
	public boolean decay = false;
	@Param(desc = "Stretch and squash a little while it swings (0 = none).", min = 0, max = 0.5, step = 0.01)
	public float squash = 0;

	private final Fx.Base base = new Fx.Base();
	private boolean playing;
	private float t;

	public void onStart() {
		base.capture(item);
		base.centerOrigin(item);
		if (trigger == Trigger.ON_TOUCH) Fx.enableTouch(item);
		if (trigger == Trigger.ON_START) wobble();
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
		if (trigger == Trigger.ON_TOUCH) wobble();
	}

	@Block(label = "wobble", desc = "Starts wobbling.", labelAr = "تمايل", descAr = "يبدأ العنصر بالتمايل.", category = "Wobble")
	public void wobble() {
		playing = true;
		t = 0;
	}

	@Block(label = "stop wobbling", desc = "Stops wobbling and straightens the element.", labelAr = "أوقف التمايل", descAr = "يوقف التمايل ويعدّل العنصر.", category = "Wobble")
	public void stop() {
		playing = false;
		base.rotation(item, 0);
		base.scale(item, 1, 1);
	}

	@Block(label = "set wobble angle", desc = "How far it turns each way (degrees).", labelAr = "اضبط زاوية التمايل", descAr = "مقدار الدوران في كل اتجاه (درجات).", category = "Wobble", args = {"degrees"})
	public void setAngle(float value) {
		angle = value;
	}

	@Block(label = "is wobbling", desc = "True while the element wobbles.", labelAr = "يتمايل الآن", descAr = "صحيح أثناء التمايل.", category = "Wobble")
	public boolean isWobbling() {
		return playing;
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt;
		float u = duration > 0 ? t / duration : 0;
		if (duration > 0 && u >= 1f) {
			stop();
			fire("onDone");
			return;
		}
		float power = decay && duration > 0 ? 1 - u : 1;
		float s = (float) Math.sin(t * speed * Math.PI * 2);
		base.rotation(item, s * angle * power);
		if (squash != 0) {
			float q = (float) Math.sin(t * speed * Math.PI * 4) * squash * power;
			base.scale(item, 1 + q, 1 - q);
		}
	}
}
