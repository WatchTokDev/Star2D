package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Damageable;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Image;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Destructible", icon = "cargo-crate", category = "Combat", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onHit(PlayerItem by, int hitsLeft)", "onDestroyed(PlayerItem by)"},
		desc = "Crates, bricks, walls: breaks after a number of hits (bullets, DamageOnTouch, or a ball touching it), shows damaged pictures on the way and can drop an item.")
@Requires(body = true, note = "Needs a physical element. Touch hits come from contacts, so a Dynamic body (a ball, a player) must be involved.")
public class Destructible extends Behavior implements Damageable {
	public enum BreakAction { DESTROY, HIDE, NOTHING }

	@Header("Durability")
	@Param(desc = "How many hits it takes.", min = 1, max = 10000)
	public int hits = 3;
	@Param(desc = "Every hit counts as 1 whatever its damage. Off: the damage points are subtracted from the hits.")
	public boolean everyHitCountsOne = true;

	@Header("Hit by touching")
	@Param(desc = "Elements that hurt it by touching it (a ball for bricks).")
	public List<PlayerItem> hitters = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words, e.g. ball (separate with commas).")
	public String hitterPrefix = "";

	@Header("Look")
	@Image
	@Param(desc = "Picture after the first hits (optional). Up to three pictures are spread over the damage.")
	public String damagedImage1 = "";
	@Image
	@Param(desc = "Picture after more hits (optional).")
	public String damagedImage2 = "";
	@Image
	@Param(desc = "Picture just before it breaks (optional).")
	public String damagedImage3 = "";
	@Sound
	@Param(desc = "Played at every hit that does not break it.")
	public String hitSound = "";
	@Sound
	@Param(desc = "Played when it breaks.")
	public String breakSound = "";

	@Header("When it breaks")
	@Param(desc = "DESTROY removes it, HIDE keeps it (invisible, no collision) so you can bring it back with reset.")
	public BreakAction breakAction = BreakAction.DESTROY;
	@Param(desc = "Wait this long before it disappears (time for your own effect).", unit = "s", min = 0, max = 10, step = 0.05)
	public float breakDelay = 0;
	@Param(desc = "Element copied when it breaks (power-up, coin...). Put the original anywhere: it is hidden when the game starts. Optional.")
	public PlayerItem dropItem;
	@Param(desc = "Chance of the drop.", unit = "%", min = 0, max = 100, step = 5)
	public float dropChance = 100;

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private final GameplaySupport.Hider hider = new GameplaySupport.Hider();
	private GameplaySupport.Template drop;
	private float left = -1, startLeft;
	private boolean broken, acted;
	private float breakTimer;
	private int shownState = -1;
	private boolean resetPending;
	private PlayerItem breaker;
	private String origImage = "";

	public void onStart() {
		try {
			if (item.getProperties() instanceof com.star4droid.star2d.ElementDefs.BoxDef) origImage = ((com.star4droid.star2d.ElementDefs.BoxDef) item.getProperties()).image;
		} catch (RuntimeException ignored) {
		}
		left = hits;
		startLeft = hits;
		if (dropItem != null && Behavior.alive(dropItem) && dropItem != item) drop = new GameplaySupport.Template(dropItem, this);
	}

	@Block(label = "hit", desc = "Counts as one hit.", labelAr = "ضربة", descAr = "تُحسب كضربة واحدة.", category = "Destructible")
	public void hit() {
		takeDamage(1, null, 0, 0, 0);
	}

	@Block(label = "destroy now", desc = "Breaks it immediately.", labelAr = "حطّم الآن", descAr = "يحطمه فوراً.", category = "Destructible")
	public void breakNow() {
		if (left < 0) left = hits;
		left = 0;
		doBreak(null);
	}

	@Block(label = "reset", desc = "Brings it back whole (also if it was hidden when it broke).", labelAr = "أعد", descAr = "يعيده سليماً (حتى لو كان مخفياً بعد التحطم).", category = "Destructible")
	public void reset() {
		resetPending = true;
	}

	private void doReset() {
		resetPending = false;
		left = hits;
		startLeft = hits;
		broken = false;
		acted = false;
		breakTimer = 0;
		if (hider.isHidden()) hider.show(item);
		showState(-1);
	}

	@Block(label = "hits left", desc = "How many hits it still takes.", labelAr = "الضربات المتبقية", descAr = "عدد الضربات المتبقية للتحطيم.", category = "Destructible")
	public float getHitsLeft() {
		return left < 0 ? hits : left;
	}

	@Block(label = "set hits", desc = "Changes the hits it takes (and resets the damage).", labelAr = "اضبط الضربات", descAr = "يغيّر عدد الضربات اللازمة ويصفّر الضرر.", category = "Destructible", args = {"hits"})
	public void setHits(int value) {
		hits = Math.max(1, value);
		left = hits;
		startLeft = hits;
		showState(-1);
	}

	@Block(label = "is destroyed", desc = "True after it broke.", labelAr = "محطّم", descAr = "صحيح بعد أن تحطم.", category = "Destructible")
	public boolean isDead() {
		return broken;
	}

	public boolean takeDamage(float amount, PlayerItem source, float pushX, float pushY, float stunSeconds) {
		if (!enabled || broken || amount <= 0) return false;
		if (left < 0) {
			left = hits;
			startLeft = hits;
		}
		left -= everyHitCountsOne ? 1f : amount;
		if (left < 0) left = 0;
		fire("onHit", source, (int) Math.ceil(left));
		if (left <= 0) {
			doBreak(source);
		} else {
			GameplaySupport.sound(stage, hitSound);
			updateState();
		}
		return true;
	}

	private void doBreak(PlayerItem by) {
		if (broken) return;
		broken = true;
		breaker = by;
		GameplaySupport.sound(stage, breakSound);
		breakTimer = breakDelay; // finish() runs from onUpdate: the physics world is locked inside contact callbacks
	}

	private void finish() {
		if (acted) return;
		acted = true;
		fire("onDestroyed", breaker);
		if (dropItem != null && dropItem != item && (dropChance >= 100 || Math.random() * 100 < dropChance)) {
			if (drop != null) drop.spawn(centerX(), centerY());
		}
		if (breakAction == BreakAction.DESTROY) GameplaySupport.remove(item);
		else if (breakAction == BreakAction.HIDE) hider.hide(item);
	}

	private void updateState() {
		int n = (damagedImage1.length() > 0 ? 1 : 0) + (damagedImage2.length() > 0 ? 1 : 0) + (damagedImage3.length() > 0 ? 1 : 0);
		if (n == 0 || startLeft <= 0) return;
		float taken = (startLeft - left) / startLeft;
		int idx = (int) (taken * (n + 1) + 1e-3f) - 1;
		if (idx > n - 1) idx = n - 1;
		showState(idx);
	}

	private void showState(int idx) {
		if (idx == shownState) return;
		boolean back = idx < 0;
		shownState = idx;
		if (back) {
			if (origImage != null && origImage.length() > 0) stage.setImage(item, origImage);
			return;
		}
		String[] imgs = {damagedImage1, damagedImage2, damagedImage3};
		int k = -1;
		for (int i = 0; i < imgs.length; i++) {
			if (imgs[i].length() == 0) continue;
			k++;
			if (k == idx) {
				stage.setImage(item, imgs[i]);
				return;
			}
		}
	}

	public void onCollisionBegin(PlayerItem other) {
		if (other == null || broken) return;
		matcher.set(hitterPrefix);
		if (GameplaySupport.isTarget(other, hitters, matcher)) contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	public void onUpdate(float dt) {
		if (resetPending) doReset();
		if (broken) {
			if (!acted && breakTimer > 0) {
				breakTimer -= dt;
				if (breakTimer <= 0) finish();
			} else if (!acted) finish();
			return;
		}
		contacts.prune();
		contacts.poll(item, hitters);
		for (int i = 0; i < contacts.size() && !broken; i++)
			if (contacts.live(i) && contacts.takeFresh(i)) takeDamage(1, contacts.item(i), 0, 0, 0);
	}

	public void onDestroy() {
		contacts.clear();
	}
}
