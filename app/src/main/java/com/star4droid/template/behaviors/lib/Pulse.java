package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Pulse", icon = "heart-pulse", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		events = {"onPulse()"},
		desc = "The element grows and shrinks again and again, like a beating heart. Good for \"Play\" buttons and pickups.")
public class Pulse extends Behavior {
	@Param(desc = "Smallest size, 1 = normal size.", min = 0.1, max = 3, step = 0.01)
	public float minScale = 0.95f;
	@Param(desc = "Biggest size, 1 = normal size.", min = 0.1, max = 3, step = 0.01)
	public float maxScale = 1.08f;
	@Param(desc = "Beats per second.", unit = "Hz", min = 0.05, max = 10, step = 0.05)
	public float speed = 1.2f;
	@Param(desc = "Opacity at the smallest size (1 = no fading).", min = 0, max = 1, step = 0.05)
	public float lowOpacity = 1f;
	@Param(desc = "Start pulsing as soon as the scene starts. Otherwise use the start / pulse once blocks.")
	public boolean autoPlay = true;
	@Param(desc = "Keep pulsing while the element is touched.")
	public boolean pulseWhileTouched = true;

	private final Fx.Base base = new Fx.Base();
	private boolean playing, down;
	private float phase; // cycles
	private int cyclesLeft = -1; // -1 = forever

	private void begin() {
		base.capture(item);
		base.centerOrigin(item);
		phase = 0;
	}

	public void onStart() {
		begin();
		playing = autoPlay;
		cyclesLeft = -1;
	}

	public void onEnable() {
		if (item != null) begin();
	}

	public void onDisable() {
		base.restore(this);
		down = false;
	}

	public void onDestroy() {
		base.restore(this);
	}

	@Block(label = "start pulse", desc = "Starts pulsing until stopped.", labelAr = "ابدأ النبض", descAr = "يبدأ النبض حتى يتم إيقافه.", category = "Pulse")
	public void start() {
		playing = true;
		cyclesLeft = -1;
	}

	@Block(label = "pulse once", desc = "Does one beat and stops.", labelAr = "نبضة واحدة", descAr = "ينبض مرة واحدة ثم يتوقف.", category = "Pulse")
	public void pulseOnce() {
		playing = true;
		phase = 0;
		cyclesLeft = 1;
	}

	@Block(label = "stop pulse", desc = "Stops pulsing and returns to the normal size.", labelAr = "أوقف النبض", descAr = "يوقف النبض ويعود للحجم العادي.", category = "Pulse")
	public void stop() {
		playing = false;
		base.scale(item, 1, 1);
		base.opacity(item, 1);
	}

	@Block(label = "set pulse speed", desc = "Beats per second.", labelAr = "اضبط سرعة النبض", descAr = "عدد النبضات في الثانية.", category = "Pulse", args = {"beats"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "set pulse size", desc = "Biggest size (1 = normal).", labelAr = "اضبط حجم النبض", descAr = "أكبر حجم (1 = عادي).", category = "Pulse", args = {"scale"})
	public void setMaxScale(float value) {
		maxScale = value;
	}

	@Block(label = "is pulsing", desc = "True while the element pulses.", labelAr = "ينبض الآن", descAr = "صحيح أثناء النبض.", category = "Pulse")
	public boolean isPulsing() {
		return playing;
	}

	public void onTouchStart(com.badlogic.gdx.scenes.scene2d.InputEvent e) {
		down = true;
	}

	public void onTouchEnd(com.badlogic.gdx.scenes.scene2d.InputEvent e) {
		down = false;
	}

	public void onUpdate(float dt) {
		if (!playing || (down && !pulseWhileTouched)) return;
		phase += dt * speed;
		if (phase >= 1f) {
			phase -= 1f;
			if (cyclesLeft > 0 && --cyclesLeft == 0) {
				stop();
				fire("onPulse");
				return;
			}
			fire("onPulse");
		}
		// 0 at the start of a cycle (smallest), 1 in the middle (biggest)
		float k = 0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2);
		float s = minScale + (maxScale - minScale) * k;
		base.scale(item, s, s);
		base.opacity(item, lowOpacity + (1 - lowOpacity) * k);
	}
}
