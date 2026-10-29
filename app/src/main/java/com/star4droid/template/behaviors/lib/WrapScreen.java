package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Wrap around screen", icon = "repeat", category = "Movement", targets = BehaviorInfo.Target.BOTH,
		events = {"onWrap()"},
		desc = "When the element leaves one side of the screen it comes back from the opposite side (Asteroids, Pac-Man tunnels, endless runners). Speed is kept.")
public class WrapScreen extends Behavior {
	public enum Area { VIEW, SCENE, CUSTOM }

	@Param(desc = "VIEW: what the camera shows now (or the screen for UI). SCENE: the first screen (0,0 to the scene size). CUSTOM: the rectangle below.")
	public Area area = Area.VIEW;
	@Param(desc = "Wrap on the left/right edges.")
	public boolean wrapX = true;
	@Param(desc = "Wrap on the top/bottom edges.")
	public boolean wrapY = true;
	@Param(desc = "Extra space outside the area before wrapping.", unit = "px", min = -1000, max = 1000)
	public float margin = 0;
	@Param(desc = "CUSTOM area: left edge.", unit = "px", min = -100000, max = 100000)
	public float customLeft = 0;
	@Param(desc = "CUSTOM area: bottom edge.", unit = "px", min = -100000, max = 100000)
	public float customBottom = 0;
	@Param(desc = "CUSTOM area: right edge.", unit = "px", min = -100000, max = 100000)
	public float customRight = 720;
	@Param(desc = "CUSTOM area: top edge.", unit = "px", min = -100000, max = 100000)
	public float customTop = 1560;

	private final float[] r = new float[4];

	/** Fills out with left, bottom, right, top (px) of the chosen area (0 = view, 1 = scene, 2 = custom). */
	static void areaRect(Behavior bh, int area, float l, float bt, float rt, float tp, float[] out) {
		if (area == 2) {
			out[0] = l;
			out[1] = bt;
			out[2] = rt;
			out[3] = tp;
		} else if (bh.isUI() || area == 1) {
			out[0] = 0;
			out[1] = 0;
			out[2] = bh.stage.getWidth();
			out[3] = bh.stage.getHeight();
		} else {
			Camera cam = bh.stage.getCamera();
			float zoom = cam instanceof OrthographicCamera ? ((OrthographicCamera) cam).zoom : 1f;
			float hw = cam.viewportWidth * zoom * 0.5f / Behavior.W, hh = cam.viewportHeight * zoom * 0.5f / Behavior.W;
			float cx = cam.position.x / Behavior.W, cy = cam.position.y / Behavior.W;
			out[0] = cx - hw;
			out[1] = cy - hh;
			out[2] = cx + hw;
			out[3] = cy + hh;
		}
	}

	@Block(label = "set wrap margin", desc = "Extra space outside the area before wrapping (px).", labelAr = "اضبط هامش الالتفاف", descAr = "مساحة إضافية خارج المنطقة قبل الالتفاف (بكسل).", category = "Wrap around screen", args = {"margin"})
	public void setMargin(float value) {
		margin = value;
	}

	@Block(label = "wrap inside the scene", desc = "true = the first screen of the scene (0,0 to the scene size), false = what the camera shows.", labelAr = "التفاف داخل المشهد", descAr = "true = الشاشة الأولى للمشهد، false = ما تعرضه الكاميرا.", category = "Wrap around screen", args = {"scene"})
	public void setWrapInScene(boolean scene) {
		area = scene ? Area.SCENE : Area.VIEW;
	}

	@Block(label = "wrap horizontally", desc = "On: leaving left/right brings it back from the other side.", labelAr = "التفاف أفقي", descAr = "مفعّل: الخروج من اليمين/اليسار يعيده من الجهة الأخرى.", category = "Wrap around screen", args = {"on"})
	public void setWrapX(boolean on) {
		wrapX = on;
	}

	@Block(label = "wrap vertically", desc = "On: leaving the top/bottom brings it back from the other side.", labelAr = "التفاف رأسي", descAr = "مفعّل: الخروج من الأعلى/الأسفل يعيده من الجهة الأخرى.", category = "Wrap around screen", args = {"on"})
	public void setWrapY(boolean on) {
		wrapY = on;
	}

	public void onUpdate(float dt) {
		areaRect(this, area.ordinal(), customLeft, customBottom, customRight, customTop, r);
		float hw = widthPx() * 0.5f, hh = heightPx() * 0.5f;
		float minX = r[0] - margin, minY = r[1] - margin, maxX = r[2] + margin, maxY = r[3] + margin;
		float x = centerX(), y = centerY(), nx = x, ny = y;
		if (wrapX) {
			if (x - hw > maxX) nx = minX - hw;
			else if (x + hw < minX) nx = maxX + hw;
		}
		if (wrapY) {
			if (y - hh > maxY) ny = minY - hh;
			else if (y + hh < minY) ny = maxY + hh;
		}
		if (nx != x || ny != y) {
			setCenter(nx, ny);
			fire("onWrap");
		}
	}
}
