package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;

@BehaviorInfo(name = "Typewriter", icon = "keyboard", category = "Text", targets = BehaviorInfo.Target.BOTH,
		events = {"onDone()"},
		desc = "A Text element types its text letter by letter, like a dialogue box. Tap to show it all at once.")
public class Typewriter extends Behavior {
	@Param(desc = "Letters per second.", unit = "letters/s", min = 1, max = 500, step = 1)
	public float speed = 30;
	@Param(desc = "Wait this long before typing starts.", unit = "s", min = 0, max = 60, step = 0.1)
	public float startDelay = 0f;
	@Param(desc = "Extra pause after . , ! ? so sentences breathe (0 = none).", unit = "s", min = 0, max = 3, step = 0.05)
	public float punctuationPause = 0.15f;
	@Param(desc = "Type another text instead of the one written in the element (empty = use the element's text).")
	public String customText = "";
	@Param(desc = "Touching the text shows all of it at once.")
	public boolean tapToSkip = true;
	@Param(desc = "Start as soon as the scene starts. Otherwise use the type text block.")
	public boolean autoPlay = true;
	@Param(desc = "Tiny sound for the letters (optional).")
	@Sound
	public String tickSound = "";
	@Param(desc = "Play the tick sound every this many letters.", min = 1, max = 20)
	public int tickEvery = 2;

	private Label label;
	private String full = "";
	private final StringBuilder sb = new StringBuilder();
	private int shown, sinceTick;
	private float acc, wait;
	private boolean playing, soundBroken;

	public void onStart() {
		if (actor() instanceof Label) label = (Label) actor();
		if (label == null) return;
		full = customText.length() > 0 ? customText : label.getText().toString();
		if (tapToSkip) Fx.enableTouch(item);
		if (autoPlay) restart();
	}

	public void onEnable() {
		if (label != null && autoPlay) restart();
	}

	/** Shows the whole text again (the saved element text is never changed). */
	public void onDisable() {
		playing = false;
		if (label != null) label.setText(full);
	}

	public void onDestroy() {
		playing = false;
	}

	private void restart() {
		sb.setLength(0);
		shown = 0;
		sinceTick = 0;
		acc = 0;
		wait = startDelay;
		playing = true;
		label.setText(sb);
	}

	private void finish() {
		playing = false;
		sb.setLength(0);
		sb.append(full);
		label.setText(sb);
		fire("onDone");
	}

	public void onClick() {
		if (playing && tapToSkip) finish();
	}

	@Block(label = "type text", desc = "Types the given text letter by letter.", labelAr = "اكتب النص", descAr = "يكتب النص المعطى حرفاً بحرف.", category = "Typewriter", args = {"text"})
	public void typeText(String text) {
		if (label == null) return;
		full = text == null ? "" : text;
		restart();
	}

	@Block(label = "type again", desc = "Types the current text again from the start.", labelAr = "أعد الكتابة", descAr = "يعيد كتابة النص من البداية.", category = "Typewriter")
	public void typeAgain() {
		if (label != null) restart();
	}

	@Block(label = "show all text", desc = "Shows the whole text at once.", labelAr = "أظهر كل النص", descAr = "يُظهر النص كاملاً دفعة واحدة.", category = "Typewriter")
	public void skip() {
		if (label != null && playing) finish();
	}

	@Block(label = "set typing speed", desc = "Letters per second.", labelAr = "اضبط سرعة الكتابة", descAr = "عدد الحروف في الثانية.", category = "Typewriter", args = {"speed"})
	public void setSpeed(float value) {
		speed = value;
	}

	@Block(label = "is typing", desc = "True while letters are still being typed.", labelAr = "يكتب الآن", descAr = "صحيح ما دام النص يُكتب.", category = "Typewriter")
	public boolean isTyping() {
		return playing;
	}

	public void onUpdate(float dt) {
		if (!playing) return;
		if (wait > 0) {
			wait -= dt;
			return;
		}
		acc += dt * speed;
		int target = (int) acc;
		boolean changed = false;
		int n = full.length();
		while (shown < target && shown < n) {
			char c = full.charAt(shown++);
			sb.append(c);
			changed = true;
			if (c != ' ' && ++sinceTick >= tickEvery) {
				sinceTick = 0;
				if (!soundBroken && tickSound.length() > 0 && !Fx.playSound(stage, tickSound)) soundBroken = true;
			}
			if (punctuationPause > 0 && (c == '.' || c == ',' || c == '!' || c == '?' || c == ':')) {
				wait = punctuationPause;
				acc = shown;
				break;
			}
		}
		if (changed) label.setText(sb);
		if (shown >= n) finish();
	}
}
