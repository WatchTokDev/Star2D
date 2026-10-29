package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Blink", icon = "eye", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "The element flashes on and off (or fades in and out). Good for \"Tap to start\" texts, warnings and invincibility.")
public class Blink extends Behavior {
	@Param(desc = "How long it stays visible each time.", unit = "s", min = 0.02, max = 30, step = 0.05)
	public float onTime = 0.5f;
	@Param(desc = "How long it stays hidden each time.", unit = "s", min = 0.02, max = 30, step = 0.05)
	public float offTime = 0.3f;
	@Param(desc = "Opacity while \"off\" (0 = invisible).", min = 0, max = 1, step = 0.05)
	public float offOpacity = 0f;
	@Param(desc = "Fade smoothly instead of switching at once.")
	public boolean smooth = false;
	@Param(desc = "Number of blinks, then it stops visible. 0 = forever.", min = 0, max = 1000)
	public int count = 0;
	@Param(desc = "Start blinking as soon as the scene starts.")
	public boolean autoPlay = true;

	private final Fx.Base base = new Fx.Base();
	private boolean playing, shown = true;
	private float t, fade = 1;
	private int done;

	private void begin() {
		base.capture(item);
		t = 0;
		done = 0;
		shown = true;
		fade = 1;
	}

	public void onStart() {
		begin();
		playing = autoPlay;
	}

	public void onEnable() {
		if (item != null) begin();
	}

	public void onDisable() {
		base.restore(this);
	}

	public void onDestroy() {
		base.restore(this);
	}

	@Block(label = "start blinking", desc = "Starts blinking.", labelAr = "ابدأ الوميض", descAr = "يبدأ الوميض.", category = "Blink")
	public void start() {
		begin();
		playing = true;
	}

	@Block(label = "stop blinking", desc = "Stops blinking and shows the element.", labelAr = "أوقف الوميض", descAr = "يوقف الوميض ويُظهر العنصر.", category = "Blink")
	public void stop() {
		playing = false;
		base.opacity(item, 1);
	}

	@Block(label = "blink times", desc = "Blinks the given number of times, then stops.", labelAr = "اومض عدة مرات", descAr = "يومض العدد المحدد من المرات ثم يتوقف.", category = "Blink", args = {"times"})
	public void blink(int times) {
		count = times;
		start();
	}

	@Block(label = "set blink speed", desc = "Seconds visible and seconds hidden in each blink.", labelAr = "اضبط مدة الوميض", descAr = "مدة الظهور (ثانية) لكل وميضة.", category = "Blink", args = {"seconds"})
	public void setBlinkTime(float seconds) {
		onTime = seconds;
		offTime = seconds;
	}

	@Block(label = "is blinking", desc = "True while blinking.", labelAr = "يومض الآن", descAr = "صحيح أثناء الوميض.", category = "Blink")
	public boolean isBlinking() {
		return playing;
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt;
		float len = shown ? onTime : offTime;
		if (t >= len) {
			t -= len;
			shown = !shown;
			if (shown && count > 0 && ++done >= count) {
				playing = false;
				base.opacity(item, 1);
				fire("onDone");
				return;
			}
		}
		float target = shown ? 1 : 0;
		if (smooth) fade += (target - fade) * clamp(dt * 14f, 0, 1);
		else fade = target;
		float k = fade;
		base.opacity(item, offOpacity + (1 - offOpacity) * k);
	}
}
