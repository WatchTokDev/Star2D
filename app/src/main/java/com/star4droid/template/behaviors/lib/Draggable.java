package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Easing;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Draggable", icon = "cursor-move", category = "Input", targets = BehaviorInfo.Target.BOTH,
		events = {"onDragStart()", "onDrag()", "onDrop()", "onDropOn(PlayerItem target)"},
		desc = "The player can drag the element with a finger. Keep it inside an area, snap it to a grid, send it back home or drop it on targets (puzzles, inventories, sliders).")
public class Draggable extends Behavior {
	@Param(desc = "Do not move sideways.")
	public boolean lockX = false;
	@Param(desc = "Do not move up and down.")
	public boolean lockY = false;
	@Param(desc = "Keep the element inside the box of this other element (optional).")
	public PlayerItem area;
	@Param(desc = "When released, snap to a grid of this size (0 = no grid).", unit = "px", min = 0, max = 1000, step = 1)
	public float gridSize = 0;
	@Param(desc = "Go back to the starting place when released.")
	public boolean snapBack = false;
	@Param(desc = "How long the trip back takes (0 = at once).", unit = "s", min = 0, max = 5, step = 0.05)
	public float snapBackTime = 0.25f;
	@Param(desc = "Elements it can be dropped on (optional). onDropOn runs when it is released over one.")
	public List<PlayerItem> dropTargets = new ArrayList<PlayerItem>();
	@Param(desc = "Move to the center of the target it was dropped on.")
	public boolean snapToTarget = true;
	@Param(desc = "Size while dragging (1 = normal), so it looks lifted.", min = 0.5, max = 2, step = 0.05)
	public float dragScale = 1.1f;
	@Param(desc = "Bring the element in front of the others while dragging.")
	public boolean bringToFront = true;
	@Param(desc = "A physical element stops moving by itself while it is held.")
	public boolean freezePhysics = true;

	private final Fx.Base base = new Fx.Base();
	private InputListener listener;
	private boolean dragging, returning;
	private int pointer = -1;
	private float grabX, grabY, fingerX, fingerY; // finger -> center offset, last finger position (px)
	private float homeX, homeY, fromX, fromY, t, scaleNow = 1;

	private float unit() {
		return isUI() ? 1f : 1f / W;
	}

	public void onStart() {
		base.capture(item);
		base.centerOrigin(item);
		homeX = centerX();
		homeY = centerY();
		Fx.enableTouch(item);
		listener = new InputListener() {
			@Override
			public boolean touchDown(InputEvent event, float x, float y, int p, int button) {
				if (!isEnabled() || dragging || button != 0) return false;
				float k = unit();
				fingerX = event.getStageX() * k;
				fingerY = event.getStageY() * k;
				grabX = centerX() - fingerX;
				grabY = centerY() - fingerY;
				dragging = true;
				returning = false;
				pointer = p;
				if (bringToFront) item.getActor().toFront();
				fire("onDragStart");
				return true;
			}

			@Override
			public void touchDragged(InputEvent event, float x, float y, int p) {
				if (!dragging || p != pointer) return;
				fingerX = event.getStageX() * unit();
				fingerY = event.getStageY() * unit();
			}

			@Override
			public void touchUp(InputEvent event, float x, float y, int p, int button) {
				if (dragging && p == pointer) drop();
			}
		};
		item.getActor().addListener(listener);
	}

	public void onEnable() {
		if (item != null) {
			Fx.enableTouch(item);
			homeX = centerX();
			homeY = centerY();
		}
	}

	public void onDisable() {
		dragging = false;
		returning = false;
		pointer = -1;
		base.scale(item, 1, 1);
	}

	public void onDestroy() {
		if (listener != null && item != null && item.getActor() != null) item.getActor().removeListener(listener);
		listener = null;
	}

	@Block(label = "go back to start place", desc = "Moves the element back to where it started (or where it was last set as home).", labelAr = "ارجع إلى المكان الأصلي", descAr = "يعيد العنصر إلى مكانه الأصلي (أو آخر مكان تم تحديده).", category = "Draggable")
	public void returnHome() {
		dragging = false;
		beginReturn();
	}

	@Block(label = "set this place as start place", desc = "The element goes back here when snap back is on.", labelAr = "اجعل هذا المكان هو الأصلي", descAr = "يعود العنصر إلى هنا عند تفعيل العودة.", category = "Draggable")
	public void setHomeHere() {
		homeX = centerX();
		homeY = centerY();
	}

	@Block(label = "is being dragged", desc = "True while a finger holds the element.", labelAr = "يتم سحبه الآن", descAr = "صحيح أثناء سحب العنصر بالإصبع.", category = "Draggable")
	public boolean isDragging() {
		return dragging;
	}

	private void beginReturn() {
		fromX = centerX();
		fromY = centerY();
		t = 0;
		returning = true;
		if (snapBackTime <= 0) {
			returning = false;
			setCenter(homeX, homeY);
		}
	}

	private void drop() {
		dragging = false;
		pointer = -1;
		PlayerItem hit = null;
		for (int i = 0; i < dropTargets.size(); i++) {
			PlayerItem target = dropTargets.get(i);
			if (alive(target) && target != item && overlaps(item, target)) {
				hit = target;
				break;
			}
		}
		if (hit != null) {
			fire("onDropOn", hit);
			if (snapToTarget) {
				setCenter(cx(hit), cy(hit));
				homeX = centerX();
				homeY = centerY();
			}
		} else if (snapBack) {
			beginReturn();
		} else if (gridSize > 0) {
			setCenter(Math.round(centerX() / gridSize) * gridSize, Math.round(centerY() / gridSize) * gridSize);
		}
		fire("onDrop");
	}

	public void onUpdate(float dt) {
		float want = dragging ? dragScale : 1f;
		if (scaleNow != want) {
			scaleNow += (want - scaleNow) * clamp(dt * 20f, 0, 1);
			if (Math.abs(want - scaleNow) < 0.002f) scaleNow = want;
			base.scale(item, scaleNow, scaleNow);
		}
		if (dragging) {
			float x = lockX ? centerX() : fingerX + grabX;
			float y = lockY ? centerY() : fingerY + grabY;
			if (area != null && alive(area)) {
				float hw = Math.max(0, (wPx(area) - widthPx()) * 0.5f), hh = Math.max(0, (hPx(area) - heightPx()) * 0.5f);
				x = clamp(x, cx(area) - hw, cx(area) + hw);
				y = clamp(y, cy(area) - hh, cy(area) + hh);
			}
			setCenter(x, y);
			if (freezePhysics) setVelocity(0, 0);
			fire("onDrag");
		} else if (returning) {
			t += dt;
			float u = clamp(t / snapBackTime, 0, 1);
			float e = Easing.apply(Easing.EASE_OUT, u);
			setCenter(fromX + (homeX - fromX) * e, fromY + (homeY - fromY) * e);
			if (u >= 1f) returning = false;
		}
	}
}
