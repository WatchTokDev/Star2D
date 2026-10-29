package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Color;
import com.star4droid.template.Items.TextItem;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Timer", icon = "timer", category = "Logic", targets = BehaviorInfo.Target.BOTH,
		events = {"onTimeout()", "onTick(int seconds)", "onWarning()"},
		desc = "A countdown or a stopwatch shown in a Text element: level time limits, race times, cooldown displays. Start, pause and reset it with blocks; it can also write the seconds into a scene variable.")
public class Timer extends Behavior {
	public enum TimerMode { COUNTDOWN, STOPWATCH }
	public enum TimeFormat { M_SS, SECONDS, TENTHS, M_SS_CC }

	@Header("Timer")
	@Param(desc = "COUNTDOWN counts down from the seconds below to 0, STOPWATCH counts up.")
	public TimerMode mode = TimerMode.COUNTDOWN;
	@Param(desc = "Countdown: where it starts. Stopwatch: stops (onTimeout) at this time, 0 = never.", unit = "s", min = 0, max = 360000, step = 1)
	public float seconds = 60;
	@Param(desc = "Starts when the scene starts. Off: call start timer.")
	public boolean autoStart = true;
	@Param(desc = "Starts again by itself after onTimeout.")
	public boolean loop = false;

	@Header("Display")
	@Param(desc = "The Text element that shows the time. Empty = this element (when it is a Text).")
	public TextItem display;
	@Param(desc = "M_SS = 1:05, SECONDS = 65, TENTHS = 65.3, M_SS_CC = 01:05.30.")
	public TimeFormat format = TimeFormat.M_SS;
	@Param(desc = "Written before the time, e.g. TIME ")
	public String prefix = "";
	@Param(desc = "Written after the time, e.g. s")
	public String suffix = "";
	@Param(desc = "Countdown: the text turns the warning color and onWarning fires when this many seconds are left. 0 = off.", unit = "s", min = 0, max = 3600, step = 1)
	public float warnAt = 10;
	@Param(desc = "Color of the text during the warning.")
	public Color warnColor = Color.valueOf("FF5252");

	@Header("Variable")
	@Param(desc = "Scene variable (int or float) that receives the time in seconds. Empty = none.")
	public String variable = "";

	private float time;
	private boolean running, warned, started;
	private int shownKey = -1, tickKey = -1;
	private final Color normal = new Color(1, 1, 1, 1);
	private final GameplaySupport.Var var = new GameplaySupport.Var();

	private PlayerItem out() {
		return display != null ? display : item;
	}

	public void onStart() {
		started = true;
		PlayerItem o = out();
		if (o != null) normal.set(o.getActor().getColor());
		resetNow();
		running = autoStart;
		render(true);
	}

	// ------------------------------------------------------------------ blocks

	@Block(label = "start timer", desc = "Starts (or resumes) the timer.", labelAr = "ابدأ المؤقت", descAr = "يبدأ المؤقت (أو يتابعه).", category = "Timer")
	public void start() {
		running = true;
	}

	@Block(label = "pause timer", desc = "Stops counting; the time stays.", labelAr = "أوقف المؤقت مؤقتاً", descAr = "يتوقف العد ويبقى الوقت كما هو.", category = "Timer")
	public void pause() {
		running = false;
	}

	@Block(label = "reset timer", desc = "Back to the starting time (the running state does not change).", labelAr = "صفّر المؤقت", descAr = "يعيد إلى وقت البداية (دون تغيير حالة التشغيل).", category = "Timer")
	public void reset() {
		resetNow();
		render(true);
	}

	@Block(label = "restart timer", desc = "Reset and start.", labelAr = "أعد تشغيل المؤقت", descAr = "تصفير ثم بدء.", category = "Timer")
	public void restart() {
		resetNow();
		running = true;
		render(true);
	}

	@Block(label = "add time", desc = "Adds seconds to the time shown (countdown: more time left; stopwatch: later).", labelAr = "أضف وقتاً", descAr = "يضيف ثواني إلى الوقت المعروض.", category = "Timer", args = {"seconds"})
	public void addTime(float extra) {
		time = Math.max(0f, time + extra);
		render(false);
	}

	@Block(label = "set time", desc = "Sets the time shown, in seconds.", labelAr = "اضبط الوقت", descAr = "يضبط الوقت المعروض بالثواني.", category = "Timer", args = {"seconds"})
	public void setTime(float value) {
		time = Math.max(0f, value);
		render(false);
	}

	@Block(label = "time", desc = "The time shown, in seconds (countdown: left; stopwatch: elapsed).", labelAr = "الوقت", descAr = "الوقت المعروض بالثواني.", category = "Timer")
	public float getTime() {
		return time;
	}

	@Block(label = "timer is running", desc = "True while it counts.", labelAr = "المؤقت يعمل", descAr = "صحيح طالما يعد.", category = "Timer")
	public boolean isRunning() {
		return running;
	}

	// ------------------------------------------------------------------ logic

	private void resetNow() {
		time = mode == TimerMode.COUNTDOWN ? seconds : 0f;
		warned = false;
		tickKey = -1;
		applyColor(false);
	}

	private boolean down() {
		return mode == TimerMode.COUNTDOWN;
	}

	private void applyColor(boolean warn) {
		PlayerItem o = out();
		if (o == null) return;
		o.getActor().setColor(warn ? warnColor : normal);
	}

	private void render(boolean force) {
		int style = format.ordinal();
		int key = GameplaySupport.timeKey(time, style, !down());
		if (!force && key == shownKey) return;
		shownKey = key;
		PlayerItem o = out();
		if (o != null) o.setItemText(prefix + GameplaySupport.time(time, style, !down()) + suffix);
		if (variable.length() > 0) var.bind(stage, variable).set(time);
	}

	public void onUpdate(float dt) {
		if (!running) return;
		boolean over = false;
		if (down()) {
			time -= dt;
			if (time <= 0) {
				time = 0;
				over = true;
			}
		} else {
			time += dt;
			if (seconds > 0 && time >= seconds) {
				time = seconds;
				over = true;
			}
		}
		render(false);
		int whole = down() ? (int) Math.ceil(time - 1e-3) : (int) Math.floor(time + 1e-3);
		if (whole != tickKey) {
			tickKey = whole;
			fire("onTick", whole);
		}
		if (down() && warnAt > 0 && !warned && time <= warnAt && !over) {
			warned = true;
			applyColor(true);
			fire("onWarning");
		}
		if (over) {
			running = false;
			fire("onTimeout");
			if (loop) {
				resetNow();
				running = true;
				render(true);
			}
		}
	}

	public void onDisable() {
		if (started) applyColor(false);
	}
}
