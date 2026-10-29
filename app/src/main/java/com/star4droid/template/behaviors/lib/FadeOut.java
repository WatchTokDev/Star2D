package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Easing;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Fade out", icon = "eye-off", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "The element fades away smoothly and can then hide or remove itself. Good for messages, pickups and closing menus.")
public class FadeOut extends Behavior {
	public enum Ease { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT }

	public enum Trigger { ON_START, ON_CLICK, MANUAL }

	public enum Then { STAY, HIDE, DESTROY }

	@Param(desc = "When the fade starts. MANUAL = only when the play block runs.")
	public Trigger trigger = Trigger.ON_START;
	@Param(desc = "How long the fade lasts.", unit = "s", min = 0.01, max = 30, step = 0.05)
	public float duration = 0.5f;
	@Param(desc = "Wait this long before starting (a message that disappears by itself).", unit = "s", min = 0, max = 600, step = 0.1)
	public float delay = 1.5f;
	@Param(desc = "Opacity at the end (0 = invisible).", min = 0, max = 1, step = 0.05)
	public float toOpacity = 0f;
	@Param(desc = "Size at the end, 1 = normal size. Use 0 to shrink away.", min = 0, max = 3, step = 0.05)
	public float toScale = 1f;
	@Param(desc = "How the speed changes during the fade.")
	public Ease ease = Ease.EASE_IN;
	@Param(desc = "What happens at the end: STAY = stays there (faded), HIDE = hidden (and its physics off), DESTROY = removed.")
	public Then then = Then.HIDE;

	private final Fx.Base base = new Fx.Base();
	private boolean playing, hidden;
	private float t;

	public void onStart() {
		base.capture(item);
		if (toScale != 1f) base.centerOrigin(item);
		if (trigger == Trigger.ON_CLICK) Fx.enableTouch(item);
		if (trigger == Trigger.ON_START) play();
	}

	public void onEnable() {
		if (item != null) base.capture(item);
		if (item != null && trigger == Trigger.ON_START) play();
	}

	public void onDisable() {
		playing = false;
		show();
		base.restore(this);
	}

	public void onDestroy() {
		playing = false;
	}

	private void show() {
		if (!hidden) return;
		hidden = false;
		Actor a = actor();
		a.setVisible(true);
		Body b = body();
		if (b != null) b.setActive(true);
	}

	public void onClick() {
		if (trigger == Trigger.ON_CLICK) play();
	}

	@Block(label = "play fade out", desc = "Fades the element away.", labelAr = "شغّل الاختفاء التدريجي", descAr = "يخفي العنصر تدريجياً.", category = "Fade out")
	public void play() {
		if (hidden) show();
		playing = true;
		t = 0;
	}

	@Block(label = "stop fade out", desc = "Stops the fade and shows the element normally again.", labelAr = "أوقف الاختفاء", descAr = "يوقف الاختفاء ويعيد العنصر لشكله العادي.", category = "Fade out")
	public void cancel() {
		playing = false;
		show();
		base.restore(this);
	}

	@Block(label = "set fade out time", desc = "How long the fade lasts (seconds).", labelAr = "اضبط مدة الاختفاء", descAr = "مدة الاختفاء التدريجي (ثانية).", category = "Fade out", args = {"seconds"})
	public void setDuration(float value) {
		duration = value;
	}

	@Block(label = "set fade out delay", desc = "Seconds to wait before the fade starts.", labelAr = "اضبط تأخير الاختفاء", descAr = "الثواني قبل بدء الاختفاء.", category = "Fade out", args = {"seconds"})
	public void setDelay(float value) {
		delay = value;
	}

	@Block(label = "is fading out", desc = "True while the element is fading out.", labelAr = "يختفي تدريجياً الآن", descAr = "صحيح أثناء الاختفاء التدريجي.", category = "Fade out")
	public boolean isFading() {
		return playing;
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt;
		float u = clamp((t - delay) / Math.max(0.001f, duration), 0, 1);
		float k = Easing.apply(ease.ordinal(), u);
		base.opacity(item, clamp(1 + (toOpacity - 1) * k, 0, 1));
		if (toScale != 1f) {
			float s = 1 + (toScale - 1) * k;
			base.scale(item, s, s);
		}
		if (u >= 1f) {
			playing = false;
			if (then == Then.HIDE) {
				hidden = true;
				actor().setVisible(false);
				Body b = body();
				if (b != null) b.setActive(false);
			}
			fire("onDone");
			if (then == Then.DESTROY) item.destroy();
		}
	}
}
