package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Easing;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Slide in", icon = "transfer-right", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "The element slides to its place from one side, with a bounce or ease if you like. A block slides it back out. Good for menus and panels.")
public class SlideIn extends Behavior {
	public enum Ease { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT }

	public enum From { LEFT, RIGHT, TOP, BOTTOM }

	@Param(desc = "The side it comes from.")
	public From from = From.LEFT;
	@Param(desc = "How far away it starts.", unit = "px", min = 0, max = 10000, step = 10)
	public float distance = 400;
	@Param(desc = "How long the slide lasts.", unit = "s", min = 0.01, max = 30, step = 0.05)
	public float duration = 0.6f;
	@Param(desc = "Wait this long before starting (use different values to slide a list one by one).", unit = "s", min = 0, max = 60, step = 0.05)
	public float delay = 0f;
	@Param(desc = "How the speed changes during the slide.")
	public Ease ease = Ease.BACK_OUT;
	@Param(desc = "Also fade in while sliding.")
	public boolean fade = true;
	@Param(desc = "Start as soon as the scene starts. Otherwise the element stays in place until the play block runs.")
	public boolean autoPlay = true;

	private final Fx.Base base = new Fx.Base();
	private boolean playing, out;
	private float t;

	public void onStart() {
		base.capture(item);
		if (autoPlay) slideIn();
	}

	public void onEnable() {
		if (item != null) {
			base.capture(item);
			if (autoPlay) slideIn();
		}
	}

	public void onDisable() {
		playing = false;
		base.restore(this);
	}

	public void onDestroy() {
		playing = false;
	}

	@Block(label = "slide in", desc = "Slides the element in from its side.", labelAr = "انزلق للداخل", descAr = "يُدخل العنصر منزلقاً من جانبه.", category = "Slide in")
	public void slideIn() {
		playing = true;
		out = false;
		t = 0;
		apply(0);
	}

	@Block(label = "slide out", desc = "Slides the element away to its side.", labelAr = "انزلق للخارج", descAr = "يُخرج العنصر منزلقاً إلى جانبه.", category = "Slide in")
	public void slideOut() {
		playing = true;
		out = true;
		t = 0;
	}

	@Block(label = "set slide distance", desc = "How far away the slide starts (px).", labelAr = "اضبط مسافة الانزلاق", descAr = "المسافة التي يبدأ منها الانزلاق (بكسل).", category = "Slide in", args = {"distance"})
	public void setDistance(float value) {
		distance = value;
	}

	@Block(label = "is sliding", desc = "True while the element slides.", labelAr = "ينزلق الآن", descAr = "صحيح أثناء الانزلاق.", category = "Slide in")
	public boolean isSliding() {
		return playing;
	}

	/** k = 0: far away / invisible, 1: in place. */
	private void apply(float k) {
		float d = (1 - k) * distance;
		float x = from == From.LEFT ? -d : (from == From.RIGHT ? d : 0);
		float y = from == From.BOTTOM ? -d : (from == From.TOP ? d : 0);
		base.offset(this, x, y);
		if (fade) base.opacity(item, clamp(k * 1.6f, 0, 1));
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt;
		float u = clamp((t - delay) / Math.max(0.001f, duration), 0, 1);
		float e = Easing.apply(ease.ordinal(), u);
		apply(out ? 1 - e : e);
		if (u >= 1f) {
			playing = false;
			apply(out ? 0 : 1);
			fire("onDone");
		}
	}
}
