package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Limit to screen", icon = "selection", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onHitEdge(String edge)"},
		desc = "Keeps the element inside the screen (or the camera view, the scene, or a rectangle) with an optional margin, so the player or a paddle can never leave. A physical element loses (or bounces off) the speed that pushes it outside.")
public class LimitToScreen extends Behavior {
	public enum Area { VIEW, SCENE, CUSTOM }

	@Param(desc = "VIEW: what the camera shows now (or the screen for UI). SCENE: the first screen (0,0 to the scene size). CUSTOM: the rectangle below.")
	public Area area = Area.VIEW;
	@Param(desc = "Limit left/right.")
	public boolean limitX = true;
	@Param(desc = "Limit up/down.")
	public boolean limitY = true;
	@Param(desc = "Space kept free at the edges.", unit = "px", min = -1000, max = 1000)
	public float margin = 0;
	@Param(desc = "The whole element must stay inside (off: only its center).")
	public boolean keepFullyInside = true;
	@Param(desc = "Physical: 0 = stops at the edge, 1 = bounces back with the same speed.", min = 0, max = 1, step = 0.05)
	public float bounciness = 0;
	@Param(desc = "CUSTOM area: left edge.", unit = "px", min = -100000, max = 100000)
	public float customLeft = 0;
	@Param(desc = "CUSTOM area: bottom edge.", unit = "px", min = -100000, max = 100000)
	public float customBottom = 0;
	@Param(desc = "CUSTOM area: right edge.", unit = "px", min = -100000, max = 100000)
	public float customRight = 720;
	@Param(desc = "CUSTOM area: top edge.", unit = "px", min = -100000, max = 100000)
	public float customTop = 1560;

	private final float[] r = new float[4];
	private boolean hitL, hitR, hitT, hitB;

	@Block(label = "limit horizontally", desc = "On: it cannot leave through the left or right edge.", labelAr = "تقييد أفقي", descAr = "مفعّل: لا يمكنه الخروج من الحافة اليمنى أو اليسرى.", category = "Limit to screen", args = {"on"})
	public void setLimitX(boolean on) {
		limitX = on;
	}

	@Block(label = "limit vertically", desc = "On: it cannot leave through the top or bottom edge.", labelAr = "تقييد رأسي", descAr = "مفعّل: لا يمكنه الخروج من الحافة العليا أو السفلى.", category = "Limit to screen", args = {"on"})
	public void setLimitY(boolean on) {
		limitY = on;
	}

	@Block(label = "set edge bounciness", desc = "0 = stops at the edge, 1 = bounces back (physical elements).", labelAr = "اضبط ارتداد الحافة", descAr = "0 = يتوقف عند الحافة، 1 = يرتد (للعناصر الفيزيائية).", category = "Limit to screen", args = {"bounciness"})
	public void setBounciness(float value) {
		bounciness = clamp(value, 0, 1);
	}

	@Block(label = "set screen margin", desc = "Space kept free at the edges (px).", labelAr = "اضبط هامش الشاشة", descAr = "المساحة المتروكة عند الحواف (بكسل).", category = "Limit to screen", args = {"margin"})
	public void setMargin(float value) {
		margin = value;
	}

	@Block(label = "set limit area", desc = "Uses your own rectangle (px): left, bottom, right, top.", labelAr = "اضبط منطقة الحد", descAr = "يستخدم مستطيلك الخاص (بكسل): يسار، أسفل، يمين، أعلى.", category = "Limit to screen", args = {"left", "bottom", "right", "top"})
	public void setCustomArea(float left, float bottom, float right, float top) {
		customLeft = left;
		customBottom = bottom;
		customRight = right;
		customTop = top;
		area = Area.CUSTOM;
	}

	public void onUpdate(float dt) {
		WrapScreen.areaRect(this, area.ordinal(), customLeft, customBottom, customRight, customTop, r);
		float hw = keepFullyInside ? widthPx() * 0.5f : 0f, hh = keepFullyInside ? heightPx() * 0.5f : 0f;
		float minX = r[0] + margin + hw, maxX = r[2] - margin - hw, minY = r[1] + margin + hh, maxY = r[3] - margin - hh;
		if (minX > maxX) minX = maxX = (minX + maxX) * 0.5f;
		if (minY > maxY) minY = maxY = (minY + maxY) * 0.5f;
		float x = centerX(), y = centerY(), nx = x, ny = y;
		boolean l = false, rt = false, t = false, bt = false;
		if (limitX) {
			if (x < minX) {
				nx = minX;
				l = true;
			} else if (x > maxX) {
				nx = maxX;
				rt = true;
			}
		}
		if (limitY) {
			if (y < minY) {
				ny = minY;
				bt = true;
			} else if (y > maxY) {
				ny = maxY;
				t = true;
			}
		}
		if (nx != x || ny != y) {
			setCenter(nx, ny);
			Body b = body();
			if (b != null) {
				float vx = b.getLinearVelocity().x, vy = b.getLinearVelocity().y;
				if ((l && vx < 0) || (rt && vx > 0)) vx = -vx * bounciness;
				if ((bt && vy < 0) || (t && vy > 0)) vy = -vy * bounciness;
				b.setLinearVelocity(vx, vy);
			}
		}
		if (l && !hitL) fire("onHitEdge", "left");
		if (rt && !hitR) fire("onHitEdge", "right");
		if (t && !hitT) fire("onHitEdge", "top");
		if (bt && !hitB) fire("onHitEdge", "bottom");
		hitL = l;
		hitR = rt;
		hitT = t;
		hitB = bt;
	}
}
