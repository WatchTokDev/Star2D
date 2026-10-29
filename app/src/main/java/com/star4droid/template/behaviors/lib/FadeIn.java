package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Easing;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Fade in", icon = "circle-opacity", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "The element appears smoothly: from invisible (and optionally smaller) to its normal look. Good for menus and titles.")
public class FadeIn extends Behavior {
	public enum Ease { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT }

	@Param(desc = "How long the fade lasts.", unit = "s", min = 0.01, max = 30, step = 0.05)
	public float duration = 0.5f;
	@Param(desc = "Wait this long before starting.", unit = "s", min = 0, max = 60, step = 0.05)
	public float delay = 0f;
	@Param(desc = "Opacity at the start (0 = invisible).", min = 0, max = 1, step = 0.05)
	public float fromOpacity = 0f;
	@Param(desc = "Size at the start, 1 = normal size. Use 0.5 to make it \"pop\" in.", min = 0, max = 3, step = 0.05)
	public float fromScale = 1f;
	@Param(desc = "How the speed changes during the fade.")
	public Ease ease = Ease.EASE_OUT;
	@Param(desc = "Start as soon as the scene starts. Otherwise the element is shown normally until the play block runs.")
	public boolean autoPlay = true;

	private final Fx.Base base = new Fx.Base();
	private boolean playing;
	private float t;

	public void onStart() {
		base.capture(item);
		if (fromScale != 1f) base.centerOrigin(item);
		if (autoPlay) play();
	}

	public void onEnable() {
		if (item != null) {
			base.capture(item);
			if (autoPlay) play();
		}
	}

	public void onDisable() {
		playing = false;
		base.restore(this);
	}

	public void onDestroy() {
		playing = false;
	}

	@Block(label = "play fade in", desc = "Hides the element and fades it in.", labelAr = "شغّل الظهور التدريجي", descAr = "يخفي العنصر ثم يظهره تدريجياً.", category = "Fade in")
	public void play() {
		playing = true;
		t = 0;
		apply(0);
	}

	@Block(label = "set fade in time", desc = "How long the fade lasts (seconds).", labelAr = "اضبط مدة الظهور", descAr = "مدة الظهور التدريجي (ثانية).", category = "Fade in", args = {"seconds"})
	public void setDuration(float value) {
		duration = value;
	}

	@Block(label = "is fading in", desc = "True while the element is fading in.", labelAr = "يظهر تدريجياً الآن", descAr = "صحيح أثناء الظهور التدريجي.", category = "Fade in")
	public boolean isFading() {
		return playing;
	}

	private void apply(float k) {
		base.opacity(item, clamp(fromOpacity + (1 - fromOpacity) * k, 0, 1));
		if (fromScale != 1f) {
			float s = fromScale + (1 - fromScale) * k;
			base.scale(item, s, s);
		}
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt;
		float u = clamp((t - delay) / Math.max(0.001f, duration), 0, 1);
		apply(Easing.apply(ease.ordinal(), u));
		if (u >= 1f) {
			playing = false;
			apply(1);
			fire("onDone");
		}
	}
}
