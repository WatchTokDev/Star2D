package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Progress bind", icon = "link-variant", category = "Logic", targets = BehaviorInfo.Target.BOTH,
		events = {"onFull()", "onEmpty()"},
		desc = "Keeps a Progress bar in step with a number: a value saved with setValue, another bar, or the number written in a Text. Health, loading, XP, timers.")
public class ProgressBind extends Behavior {
	public enum Source { SAVED_VALUE, OTHER_PROGRESS, OTHER_TEXT, MANUAL }

	@Param(desc = "Where the number comes from. SAVED_VALUE = a value saved by name (setValue / getValue), OTHER_PROGRESS = the value of another Progress element, OTHER_TEXT = the first number written in a Text element, MANUAL = only the set value block.")
	public Source source = Source.SAVED_VALUE;
	@Param(desc = "Name of the saved value (SAVED_VALUE).")
	public String valueName = "";
	@Param(desc = "The other element (OTHER_PROGRESS / OTHER_TEXT).")
	public PlayerItem other;
	@Param(desc = "Number that shows an empty bar.", min = -1000000000, max = 1000000000)
	public float minValue = 0;
	@Param(desc = "Number that shows a full bar.", min = -1000000000, max = 1000000000)
	public float maxValue = 100;
	@Param(desc = "How fast the bar follows (0 = instantly).", unit = "1/s", min = 0, max = 60, step = 0.5)
	public float smoothing = 8;
	@Param(desc = "How often the source is read; a value saved by name is read from storage.", unit = "s", min = 0.016, max = 2, step = 0.01)
	public float pollInterval = 0.1f;

	private ProgressItem bar;
	private float wanted, shown, poll, manual;
	private boolean full, empty, first = true;

	public void onStart() {
		if (item instanceof ProgressItem) bar = (ProgressItem) item;
		else if (actor() instanceof ProgressItem) bar = (ProgressItem) actor();
		first = true;
		poll = 0;
	}

	public void onEnable() {
		first = true;
		poll = 0;
	}

	@Block(label = "set bar value", desc = "Sets the number the bar shows (use with source MANUAL).", labelAr = "اضبط قيمة الشريط", descAr = "يحدد الرقم الذي يعرضه الشريط (مع المصدر MANUAL).", category = "Progress bind", args = {"value"})
	public void setBarValue(float value) {
		manual = value;
		poll = 0;
	}

	@Block(label = "set bar maximum", desc = "The number that shows a full bar.", labelAr = "اضبط الحد الأقصى للشريط", descAr = "الرقم الذي يُظهر الشريط ممتلئاً.", category = "Progress bind", args = {"maximum"})
	public void setMaxValue(float value) {
		maxValue = value;
	}

	@Block(label = "bar fraction", desc = "How full the bar is, from 0 to 1.", labelAr = "نسبة امتلاء الشريط", descAr = "مقدار امتلاء الشريط من 0 إلى 1.", category = "Progress bind")
	public float getFraction() {
		return shown;
	}

	private float read() {
		switch (source) {
			case SAVED_VALUE:
				if (valueName.length() == 0) return wantedValue();
				String s = stage.getValue(valueName);
				return s == null || s.length() == 0 ? minValue : (float) parse(s);
			case OTHER_PROGRESS:
				if (!alive(other)) return wantedValue();
				return other instanceof ProgressItem ? ((ProgressItem) other).getProgressF() : other.getProgress();
			case OTHER_TEXT:
				if (alive(other) && other.getActor() instanceof Label) return (float) parse(((Label) other.getActor()).getText());
				return wantedValue();
			default:
				return manual;
		}
	}

	private float wantedValue() {
		return minValue + wanted * (maxValue - minValue);
	}

	/** First number in the text ("Score: 1,250" -> 1250, "-3.5 hp" -> -3.5); 0 when there is none. */
	static double parse(CharSequence s) {
		int n = s.length(), i = 0;
		while (i < n && !(Character.isDigit(s.charAt(i)) || (s.charAt(i) == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1))))) i++;
		if (i >= n) return 0;
		boolean neg = false;
		if (s.charAt(i) == '-') {
			neg = true;
			i++;
		}
		double v = 0, scale = 0;
		for (; i < n; i++) {
			char c = s.charAt(i);
			if (c >= '0' && c <= '9') {
				v = v * 10 + (c - '0');
				if (scale > 0) scale *= 10;
			} else if (c == '.' && scale == 0) scale = 1;
			else if (c == ',' && scale == 0) continue;
			else break;
		}
		if (scale > 0) v /= scale;
		return neg ? -v : v;
	}

	public void onUpdate(float dt) {
		if (bar == null) return;
		poll -= dt;
		if (poll <= 0) {
			poll = pollInterval;
			float range = maxValue - minValue;
			wanted = range == 0 ? 0 : clamp((read() - minValue) / range, 0, 1);
		}
		if (first || smoothing <= 0) {
			shown = wanted;
			first = false;
		} else {
			shown += (wanted - shown) * clamp(smoothing * dt, 0, 1);
			if (Math.abs(wanted - shown) < 0.0005f) shown = wanted;
		}
		float v = shown * bar.getMaxF();
		if (v != bar.getProgressF()) bar.setProgress(v);
		boolean f = shown >= 0.9999f, e = shown <= 0.0001f;
		if (f && !full) fire("onFull");
		if (e && !empty) fire("onEmpty");
		full = f;
		empty = e;
	}
}
