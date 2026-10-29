package com.star4droid.template.behaviors.lib;

import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Param;

@BehaviorInfo(name = "Lifetime", icon = "timer-sand", category = "Logic", targets = BehaviorInfo.Target.BOTH,
		events = {"onExpire()"},
		desc = "The element removes itself after a while, fading out (and optionally shrinking) first. Bullets, particles, temporary platforms, hints.")
public class Lifetime extends Behavior {
	public enum EndAction { DESTROY, HIDE }

	@Param(desc = "How long it lives, counted from when the scene starts (or from reset).", unit = "s", min = 0.05, max = 3600, step = 0.1)
	public float seconds = 3;
	@Param(desc = "Random extra life between -x and +x seconds, so a crowd does not vanish all at once.", unit = "s", min = 0, max = 60, step = 0.1)
	public float randomExtra = 0;
	@Param(desc = "Fades out during the last seconds.", unit = "s", min = 0, max = 60, step = 0.1)
	public float fadeTime = 0.3f;
	@Param(desc = "Also shrinks to nothing while it fades.")
	public boolean shrink = false;
	@Param(desc = "DESTROY removes the element, HIDE keeps it invisible and inactive.")
	public EndAction endAction = EndAction.DESTROY;

	private float total, left, baseAlpha = 1, baseSx = 1, baseSy = 1;
	private boolean done, changed;
	private final GameplaySupport.Hider hider = new GameplaySupport.Hider();

	public void onStart() {
		baseAlpha = item.getActor().getColor().a;
		baseSx = item.getActor().getScaleX();
		baseSy = item.getActor().getScaleY();
		restart();
	}

	private void restart() {
		total = Math.max(0.05f, seconds + (randomExtra > 0 ? (float) (Math.random() * 2 - 1) * randomExtra : 0f));
		left = total;
		done = false;
	}

	@Block(label = "restart lifetime", desc = "Starts counting its life again from the full time.", labelAr = "أعد العد", descAr = "يبدأ العد التنازلي لعمر العنصر من جديد.", category = "Lifetime")
	public void reset() {
		if (changed) restoreLook();
		if (hider.isHidden()) hider.show(item);
		restart();
	}

	@Block(label = "add life", desc = "Adds (or with a negative number removes) seconds of life.", labelAr = "أضف عمراً", descAr = "يضيف ثواني إلى عمر العنصر (أو ينقصها بقيمة سالبة).", category = "Lifetime", args = {"seconds"})
	public void extend(float extraSeconds) {
		left += extraSeconds;
		if (left > total) total = left;
	}

	@Block(label = "set life left", desc = "Sets the remaining seconds of life.", labelAr = "اضبط العمر المتبقي", descAr = "يضبط الثواني المتبقية من عمر العنصر.", category = "Lifetime", args = {"seconds"})
	public void setTimeLeft(float value) {
		left = value;
		if (left > total) total = left;
	}

	@Block(label = "life left", desc = "Seconds of life that remain.", labelAr = "العمر المتبقي", descAr = "الثواني المتبقية من العمر.", category = "Lifetime")
	public float getTimeLeft() {
		return Math.max(0f, left);
	}

	private void restoreLook() {
		item.getActor().getColor().a = baseAlpha;
		item.getActor().setScale(baseSx, baseSy);
		changed = false;
	}

	public void onUpdate(float dt) {
		if (done) return;
		left -= dt;
		if (fadeTime > 0 && left < fadeTime && left > 0) {
			float k = left / fadeTime;
			item.getActor().getColor().a = baseAlpha * k;
			if (shrink) item.getActor().setScale(baseSx * k, baseSy * k);
			changed = true;
		}
		if (left <= 0) {
			done = true;
			fire("onExpire");
			if (endAction == EndAction.DESTROY) {
				GameplaySupport.remove(item);
			} else {
				if (changed) restoreLook();
				hider.hide(item);
			}
		}
	}

	public void onDisable() {
		if (changed) restoreLook();
	}
}
