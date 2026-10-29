package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Color;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Tint cycle", icon = "palette", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		desc = "The element's color slowly changes: back and forth between two colors, or through the whole rainbow. Good for titles, power-ups and danger signs.")
public class TintCycle extends Behavior {
	public enum Mode { BETWEEN, RAINBOW }

	@Param(desc = "BETWEEN = fades between the two colors, RAINBOW = goes through all colors.")
	public Mode mode = Mode.BETWEEN;
	@Param(desc = "First color (BETWEEN).")
	public Color colorA = Color.valueOf("FFFFFF");
	@Param(desc = "Second color (BETWEEN).")
	public Color colorB = Color.valueOf("FF5252");
	@Param(desc = "Full cycles per second.", unit = "Hz", min = 0.01, max = 10, step = 0.05)
	public float speed = 0.5f;
	@Param(desc = "How strong the colors are (RAINBOW).", min = 0, max = 1, step = 0.05)
	public float saturation = 0.6f;
	@Param(desc = "How light the colors are (RAINBOW).", min = 0, max = 1, step = 0.05)
	public float brightness = 1f;
	@Param(desc = "Start as soon as the scene starts.")
	public boolean autoPlay = true;

	private final Fx.Base base = new Fx.Base();
	private boolean playing;
	private float t;

	public void onStart() {
		base.capture(item);
		playing = autoPlay;
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

	@Block(label = "start color cycle", desc = "Starts changing the color.", labelAr = "ابدأ تغيير اللون", descAr = "يبدأ تغيير اللون.", category = "Tint cycle")
	public void start() {
		playing = true;
	}

	@Block(label = "stop color cycle", desc = "Stops and gives the element its normal color back.", labelAr = "أوقف تغيير اللون", descAr = "يوقف التغيير ويعيد اللون الأصلي.", category = "Tint cycle")
	public void stop() {
		playing = false;
		Color c = actor().getColor();
		c.set(base.r, base.g, base.b, c.a);
	}

	@Block(label = "set color cycle speed", desc = "Full cycles per second.", labelAr = "اضبط سرعة تغيير اللون", descAr = "عدد الدورات الكاملة في الثانية.", category = "Tint cycle", args = {"cycles"})
	public void setSpeed(float value) {
		speed = value;
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt * speed;
		if (t > 1000f) t -= 1000f;
		Color c = actor().getColor();
		float ph = t - (float) Math.floor(t);
		if (mode == Mode.RAINBOW) {
			hsv(ph, saturation, brightness, c);
		} else {
			float k = 0.5f - 0.5f * (float) Math.cos(ph * Math.PI * 2);
			c.set(colorA.r + (colorB.r - colorA.r) * k, colorA.g + (colorB.g - colorA.g) * k, colorA.b + (colorB.b - colorA.b) * k, c.a);
		}
	}

	/** In-place HSV -> RGB (no allocation); the alpha of c is kept. */
	private static void hsv(float h, float s, float v, Color c) {
		float x = h * 6f;
		int i = (int) x;
		float f = x - i, p = v * (1 - s), q = v * (1 - s * f), r2 = v * (1 - s * (1 - f));
		switch (i % 6) {
			case 0: c.set(v, r2, p, c.a); break;
			case 1: c.set(q, v, p, c.a); break;
			case 2: c.set(p, v, r2, c.a); break;
			case 3: c.set(p, q, v, c.a); break;
			case 4: c.set(r2, p, v, c.a); break;
			default: c.set(v, p, q, c.a); break;
		}
	}
}
