package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sets;

@BehaviorInfo(name = "Floating", icon = "wave", category = "Motion", targets = BehaviorInfo.Target.BOTH,
		desc = "The element bobs up and down (and/or sideways) forever. Physical elements become kinematic while it runs.")
@Sets({"type=KINEMATIC"})
public class Floating extends Behavior {
	@Param(desc = "Vertical distance each way.", unit = "px", min = 0, max = 2000)
	public float amplitudeY = 12;
	@Param(desc = "Horizontal distance each way.", unit = "px", min = 0, max = 2000)
	public float amplitudeX = 0;
	@Param(desc = "Full cycles per second.", unit = "Hz", min = 0, max = 20, step = 0.1)
	public float frequency = 0.5f;
	@Param(desc = "Start offset in the cycle (0..1) so several elements do not move together.", min = 0, max = 1, step = 0.05)
	public float phase = 0;

	private float ox, oy; // offset currently applied on top of the element's own position

	@Block(label = "set float height", desc = "Vertical distance each way (px).", labelAr = "اضبط ارتفاع الطفو", descAr = "المسافة العمودية في كل اتجاه (بكسل).", category = "Floating", args = {"height"})
	public void setAmplitude(float value) {
		amplitudeY = value;
	}

	@Block(label = "set float width", desc = "Horizontal distance each way (px).", labelAr = "اضبط عرض الطفو", descAr = "المسافة الأفقية في كل اتجاه (بكسل).", category = "Floating", args = {"width"})
	public void setAmplitudeX(float value) {
		amplitudeX = value;
	}

	@Block(label = "set float speed", desc = "Full cycles per second.", labelAr = "اضبط سرعة الطفو", descAr = "عدد الدورات الكاملة في الثانية.", category = "Floating", args = {"cycles"})
	public void setFrequency(float value) {
		frequency = value;
	}

	@Block(label = "float height", desc = "Current vertical distance each way (px).", labelAr = "ارتفاع الطفو", descAr = "المسافة العمودية الحالية في كل اتجاه (بكسل).", category = "Floating")
	public float getAmplitude() {
		return amplitudeY;
	}

	public void onEnable() {
		ox = 0;
		oy = 0;
	}

	/** Moves the element back by the offset it added: the saved position stays the truth. */
	public void onDisable() {
		setCenter(centerX() - ox, centerY() - oy);
		ox = 0;
		oy = 0;
	}

	public void onUpdate(float dt) {
		double a = (time() * frequency + phase) * Math.PI * 2;
		float nx = (float) Math.sin(a) * amplitudeX, ny = (float) Math.sin(a) * amplitudeY;
		setCenter(centerX() + nx - ox, centerY() + ny - oy);
		ox = nx;
		oy = ny;
	}
}
