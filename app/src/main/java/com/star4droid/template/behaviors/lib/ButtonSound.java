package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;

@BehaviorInfo(name = "Button sound", icon = "volume-high", category = "Input", targets = BehaviorInfo.Target.BOTH,
		desc = "Plays a sound when the element is touched, released or clicked. Add it to every button of your menu.")
public class ButtonSound extends Behavior {
	public enum When { TOUCH_START, CLICK }

	@Param(desc = "Sound played when the touch starts / the click happens (a file of the project's sounds).")
	@Sound
	public String sound = "";
	@Param(desc = "Sound played when the finger lifts (optional).")
	@Sound
	public String releaseSound = "";
	@Param(desc = "TOUCH_START = as soon as it is pressed, CLICK = only when it is pressed and released on it.")
	public When when = When.TOUCH_START;
	@Param(desc = "Ignore touches that come sooner than this after the last sound.", unit = "s", min = 0, max = 2, step = 0.01)
	public float cooldown = 0.05f;

	private float last = -10;
	private boolean broken;

	public void onStart() {
		Fx.enableTouch(item);
	}

	public void onEnable() {
		if (item != null) Fx.enableTouch(item);
	}

	private void play(String name) {
		if (broken || name.length() == 0 || time() - last < cooldown) return;
		last = time();
		if (!Fx.playSound(stage, name)) broken = true;
	}

	public void onTouchStart(InputEvent e) {
		if (when == When.TOUCH_START) play(sound);
	}

	public void onClick() {
		if (when == When.CLICK) play(sound);
	}

	public void onTouchEnd(InputEvent e) {
		play(releaseSound);
	}

	@Block(label = "play button sound", desc = "Plays the button sound now.", labelAr = "شغّل صوت الزر", descAr = "يشغّل صوت الزر الآن.", category = "Button sound")
	public void playNow() {
		last = -10;
		broken = false;
		play(sound);
	}

	@Block(label = "set button sound", desc = "Changes the sound file (name of a file in the sounds folder).", labelAr = "اضبط صوت الزر", descAr = "يغيّر ملف الصوت (اسم ملف في مجلد الأصوات).", category = "Button sound", args = {"sound"})
	public void setSound(String name) {
		sound = name == null ? "" : name;
		broken = false;
	}
}
