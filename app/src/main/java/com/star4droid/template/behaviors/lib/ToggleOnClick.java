package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Toggle on click", icon = "toggle-switch", category = "Input", targets = BehaviorInfo.Target.BOTH,
		events = {"onToggled(boolean visible)"},
		desc = "Touching the element shows or hides other elements: a settings panel, a menu, a hint.")
public class ToggleOnClick extends Behavior {
	public enum Trigger { CLICK, TOUCH_START }

	public enum Action { TOGGLE, SHOW, HIDE }

	@Param(desc = "The elements that are shown / hidden.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "TOGGLE = flip each time, SHOW = always show, HIDE = always hide.")
	public Action action = Action.TOGGLE;
	@Param(desc = "CLICK = when it is pressed and released on the element, TOUCH_START = as soon as it is pressed.")
	public Trigger trigger = Trigger.CLICK;
	@Param(desc = "Hide the targets when the scene starts.")
	public boolean startHidden = false;
	@Param(desc = "Hidden physical elements also stop colliding and moving.")
	public boolean affectPhysics = true;

	private boolean visible = true;

	public void onStart() {
		Fx.enableTouch(item);
		visible = !startHidden;
		if (startHidden) apply(false);
	}

	public void onEnable() {
		if (item != null) Fx.enableTouch(item);
	}

	public void onTouchStart(InputEvent e) {
		if (trigger == Trigger.TOUCH_START) press();
	}

	public void onClick() {
		if (trigger == Trigger.CLICK) press();
	}

	private void press() {
		if (action == Action.SHOW) setShown(true);
		else if (action == Action.HIDE) setShown(false);
		else setShown(!visible);
	}

	private void apply(boolean show) {
		for (int i = 0; i < targets.size(); i++) {
			PlayerItem it = targets.get(i);
			if (!alive(it)) continue;
			it.getActor().setVisible(show);
			if (affectPhysics) {
				Body b = it.getBody();
				if (b != null) b.setActive(show);
			}
		}
	}

	@Block(label = "toggle targets", desc = "Shows the targets if hidden, hides them if shown.", labelAr = "بدّل ظهور العناصر", descAr = "يُظهر العناصر المخفية ويُخفي الظاهرة.", category = "Toggle on click")
	public void toggle() {
		setShown(!visible);
	}

	@Block(label = "show or hide targets", desc = "true = show the targets, false = hide them.", labelAr = "أظهر أو أخفِ العناصر", descAr = "صحيح = إظهار العناصر، خطأ = إخفاؤها.", category = "Toggle on click", args = {"show"})
	public void setShown(boolean show) {
		visible = show;
		apply(show);
		fire("onToggled", show);
	}

	@Block(label = "targets are shown", desc = "True while the targets are shown.", labelAr = "العناصر ظاهرة", descAr = "صحيح عندما تكون العناصر ظاهرة.", category = "Toggle on click")
	public boolean isShown() {
		return visible;
	}
}
