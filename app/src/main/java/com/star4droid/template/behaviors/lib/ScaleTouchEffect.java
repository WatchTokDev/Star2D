package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Scale touch effect", icon = "resize", category = "UI effects", targets = BehaviorInfo.Target.BOTH,
		desc = "The element shrinks (or grows) while it is touched and springs back when released.")
public class ScaleTouchEffect extends Behavior {
	@Param(desc = "Scale while touched, 1 = normal size.", min = 0.1, max = 3, step = 0.05)
	public float pressedScale = 0.9f;
	@Param(desc = "How fast it moves to the new size.", min = 1, max = 60, unit = "1/s")
	public float speed = 18;
	@Param(desc = "Also fade a little while touched (1 = no fade).", min = 0, max = 1, step = 0.05)
	public float pressedAlpha = 1f;

	private float baseX = 1, baseY = 1, baseAlpha = 1, factor = 1, alpha = 1;
	private boolean down;

	private void capture() {
		Actor a = actor();
		baseX = a.getScaleX();
		baseY = a.getScaleY();
		baseAlpha = a.getColor().a;
		factor = 1;
		alpha = 1;
	}

	public void onStart() {
		capture();
	}

	public void onEnable() {
		capture();
	}

	/** Back to the saved look (the element's own properties are never changed). */
	public void onDisable() {
		down = false;
		Actor a = actor();
		a.setScale(baseX, baseY);
		Color c = a.getColor();
		a.setColor(c.r, c.g, c.b, baseAlpha);
	}

	@Block(label = "set pressed scale", desc = "Size while touched (1 = normal).", labelAr = "اضبط حجم الضغط", descAr = "الحجم أثناء اللمس (1 = عادي).", category = "Touch effect", args = {"scale"})
	public void setPressedScale(float value) {
		pressedScale = value;
	}

	@Block(label = "set pressed opacity", desc = "Opacity while touched (1 = no fade, 0 = invisible).", labelAr = "اضبط شفافية الضغط", descAr = "الشفافية أثناء اللمس (1 = بدون تلاشي، 0 = مخفي).", category = "Touch effect", args = {"opacity"})
	public void setPressedAlpha(float value) {
		pressedAlpha = value;
	}

	@Block(label = "set touch effect speed", desc = "How fast the size changes.", labelAr = "اضبط سرعة تأثير اللمس", descAr = "سرعة تغيّر الحجم.", category = "Touch effect", args = {"speed"})
	public void setEffectSpeed(float value) {
		speed = value;
	}

	public void onTouchStart(InputEvent e) {
		down = true;
	}

	public void onTouchEnd(InputEvent e) {
		down = false;
	}

	public void onUpdate(float dt) {
		float k = clamp(speed * dt, 0, 1);
		factor += ((down ? pressedScale : 1f) - factor) * k;
		alpha += ((down ? pressedAlpha : 1f) - alpha) * k;
		Actor a = actor();
		a.setScale(baseX * factor, baseY * factor);
		Color c = a.getColor();
		a.setColor(c.r, c.g, c.b, baseAlpha * alpha);
	}
}
