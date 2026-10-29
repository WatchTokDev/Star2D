package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Long press", icon = "timer", category = "Input", targets = BehaviorInfo.Target.BOTH,
		events = {"onLongPress()", "onShortPress()"},
		desc = "Tells the difference between a quick tap and holding the element. Hold-to-confirm buttons, hidden menus, charge-up.")
public class LongPress extends Behavior {
	@Param(desc = "How long the finger must stay down.", unit = "s", min = 0.1, max = 10, step = 0.05)
	public float holdTime = 0.6f;
	@Param(desc = "If the finger moves farther than this (screen pixels) it is not a press any more.", unit = "px", min = 0, max = 500, step = 1)
	public float moveTolerance = 30;
	@Param(desc = "Keep firing onLongPress every hold time while the finger stays down.")
	public boolean repeat = false;
	@Param(desc = "A Progress element that fills while holding (optional): hold to confirm.")
	public ProgressItem holdBar;

	private boolean down, fired;
	private int pointer;
	private float held, sx, sy;

	public void onStart() {
		Fx.enableTouch(item);
	}

	public void onEnable() {
		if (item != null) Fx.enableTouch(item);
	}

	public void onDisable() {
		cancel();
	}

	private void cancel() {
		down = false;
		fired = false;
		held = 0;
		showBar(0);
	}

	private void showBar(float k) {
		if (holdBar != null && alive(holdBar)) holdBar.setProgress(k * holdBar.getMaxF());
	}

	public void onTouchStart(InputEvent e) {
		down = true;
		fired = false;
		held = 0;
		pointer = e.getPointer();
		sx = Gdx.input.getX(pointer);
		sy = Gdx.input.getY(pointer);
	}

	public void onTouchEnd(InputEvent e) {
		boolean wasDown = down, wasFired = fired;
		cancel();
		if (wasDown && !wasFired) fire("onShortPress");
	}

	@Block(label = "set hold time", desc = "How long the finger must stay down (seconds).", labelAr = "اضبط مدة الضغط المطوّل", descAr = "المدة التي يجب أن يبقى فيها الإصبع (ثانية).", category = "Long press", args = {"seconds"})
	public void setHoldTime(float value) {
		holdTime = value;
	}

	@Block(label = "held time", desc = "How long the element has been held (seconds), 0 when not touched.", labelAr = "مدة الضغط", descAr = "المدة التي ضُغط فيها العنصر (ثانية)، 0 إن لم يُلمس.", category = "Long press")
	public float getHeld() {
		return down ? held : 0;
	}

	@Block(label = "is held", desc = "True while a finger is holding the element.", labelAr = "مضغوط الآن", descAr = "صحيح أثناء ضغط العنصر بإصبع.", category = "Long press")
	public boolean isHeld() {
		return down;
	}

	public void onUpdate(float dt) {
		if (!down) return;
		float dx = Gdx.input.getX(pointer) - sx, dy = Gdx.input.getY(pointer) - sy;
		if (dx * dx + dy * dy > moveTolerance * moveTolerance) {
			cancel();
			return;
		}
		held += dt;
		showBar(clamp(held / holdTime, 0, 1));
		if (!fired && held >= holdTime) {
			fired = true;
			fire("onLongPress");
			if (repeat) {
				held = 0;
				fired = false;
			}
		}
	}
}
