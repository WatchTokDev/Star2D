package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Camera;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Camera Shake", icon = "vibrate", category = "Camera", targets = BehaviorInfo.Target.BOTH,
		events = {"onShakeEnd()"},
		desc = "Screen shake for hits, explosions and landings. Call shake with a strength from 0 to 1; strong hits shake much more than weak ones and fade out by themselves. Health can trigger it too (Shake on hit). Works with or without Camera Follow.")
public class CameraShake extends Behavior {
	@Header("Shake")
	@Param(desc = "Largest displacement of the camera at full strength.", unit = "px", min = 1, max = 500)
	public float maxOffset = 24;
	@Param(desc = "How fast it trembles.", unit = "Hz", min = 1, max = 80)
	public float frequency = 25;
	@Param(desc = "How fast the strength fades, per second (1 = a full shake lasts one second).", min = 0.1, max = 10, step = 0.1)
	public float decay = 1.2f;
	@Param(desc = "2 makes weak hits very gentle and strong hits violent; 1 is linear.", min = 1, max = 4, step = 0.5)
	public float power = 2;
	@Param(desc = "Shake sideways.")
	public boolean shakeX = true;
	@Param(desc = "Shake up and down.")
	public boolean shakeY = true;
	@Param(desc = "Shake once when the scene starts (0 = no).", min = 0, max = 1, step = 0.05)
	public float shakeOnStart = 0;

	private GameplaySupport.CameraRig rig;
	private float t, holdLevel, holdLeft;
	private boolean active;

	public void onStart() {
		rig = GameplaySupport.rig(stage);
		rig.driven = true;
		if (shakeOnStart > 0) rig.trauma = Math.min(1f, rig.trauma + shakeOnStart);
	}

	@Block(label = "shake camera", desc = "Adds a shake: 0.2 = a bump, 0.5 = a hit, 1 = an explosion.", labelAr = "اهزز الكاميرا", descAr = "يضيف هزة: 0.2 = نقرة، 0.5 = ضربة، 1 = انفجار.", category = "Camera Shake", args = {"strength"})
	public void shake(float strength) {
		if (rig == null) rig = GameplaySupport.rig(stage);
		rig.trauma = Math.min(1f, rig.trauma + Math.max(0f, strength));
	}

	@Block(label = "shake camera for", desc = "Keeps shaking at this strength for some seconds, then fades (earthquakes, engines).", labelAr = "اهزز الكاميرا لمدة", descAr = "يستمر بالهز بهذه القوة عدة ثوان ثم يخفت (زلازل، محركات).", category = "Camera Shake", args = {"strength", "seconds"})
	public void shakeFor(float strength, float seconds) {
		holdLevel = clamp(strength, 0f, 1f);
		holdLeft = Math.max(0f, seconds);
	}

	@Block(label = "stop camera shake", desc = "Ends the shake immediately.", labelAr = "أوقف هزة الكاميرا", descAr = "ينهي الهزة فوراً.", category = "Camera Shake")
	public void stopShake() {
		holdLeft = 0;
		if (rig != null) rig.trauma = 0;
	}

	@Block(label = "camera is shaking", desc = "True while the screen shakes.", labelAr = "الكاميرا تهتز", descAr = "صحيح طالما الشاشة تهتز.", category = "Camera Shake")
	public boolean isShaking() {
		return rig != null && rig.trauma > 0;
	}

	public void onUpdate(float dt) {
		if (rig == null) rig = GameplaySupport.rig(stage);
		if (holdLeft > 0) {
			holdLeft -= dt;
			if (rig.trauma < holdLevel) rig.trauma = holdLevel;
		}
		float tr = rig.trauma;
		if (tr <= 0 && rig.offX == 0 && rig.offY == 0) return;
		Camera cam = stage.getCamera();
		float bx = rig.baseX(cam), by = rig.baseY(cam), ox = 0, oy = 0;
		if (tr > 0) {
			float amp = (float) Math.pow(tr, power) * maxOffset * W;
			t += dt * frequency;
			if (shakeX) ox = GameplaySupport.noise(t, 1f) * amp;
			if (shakeY) oy = GameplaySupport.noise(t, 2f) * amp;
			rig.trauma = Math.max(0f, tr - decay * dt);
			active = true;
		} else if (active) {
			active = false;
			fire("onShakeEnd");
		}
		rig.write(cam, bx, by, ox, oy);
	}

	public void onDisable() {
		if (rig == null) return;
		Camera cam = stage.getCamera();
		rig.write(cam, rig.baseX(cam), rig.baseY(cam), 0, 0);
		rig.driven = false;
	}

	public void onEnable() {
		if (rig != null) rig.driven = true;
	}

	public void onDestroy() {
		onDisable();
	}
}
