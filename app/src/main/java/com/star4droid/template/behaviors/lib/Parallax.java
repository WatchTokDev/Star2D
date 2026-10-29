package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Parallax", icon = "layers", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		desc = "A background layer that moves slower than the world when the camera moves, for depth. Works for screen backgrounds (UI) and world elements.")
public class Parallax extends Behavior {
	@Param(desc = "Sideways speed relative to the world: 0 = moves with the world (no effect), 0.5 = half speed (far), 1 = stays still on the screen (very far).", min = -1, max = 2, step = 0.05)
	public float factorX = 0.5f;
	@Param(desc = "Same, for up and down. Only used when \"Affect vertical\" is on.", min = -1, max = 2, step = 0.05)
	public float factorY = 0.5f;
	@Param(desc = "Also react when the camera moves up and down.")
	public boolean affectVertical = false;
	@Param(desc = "Repeat the movement every this many pixels sideways, so a row of identical tiles scrolls forever (0 = no repeat).", unit = "px", min = 0, max = 100000, step = 10)
	public float loopWidth = 0;
	@Param(desc = "Same, for up and down (0 = no repeat).", unit = "px", min = 0, max = 100000, step = 10)
	public float loopHeight = 0;

	private final Fx.Base base = new Fx.Base();
	private boolean ready;
	private float camX0, camY0;

	public void onStart() {
		base.capture(item);
		ready = false;
	}

	public void onEnable() {
		if (item != null) base.capture(item);
		ready = false;
	}

	public void onDisable() {
		base.restore(this);
	}

	public void onDestroy() {
		base.restore(this);
	}

	@Block(label = "reset parallax", desc = "Uses the camera's current place as the starting point.", labelAr = "أعد ضبط البارالاكس", descAr = "يعتمد مكان الكاميرا الحالي كنقطة بداية.", category = "Parallax")
	public void reset() {
		ready = false;
	}

	@Block(label = "set parallax speed", desc = "0 = moves with the world, 1 = stays on the screen.", labelAr = "اضبط سرعة البارالاكس", descAr = "0 = يتحرك مع العالم، 1 = يبقى ثابتاً على الشاشة.", category = "Parallax", args = {"factor"})
	public void setFactor(float value) {
		factorX = value;
		factorY = value;
	}

	private static float wrap(float v, float len) {
		if (len <= 0) return v;
		return v - (float) Math.floor(v / len + 0.5f) * len;
	}

	public void onUpdate(float dt) {
		float camX = stage.getCameraX() / W, camY = stage.getCameraY() / W;
		if (!ready) {
			ready = true;
			camX0 = camX;
			camY0 = camY;
		}
		float dx = camX - camX0, dy = affectVertical ? camY - camY0 : 0;
		// where the layer appears to drift on the SCREEN
		float sx = wrap(-(1 - factorX) * dx, loopWidth), sy = affectVertical ? wrap(-(1 - factorY) * dy, loopHeight) : 0;
		if (isUI()) base.offset(this, sx, sy);
		else base.offset(this, dx + sx, dy + sy);
	}
}
