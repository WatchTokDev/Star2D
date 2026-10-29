package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Damageable;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Collectible", icon = "coin", category = "Gameplay", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onCollected(PlayerItem collector, float value)", "onRespawned()"},
		desc = "Coins, gems, keys, hearts: when the player touches it, it adds a value to a scene variable (score, coins, keys), can heal, plays a sound and disappears or comes back later.")
@Requires(body = true, note = "Needs a physical element; best as Sensor so the player walks through it.")
public class Collectible extends Behavior {
	public enum AfterCollect { DESTROY, HIDE, NOTHING }

	@Header("Who can pick it up")
	@Param(desc = "Elements that can collect it.")
	public List<PlayerItem> collectors = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words (separate with commas). Leave the list and this text empty to let any element with a Health behavior collect it.")
	public String collectorPrefix = "player";

	@Header("Reward")
	@Param(desc = "Name of the scene variable (create it in Variables: an int or float), e.g. score or coins. Empty = no variable.")
	public String variable = "score";
	@Param(desc = "Added to the variable when collected.", min = -1000000, max = 1000000)
	public float value = 1;
	@Param(desc = "Heals the collector (needs a Health behavior on it).", min = 0, max = 100000)
	public float heal = 0;
	@Param(desc = "Counts for a Goal that wants every collectible of this group (coins, stars...). Empty = no group.")
	public String group = "";
	@Sound
	@Param(desc = "Played when it is collected.")
	public String collectSound = "";

	@Header("After")
	@Param(desc = "DESTROY removes it, HIDE keeps it invisible (comes back with respawn after), NOTHING leaves it.")
	public AfterCollect afterCollect = AfterCollect.DESTROY;
	@Param(desc = "A short pop (grow and fade) before it disappears.", unit = "s", min = 0, max = 2, step = 0.05)
	public float popTime = 0.12f;
	@Param(desc = "HIDE only: it comes back after this long. 0 = never.", unit = "s", min = 0, max = 3600, step = 0.5)
	public float respawnAfter = 0;

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private final GameplaySupport.Hider hider = new GameplaySupport.Hider();
	private final GameplaySupport.Var var = new GameplaySupport.Var();
	private boolean collected, registered, backPending;
	private float pop, respawnLeft, baseAlpha = 1, baseSx = 1, baseSy = 1;
	private PlayerItem pendingCollector;

	public void onStart() {
		baseAlpha = item.getActor().getColor().a;
		baseSx = item.getActor().getScaleX();
		baseSy = item.getActor().getScaleY();
		register();
	}

	private void register() {
		if (registered || group.length() == 0) return;
		registered = true;
		GameplaySupport.collectibleAdded(stage, group);
	}

	@Block(label = "collect", desc = "Collects it right now as if this element touched it.", labelAr = "اجمع", descAr = "يجمعه الآن كأن هذا العنصر لمسه.", category = "Collectible", args = {"collector"})
	public void collect(PlayerItem collector) {
		if (!collected) pendingCollector = collector == null ? item : collector;
	}

	@Block(label = "bring back", desc = "Shows it again and lets it be collected once more.", labelAr = "أعده", descAr = "يعرضه مجدداً ويسمح بجمعه مرة أخرى.", category = "Collectible")
	public void bringBack() {
		backPending = true;
	}

	@Block(label = "set reward", desc = "What it adds to the variable when collected.", labelAr = "اضبط المكافأة", descAr = "ما يضيفه إلى المتغير عند جمعه.", category = "Collectible", args = {"value"})
	public void setReward(float v) {
		value = v;
	}

	@Block(label = "is collected", desc = "True after it was collected (and not yet brought back).", labelAr = "تم جمعه", descAr = "صحيح بعد جمعه (وقبل إعادته).", category = "Collectible")
	public boolean isCollected() {
		return collected;
	}

	private boolean accepts(PlayerItem o) {
		if (o == null) return false;
		matcher.set(collectorPrefix);
		if (GameplaySupport.noFilter(collectors, matcher)) return GameplaySupport.damageable(o) != null;
		return GameplaySupport.isTarget(o, collectors, matcher);
	}

	public void onCollisionBegin(PlayerItem other) {
		if (!collected && accepts(other)) contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	private void take(PlayerItem by) {
		if (collected) return;
		collected = true;
		if (group.length() > 0) GameplaySupport.collectibleTaken(stage, group);
		if (variable.length() > 0) var.bind(stage, variable).add(value);
		if (heal > 0) {
			Damageable d = GameplaySupport.damageable(by);
			if (d instanceof Health) ((Health) d).heal(heal);
		}
		GameplaySupport.sound(stage, collectSound);
		fire("onCollected", by, value);
		pop = popTime;
		contacts.clear();
		if (pop <= 0) afterPop();
	}

	private void afterPop() {
		restoreLook();
		if (afterCollect == AfterCollect.DESTROY) {
			GameplaySupport.remove(item);
		} else if (afterCollect == AfterCollect.HIDE) {
			hider.hide(item);
			respawnLeft = respawnAfter;
		}
	}

	private void restoreLook() {
		item.getActor().getColor().a = baseAlpha;
		item.getActor().setScale(baseSx, baseSy);
	}

	private void comeBack() {
		if (!collected) return;
		collected = false;
		pop = 0;
		respawnLeft = 0;
		restoreLook();
		hider.show(item);
		if (group.length() > 0) GameplaySupport.collectibleUntaken(stage, group);
		fire("onRespawned");
	}

	public void onUpdate(float dt) {
		if (backPending) {
			backPending = false;
			comeBack();
		}
		if (collected) {
			if (pop > 0) {
				pop -= dt;
				float k = popTime > 0 ? Math.max(0f, pop / popTime) : 0f;
				item.getActor().getColor().a = baseAlpha * k;
				float s = 1f + (1f - k) * 0.5f;
				item.getActor().setScale(baseSx * s, baseSy * s);
				if (pop <= 0) afterPop();
			} else if (respawnLeft > 0) {
				respawnLeft -= dt;
				if (respawnLeft <= 0) comeBack();
			}
			return;
		}
		if (pendingCollector != null) {
			PlayerItem by = pendingCollector;
			pendingCollector = null;
			take(by);
			return;
		}
		contacts.prune();
		contacts.poll(item, collectors);
		for (int i = 0; i < contacts.size(); i++) {
			if (!contacts.live(i) || !contacts.takeFresh(i)) continue;
			take(contacts.item(i));
			return;
		}
	}

	public void onDestroy() {
		if (registered && group.length() > 0) GameplaySupport.collectibleRemoved(stage, group, collected);
		contacts.clear();
	}
}
