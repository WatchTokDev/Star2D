package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Easing;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Count up", icon = "numeric", category = "Text", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "A Text element counts from one number to another, like a score. Optional prefix, suffix and thousands separator.")
public class CountUp extends Behavior {
	public enum Ease { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BACK_OUT, BOUNCE_OUT, ELASTIC_OUT }

	@Param(desc = "Number it starts from.", min = -1000000000, max = 1000000000)
	public float from = 0;
	@Param(desc = "Number it counts to.", min = -1000000000, max = 1000000000)
	public float to = 100;
	@Param(desc = "How long the counting lasts.", unit = "s", min = 0.05, max = 120, step = 0.1)
	public float duration = 1.5f;
	@Param(desc = "Wait this long before counting.", unit = "s", min = 0, max = 60, step = 0.1)
	public float delay = 0f;
	@Param(desc = "Digits after the dot.", min = 0, max = 6)
	public int decimals = 0;
	@Param(desc = "Text before the number, e.g. $ or Score: ")
	public String prefix = "";
	@Param(desc = "Text after the number, e.g. % or pts")
	public String suffix = "";
	@Param(desc = "Write 1,234,567 instead of 1234567.")
	public boolean thousandsSeparator = false;
	@Param(desc = "How the speed changes while counting.")
	public Ease ease = Ease.EASE_OUT;
	@Param(desc = "Start counting as soon as the scene starts. Otherwise use the count to block.")
	public boolean autoPlay = true;

	private static final long[] POW10 = {1L, 10L, 100L, 1000L, 10000L, 100000L, 1000000L, 10000000L, 100000000L, 1000000000L, 10000000000L, 100000000000L, 1000000000000L, 10000000000000L, 100000000000000L, 1000000000000000L, 10000000000000000L, 100000000000000000L, 1000000000000000000L};

	private Label label;
	private final StringBuilder sb = new StringBuilder();
	private float current, t;
	private boolean playing;

	public void onStart() {
		if (actor() instanceof Label) label = (Label) actor();
		if (label == null) return;
		current = from;
		show(current);
		if (autoPlay) play();
	}

	public void onEnable() {
		if (label != null && autoPlay) play();
	}

	public void onDisable() {
		playing = false;
	}

	@Block(label = "count again", desc = "Counts from the start number to the end number.", labelAr = "عُدّ مجدداً", descAr = "يعدّ من رقم البداية إلى رقم النهاية.", category = "Count up")
	public void play() {
		if (label == null) return;
		current = from;
		t = 0;
		playing = true;
		show(current);
	}

	@Block(label = "count to", desc = "Counts from what is shown now to the given number.", labelAr = "عُدّ حتى", descAr = "يعدّ من الرقم المعروض حالياً إلى الرقم المعطى.", category = "Count up", args = {"number"})
	public void countTo(float value) {
		if (label == null) return;
		from = current;
		to = value;
		t = 0;
		delay = 0;
		playing = true;
	}

	@Block(label = "set number", desc = "Shows the number at once, without counting.", labelAr = "اضبط الرقم", descAr = "يعرض الرقم فوراً بدون عدّ.", category = "Count up", args = {"number"})
	public void setNumber(float value) {
		if (label == null) return;
		playing = false;
		from = value;
		to = value;
		current = value;
		show(value);
	}

	@Block(label = "shown number", desc = "The number shown right now.", labelAr = "الرقم المعروض", descAr = "الرقم المعروض حالياً.", category = "Count up")
	public float getNumber() {
		return current;
	}

	@Block(label = "is counting", desc = "True while the number is changing.", labelAr = "يعدّ الآن", descAr = "صحيح أثناء تغيّر الرقم.", category = "Count up")
	public boolean isCounting() {
		return playing;
	}

	private void show(float v) {
		int d = decimals < 0 ? 0 : (decimals > 6 ? 6 : decimals);
		sb.setLength(0);
		sb.append(prefix);
		if (v < 0) {
			sb.append('-');
			v = -v;
		}
		long total = Math.round((double) v * POW10[d]);
		long ip = total / POW10[d], fp = total % POW10[d];
		int digits = 1;
		for (long p = ip; p >= 10; p /= 10) digits++;
		for (int i = digits - 1; i >= 0; i--) {
			sb.append((char) ('0' + (ip / POW10[i]) % 10));
			if (thousandsSeparator && i > 0 && i % 3 == 0) sb.append(',');
		}
		if (d > 0) {
			sb.append('.');
			for (int i = d - 1; i >= 0; i--) sb.append((char) ('0' + (fp / POW10[i]) % 10));
		}
		sb.append(suffix);
		label.setText(sb);
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		t += dt;
		float u = clamp((t - delay) / Math.max(0.001f, duration), 0, 1);
		current = from + (to - from) * Easing.apply(ease.ordinal(), u);
		show(current);
		if (u >= 1f) {
			playing = false;
			current = to;
			show(current);
			fire("onDone");
		}
	}
}
