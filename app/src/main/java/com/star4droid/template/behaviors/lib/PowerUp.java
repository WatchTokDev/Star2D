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

@BehaviorInfo(name = "Power Up", icon = "lightning-bolt", category = "Gameplay", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onCollected(PlayerItem collector)", "onExpired(PlayerItem collector)"},
		desc = "A pickup that changes the settings of a behavior on whoever takes it for a while (faster, higher jumps, faster fire...), then puts them back. Can also heal and make invulnerable.")
@Requires(body = true, note = "Needs a physical element; best as Sensor so the player walks through it.")
public class PowerUp extends Behavior {
	public enum Mode { SET, ADD, MULTIPLY }
	public enum AfterCollect { DESTROY, HIDE }

	@Header("Who can pick it up")
	@Param(desc = "Elements that can take it.")
	public List<PlayerItem> collectors = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words (separate with commas). Leave the list and this text empty to let any element with a Health behavior take it.")
	public String collectorPrefix = "player";

	@Header("Effect 1")
	@Param(label = "Behavior", desc = "Class name of the behavior on the collector, e.g. PlatformerPlayer, Shooter, TopDownPlayer. Empty = no effect.")
	public String behavior1 = "PlatformerPlayer";
	@Param(label = "Setting", desc = "The setting (public field) to change, exactly as written in the behavior, e.g. jumpSpeed, speed, fireRate.")
	public String parameter1 = "jumpSpeed";
	@Param(label = "How", desc = "SET puts the value, ADD adds it, MULTIPLY multiplies the current value.")
	public Mode mode1 = Mode.MULTIPLY;
	@Param(label = "Amount", min = -100000, max = 100000)
	public float amount1 = 1.5f;

	@Header("Effect 2")
	@Param(label = "Behavior", desc = "A second setting to change at the same time (optional).")
	public String behavior2 = "";
	@Param(label = "Setting", desc = "The second setting to change, exactly as written in the behavior.")
	public String parameter2 = "speed";
	@Param(label = "How", desc = "SET puts the value, ADD adds it, MULTIPLY multiplies the current value.")
	public Mode mode2 = Mode.MULTIPLY;
	@Param(label = "Amount", min = -100000, max = 100000)
	public float amount2 = 1.5f;

	@Header("Duration and extras")
	@Param(desc = "How long the effects last. 0 = for ever.", unit = "s", min = 0, max = 3600, step = 0.5)
	public float duration = 10;
	@Param(desc = "Hit points restored to the collector (needs Health).", min = 0, max = 100000)
	public float heal = 0;
	@Param(desc = "The collector cannot be hurt for this long (needs Health).", unit = "s", min = 0, max = 600, step = 0.5)
	public float shield = 0;
	@Sound
	@Param(desc = "Played when it is taken.")
	public String collectSound = "";

	@Header("After")
	@Param(desc = "DESTROY removes it once the effects ended, HIDE only hides it.")
	public AfterCollect afterCollect = AfterCollect.DESTROY;
	@Param(desc = "It comes back after this long (so the same power-up can be taken again). 0 = never.", unit = "s", min = 0, max = 3600, step = 0.5)
	public float respawnAfter = 0;

	private static final class Effect {
		PlayerItem target;
		Behavior b1, b2;
		Object o1, o2;
		float left;
	}

	private final ArrayList<Effect> effects = new ArrayList<Effect>();
	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private final GameplaySupport.Hider hider = new GameplaySupport.Hider();
	private boolean taken;
	private float respawnLeft;
	private PlayerItem pending;

	@Block(label = "apply to", desc = "Gives the effect to this element now, as if it had taken the power-up.", labelAr = "طبّق على", descAr = "يمنح العنصر التأثير الآن كأنه التقط التعزيز.", category = "Power Up", args = {"target"})
	public void applyTo(PlayerItem target) {
		if (target != null) pending = target;
	}

	@Block(label = "end all effects", desc = "Puts back all the settings right now.", labelAr = "أنهِ كل التأثيرات", descAr = "يعيد كل الإعدادات فوراً.", category = "Power Up")
	public void endAll() {
		for (int i = effects.size() - 1; i >= 0; i--) end(i, false);
	}

	@Block(label = "active effects", desc = "How many collectors currently have the effect.", labelAr = "التأثيرات الفعالة", descAr = "عدد من لديهم التأثير حالياً.", category = "Power Up")
	public int getActiveCount() {
		return effects.size();
	}

	private boolean accepts(PlayerItem o) {
		if (o == null) return false;
		matcher.set(collectorPrefix);
		if (GameplaySupport.noFilter(collectors, matcher)) return GameplaySupport.damageable(o) != null;
		return GameplaySupport.isTarget(o, collectors, matcher);
	}

	public void onCollisionBegin(PlayerItem other) {
		if (!taken && accepts(other)) contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	private static Object change(Object old, Mode mode, float amount) {
		if (old instanceof Boolean) return mode == Mode.SET ? Boolean.valueOf(amount != 0) : old;
		if (!(old instanceof Number)) return null;
		float v = ((Number) old).floatValue();
		return Float.valueOf(mode == Mode.SET ? amount : (mode == Mode.ADD ? v + amount : v * amount));
	}

	private Behavior apply(PlayerItem target, String id, String field, Mode mode, float amount, Effect e, int slot) {
		if (id == null || id.length() == 0 || field == null || field.length() == 0) return null;
		Behavior b = target.getBehavior(id);
		if (b == null) return null;
		Object old = b.getParam(field);
		Object now = change(old, mode, amount);
		if (now == null || !b.setParam(field, now)) return null;
		if (slot == 1) e.o1 = old;
		else e.o2 = old;
		return b;
	}

	private void give(PlayerItem by) {
		for (int i = 0; i < effects.size(); i++) {
			if (effects.get(i).target == by) { // already boosted: only extend, never apply twice (the original values stay safe)
				effects.get(i).left = duration;
				return;
			}
		}
		Effect e = new Effect();
		e.target = by;
		e.left = duration;
		e.b1 = apply(by, behavior1, parameter1, mode1, amount1, e, 1);
		e.b2 = apply(by, behavior2, parameter2, mode2, amount2, e, 2);
		if (duration <= 0) return; // permanent: nothing to put back
		if (e.b1 != null || e.b2 != null) effects.add(e);
	}

	private void end(int i, boolean fireEvent) {
		Effect e = effects.remove(i);
		if (alive(e.target)) {
			if (e.b1 != null) e.b1.setParam(parameter1, e.o1);
			if (e.b2 != null) e.b2.setParam(parameter2, e.o2);
		}
		if (fireEvent) fire("onExpired", e.target);
	}

	private void take(PlayerItem by) {
		give(by);
		Damageable d = GameplaySupport.damageable(by);
		if (d instanceof Health) {
			if (heal > 0) ((Health) d).heal(heal);
			if (shield > 0) ((Health) d).makeInvulnerable(shield);
		}
		GameplaySupport.sound(stage, collectSound);
		fire("onCollected", by);
		taken = true;
		contacts.clear();
		hider.hide(item); // hidden, not destroyed: it keeps running to put the settings back
		respawnLeft = respawnAfter;
	}

	public void onUpdate(float dt) {
		for (int i = effects.size() - 1; i >= 0; i--) {
			effects.get(i).left -= dt;
			if (effects.get(i).left <= 0 || !alive(effects.get(i).target)) end(i, true);
		}
		if (taken) {
			if (respawnLeft > 0) {
				respawnLeft -= dt;
				if (respawnLeft <= 0) {
					taken = false;
					hider.show(item);
				}
			} else if (effects.isEmpty() && afterCollect == AfterCollect.DESTROY) {
				GameplaySupport.remove(item);
			}
			return;
		}
		if (pending != null) {
			PlayerItem p = pending;
			pending = null;
			take(p);
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
		for (int i = effects.size() - 1; i >= 0; i--) end(i, false);
		contacts.clear();
	}
}
