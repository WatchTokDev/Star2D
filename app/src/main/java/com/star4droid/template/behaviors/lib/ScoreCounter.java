package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Items.TextItem;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Score Counter", icon = "scoreboard", category = "Logic", targets = BehaviorInfo.Target.BOTH,
		events = {"onScoreChanged(float value)", "onNewHighScore(float value)"},
		desc = "Shows a scene variable (score, coins, lives...) in a Text element with a prefix, padding and a counting animation, and keeps a high score on the device.")
public class ScoreCounter extends Behavior {
	@Header("Value")
	@Param(desc = "Scene variable to show (an int or float created in Variables). Collectible and Timer write to variables by name. Empty = the counter keeps its own number.")
	public String variable = "score";

	@Header("Display")
	@Param(desc = "The Text element that shows it. Empty = this element (when it is a Text).")
	public TextItem display;
	@Param(desc = "Written before the number, e.g. SCORE ")
	public String prefix = "";
	@Param(desc = "Written after the number, e.g.  pts")
	public String suffix = "";
	@Param(desc = "Digits after the decimal point.", min = 0, max = 6)
	public int decimals = 0;
	@Param(desc = "Fill with zeros up to this many digits (0005). 0 = off.", min = 0, max = 12)
	public int padDigits = 0;
	@Param(desc = "Group digits: 12,345.")
	public boolean thousandsSeparator = false;
	@Param(desc = "The number runs up/down to the new value instead of jumping.")
	public boolean animate = true;
	@Param(desc = "How long the counting takes at most.", unit = "s", min = 0.05, max = 5, step = 0.05)
	public float animateTime = 0.4f;

	@Header("High score")
	@Param(desc = "Name under which the best value is stored on the device (e.g. highscore_level1). Empty = no high score.")
	public String highScoreKey = "";
	@Param(desc = "Text element that shows the high score (optional).")
	public TextItem highScoreDisplay;
	@Param(desc = "Written before the high score.")
	public String highScorePrefix = "BEST ";

	private final GameplaySupport.Var var = new GameplaySupport.Var();
	private float own, shown = Float.NaN, high, lastValue = Float.NaN, saveTimer;
	private long shownKey = Long.MIN_VALUE;
	private boolean highDirty;

	private PlayerItem out() {
		return display != null ? display : item;
	}

	private double target() {
		return variable.length() > 0 ? var.bind(stage, variable).get() : own;
	}

	public void onStart() {
		high = readHigh();
		renderHigh();
		shown = (float) target();
		lastValue = shown;
		render(true);
	}

	@Block(label = "add score", desc = "Adds points (negative = subtracts).", labelAr = "أضف نقاطاً", descAr = "يضيف نقاطاً (القيمة السالبة تنقص).", category = "Score Counter", args = {"points"})
	public void add(float points) {
		if (variable.length() > 0) var.bind(stage, variable).add(points);
		else own += points;
	}

	@Block(label = "set score", desc = "Sets the value.", labelAr = "اضبط النقاط", descAr = "يضبط القيمة.", category = "Score Counter", args = {"value"})
	public void setScore(float value) {
		if (variable.length() > 0) var.bind(stage, variable).set(value);
		else own = value;
	}

	@Block(label = "score", desc = "Current value.", labelAr = "النقاط", descAr = "القيمة الحالية.", category = "Score Counter")
	public float getScore() {
		return (float) target();
	}

	@Block(label = "reset score", desc = "Back to 0 without the counting animation.", labelAr = "صفّر النقاط", descAr = "يعيد إلى 0 دون حركة العد.", category = "Score Counter")
	public void resetScore() {
		setScore(0);
		shown = 0;
		lastValue = 0;
		render(true);
	}

	@Block(label = "high score", desc = "The best value stored on the device.", labelAr = "أعلى نتيجة", descAr = "أفضل قيمة محفوظة على الجهاز.", category = "Score Counter")
	public float getHighScore() {
		return high;
	}

	@Block(label = "erase high score", desc = "Sets the stored best value back to 0.", labelAr = "امسح أعلى نتيجة", descAr = "يعيد القيمة الأفضل المحفوظة إلى 0.", category = "Score Counter")
	public void resetHighScore() {
		high = 0;
		highDirty = true;
		saveTimer = 0;
		renderHigh();
	}

	private float readHigh() {
		if (highScoreKey.length() == 0) return 0;
		try {
			String v = stage.getValue("hs_" + highScoreKey);
			return v == null || v.length() == 0 ? 0 : Float.parseFloat(v);
		} catch (Throwable e) {
			return 0;
		}
	}

	private void saveHigh() {
		highDirty = false;
		if (highScoreKey.length() == 0) return;
		try {
			stage.setValue("hs_" + highScoreKey, String.valueOf(high));
		} catch (Throwable ignored) {
		}
	}

	private void renderHigh() {
		if (highScoreDisplay != null)
			highScoreDisplay.setItemText(highScorePrefix + GameplaySupport.number(high, decimals, padDigits, thousandsSeparator) + suffix);
	}

	private void render(boolean force) {
		double scale = Math.pow(10, decimals);
		long key = Math.round(shown * scale);
		if (!force && key == shownKey) return;
		shownKey = key;
		PlayerItem o = out();
		if (o != null) o.setItemText(prefix + GameplaySupport.number(shown, decimals, padDigits, thousandsSeparator) + suffix);
	}

	public void onUpdate(float dt) {
		float t = (float) target();
		if (t != lastValue) {
			lastValue = t;
			fire("onScoreChanged", t);
			if (highScoreKey.length() > 0 && t > high) {
				high = t;
				highDirty = true;
				saveTimer = 1f;
				renderHigh();
				fire("onNewHighScore", t);
			}
		}
		if (animate && Math.abs(t - shown) > 1e-4f) {
			float rate = Math.max(1f, Math.abs(t - shown)) / animateTime;
			float step = rate * dt;
			if (Math.abs(t - shown) <= step) shown = t;
			else shown += Math.signum(t - shown) * step;
		} else {
			shown = t;
		}
		render(false);
		if (highDirty) {
			saveTimer -= dt;
			if (saveTimer <= 0) saveHigh();
		}
	}

	public void onDestroy() {
		if (highDirty) saveHigh();
	}
}
