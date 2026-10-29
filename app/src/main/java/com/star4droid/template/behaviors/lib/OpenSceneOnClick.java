package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Fx;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;

@BehaviorInfo(name = "Open scene on click", icon = "door-open", category = "Input", targets = BehaviorInfo.Target.BOTH,
		events = {"onOpening()"},
		desc = "Touching the element opens another scene, after an optional wait and fade. For Play buttons and menus.")
public class OpenSceneOnClick extends Behavior {
	public enum Trigger { CLICK, TOUCH_START }

	@Param(desc = "Name of the scene to open (as written in the scenes list).")
	public String scene = "";
	@Param(desc = "CLICK = when it is pressed and released on the element, TOUCH_START = as soon as it is pressed.")
	public Trigger trigger = Trigger.CLICK;
	@Param(desc = "Wait this long before the scene changes (time for a sound or an effect).", unit = "s", min = 0, max = 30, step = 0.05)
	public float delay = 0f;
	@Param(desc = "A big black box over the whole screen (optional): it fades in before the scene changes. Make it invisible or 0% opaque in the editor.")
	public PlayerItem fadeOverlay;
	@Param(desc = "How long the overlay takes to fade in.", unit = "s", min = 0.05, max = 5, step = 0.05)
	public float fadeTime = 0.4f;
	@Param(desc = "Sound played when the touch is accepted (optional).")
	@Sound
	public String clickSound = "";

	private boolean opening;
	private float t;
	private boolean overlayShown, overlayWasVisible;
	private float overlayAlpha;

	public void onStart() {
		Fx.enableTouch(item);
	}

	public void onEnable() {
		if (item != null) Fx.enableTouch(item);
	}

	public void onDisable() {
		cancel();
	}

	public void onTouchStart(InputEvent e) {
		if (trigger == Trigger.TOUCH_START) openNow();
	}

	public void onClick() {
		if (trigger == Trigger.CLICK) openNow();
	}

	@Block(label = "open scene now", desc = "Starts the scene change (with the delay and fade of this behavior).", labelAr = "افتح المشهد الآن", descAr = "يبدأ تغيير المشهد (مع التأخير والتلاشي).", category = "Open scene")
	public void openNow() {
		if (opening || scene.length() == 0) return;
		opening = true;
		t = 0;
		if (clickSound.length() > 0) Fx.playSound(stage, clickSound);
		fire("onOpening");
		if (delay <= 0 && fadeOverlay == null) go();
	}

	@Block(label = "set scene to open", desc = "Chooses which scene this element opens.", labelAr = "اضبط المشهد المراد فتحه", descAr = "يحدد المشهد الذي يفتحه هذا العنصر.", category = "Open scene", args = {"scene"})
	public void setScene(String name) {
		scene = name == null ? "" : name;
	}

	@Block(label = "cancel opening", desc = "Cancels a scene change that is waiting.", labelAr = "ألغِ الفتح", descAr = "يلغي تغيير المشهد المنتظر.", category = "Open scene")
	public void cancel() {
		opening = false;
		if (overlayShown && alive(fadeOverlay)) {
			Actor a = fadeOverlay.getActor();
			a.getColor().a = overlayAlpha;
			a.setVisible(overlayWasVisible);
		}
		overlayShown = false;
	}

	private void go() {
		opening = false;
		stage.openScene(scene);
	}

	public void onUpdate(float dt) {
		if (!opening) return;
		t += dt;
		if (alive(fadeOverlay)) {
			Actor a = fadeOverlay.getActor();
			if (!overlayShown) {
				overlayShown = true;
				overlayWasVisible = a.isVisible();
				overlayAlpha = a.getColor().a;
				a.setVisible(true);
				a.toFront();
			}
			float k = clamp((t - delay) / Math.max(0.01f, fadeTime), 0, 1);
			a.getColor().a = overlayAlpha + (1 - overlayAlpha) * k;
			if (k >= 1f) go();
		} else if (t >= delay) {
			go();
		}
	}
}
