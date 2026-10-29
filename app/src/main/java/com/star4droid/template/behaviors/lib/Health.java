package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Damageable;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;

@BehaviorInfo(name = "Health", icon = "heart", category = "Combat", targets = BehaviorInfo.Target.BOTH,
		events = {"onDamage(float amount, PlayerItem source)", "onHeal(float amount)", "onDeath(PlayerItem killer)", "onRevive()"},
		desc = "Hit points for any element: damage, healing, invulnerability frames, regeneration, a progress bar that follows the health and what happens on death (destroy, hide, respawn, restart the scene or open another one).")
public class Health extends Behavior implements Damageable {
	public enum DieAction { DESTROY, HIDE, RESPAWN, RESTART_SCENE, OPEN_SCENE, NOTHING }

	@Header("Hit points")
	@Param(desc = "Hit points when full.", min = 1, max = 1000000)
	public float maxHealth = 100;
	@Param(desc = "Hit points at the start of the scene.", min = 0, max = 1000000)
	public float health = 100;
	@Param(desc = "After a hit nothing can hurt it for this long (also stops damage from several touching enemies at once).", unit = "s", min = 0, max = 30, step = 0.1)
	public float invulnerableTime = 0;
	@Param(desc = "Hit points recovered per second (0 = no regeneration).", unit = "hp/s", min = 0, max = 10000, step = 0.5)
	public float regenPerSecond = 0;
	@Param(desc = "Regeneration starts this long after the last hit.", unit = "s", min = 0, max = 60, step = 0.5)
	public float regenDelay = 3;

	@Header("Display")
	@Param(desc = "A progress bar element that shows the health (its Max and Progress are driven for you). Optional.")
	public ProgressItem bar;
	@Param(desc = "Blink while invulnerable so the player sees the hit.")
	public boolean blinkWhileInvulnerable = true;
	@Param(desc = "Shake the camera when hurt (needs a CameraShake in the scene). 0 = off.", min = 0, max = 1, step = 0.05)
	public float shakeOnHit = 0;
	@Sound
	@Param(desc = "Played when it takes damage.")
	public String hurtSound = "";
	@Sound
	@Param(desc = "Played when it dies.")
	public String deathSound = "";

	@Header("Death")
	@Param(desc = "What happens when the hit points reach 0. RESPAWN needs a Respawn behavior on the same element (else the scene restarts).")
	public DieAction dieAction = DieAction.DESTROY;
	@Param(desc = "Wait this long after dying before the action (time for your death animation).", unit = "s", min = 0, max = 30, step = 0.1)
	public float dieDelay = 0;
	@Param(desc = "Scene to open for OPEN_SCENE; for RESTART_SCENE leave empty to reopen the current scene (type its exact name if it has capital letters).")
	public String scene = "";

	@Header("Hit reaction")
	@Param(desc = "Behaviors switched off while stunned by a hit that has a stun time (names separated by commas).")
	public String stunBehaviors = "PlatformerPlayer, TopDownPlayer, JoystickMovement";

	private boolean inited, dead, acted, gone;
	private float invul, sinceHit, deathTimer, stunLeft;
	private float shownHealth = -1, shownMax = -1;
	private float baseAlpha = 1;
	private boolean blinking, showPending;
	private final GameplaySupport.Hider hider = new GameplaySupport.Hider();
	private final ArrayList<String> stunned = new ArrayList<String>();

	private void init() {
		if (inited) return;
		inited = true;
		if (maxHealth < 1) maxHealth = 1;
		if (health > maxHealth) health = maxHealth;
		if (health < 0) health = 0;
		if (item != null && item.getActor() != null) baseAlpha = item.getActor().getColor().a;
		refreshBar();
	}

	public void onStart() {
		init();
	}

	// ------------------------------------------------------------------ blocks

	@Block(label = "damage", desc = "Takes hit points away (respects invulnerability).", labelAr = "ألحق ضرراً", descAr = "ينقص نقاط الحياة (مع احترام فترة الحصانة).", category = "Health", args = {"amount"})
	public void damage(float amount) {
		takeDamage(amount, null, 0, 0, 0);
	}

	@Block(label = "damage by", desc = "Takes hit points away and tells who did it.", labelAr = "ألحق ضرراً بواسطة", descAr = "ينقص نقاط الحياة ويحدد من تسبب بذلك.", category = "Health", args = {"amount", "source"})
	public void damageBy(float amount, PlayerItem source) {
		takeDamage(amount, source, 0, 0, 0);
	}

	@Block(label = "heal", desc = "Adds hit points (never above the maximum).", labelAr = "عالج", descAr = "يضيف نقاط حياة (دون تجاوز الحد الأقصى).", category = "Health", args = {"amount"})
	public void heal(float amount) {
		init();
		if (dead || amount <= 0) return;
		float before = health;
		health = Math.min(maxHealth, health + amount);
		if (health > before) fire("onHeal", health - before);
	}

	@Block(label = "kill", desc = "Sets the hit points to 0 right now (ignores invulnerability).", labelAr = "اقتل", descAr = "يجعل نقاط الحياة صفراً فوراً (يتجاهل الحصانة).", category = "Health")
	public void kill() {
		init();
		if (dead) return;
		health = 0;
		die(null);
	}

	@Block(label = "revive", desc = "Brings it back to life with the given share of the maximum (0..1).", labelAr = "أحيِ", descAr = "يعيد الحياة بالنسبة المحددة من الحد الأقصى (0..1).", category = "Health", args = {"fraction"})
	public void revive(float fraction) {
		init();
		if (gone) return;
		boolean was = dead;
		dead = false;
		acted = false;
		deathTimer = 0;
		health = Math.max(1f, maxHealth * clamp(fraction, 0f, 1f));
		if (hider.isHidden()) showPending = true;
		if (was) fire("onRevive");
	}

	@Block(label = "set health", desc = "Sets the current hit points (0 = dead).", labelAr = "اضبط الحياة", descAr = "يضبط نقاط الحياة الحالية (0 = ميت).", category = "Health", args = {"health"})
	public void setHealth(float value) {
		init();
		if (dead) return;
		health = clamp(value, 0f, maxHealth);
		if (health <= 0) die(null);
	}

	@Block(label = "set max health", desc = "Changes the maximum; optionally heals to full.", labelAr = "اضبط الحد الأقصى للحياة", descAr = "يغيّر الحد الأقصى ويمكنه ملء الحياة.", category = "Health", args = {"max", "fill"})
	public void setMaxHealth(float max, boolean fill) {
		init();
		maxHealth = Math.max(1f, max);
		health = fill ? maxHealth : Math.min(health, maxHealth);
	}

	@Block(label = "invulnerable for", desc = "Nothing can hurt it for this many seconds (shield power-ups).", labelAr = "محصّن لمدة", descAr = "لا شيء يستطيع إيذاءه لعدد الثواني (دروع التعزيز).", category = "Health", args = {"seconds"})
	public void makeInvulnerable(float seconds) {
		if (seconds > invul) invul = seconds;
	}

	@Block(label = "health", desc = "Current hit points.", labelAr = "الحياة", descAr = "نقاط الحياة الحالية.", category = "Health")
	public float getHealth() {
		init();
		return health;
	}

	@Block(label = "health percent", desc = "Current hit points as 0..100 % of the maximum.", labelAr = "نسبة الحياة", descAr = "الحياة الحالية كنسبة 0..100 من الحد الأقصى.", category = "Health")
	public float getHealthPercent() {
		init();
		return health * 100f / maxHealth;
	}

	@Block(label = "is dead", desc = "True when the hit points reached 0.", labelAr = "ميت", descAr = "صحيح عندما تصل الحياة إلى 0.", category = "Health")
	public boolean isDead() {
		return dead;
	}

	@Block(label = "is invulnerable", desc = "True while a hit cannot hurt it.", labelAr = "محصّن", descAr = "صحيح طالما لا يمكن إيذاؤه.", category = "Health")
	public boolean isInvulnerable() {
		return invul > 0;
	}

	// ------------------------------------------------------------------ damage

	public boolean takeDamage(float amount, PlayerItem source, float pushX, float pushY, float stunSeconds) {
		init();
		if (!enabled || dead || amount <= 0 || invul > 0) return false;
		health = Math.max(0f, health - amount);
		sinceHit = 0;
		invul = invulnerableTime;
		fire("onDamage", amount, source);
		GameplaySupport.sound(stage, hurtSound);
		if (shakeOnHit > 0) GameplaySupport.shake(stage, shakeOnHit);
		if ((pushX != 0 || pushY != 0) && health > 0) GameplaySupport.setVel(stage, item, pushX, pushY);
		if (stunSeconds > 0 && health > 0) stun(stunSeconds);
		if (health <= 0) die(source);
		return true;
	}

	private void stun(float seconds) {
		if (stunLeft <= 0) {
			String[] ids = stunBehaviors == null ? new String[0] : stunBehaviors.split("[,; ]+");
			for (int i = 0; i < ids.length; i++) {
				String id = ids[i].trim();
				if (id.length() > 0 && item.hasBehavior(id) && item.isBehaviorEnabled(id)) {
					item.getBehavior(id).setEnabled(false);
					stunned.add(id);
				}
			}
		}
		if (seconds > stunLeft) stunLeft = seconds;
	}

	private void endStun() {
		for (int i = 0; i < stunned.size(); i++) {
			Behavior b = item.getBehavior(stunned.get(i));
			if (b != null) b.setEnabled(true);
		}
		stunned.clear();
		stunLeft = 0;
	}

	private void die(PlayerItem killer) {
		dead = true;
		health = 0;
		fire("onDeath", killer);
		GameplaySupport.sound(stage, deathSound);
		deathTimer = dieDelay; // the action runs in onUpdate: destroying / hiding inside a contact callback would touch a locked physics world
	}

	private void runDieAction() {
		if (acted) return;
		acted = true;
		switch (dieAction) {
			case DESTROY:
				gone = true;
				GameplaySupport.remove(item);
				break;
			case HIDE:
				hider.hide(item);
				break;
			case RESPAWN: {
				Behavior r = item.getBehavior("Respawn");
				if (r instanceof Respawn) ((Respawn) r).respawn();
				else stage.openScene(GameplaySupport.sceneName(stage, scene));
				break;
			}
			case RESTART_SCENE:
				stage.openScene(GameplaySupport.sceneName(stage, scene));
				break;
			case OPEN_SCENE:
				if (scene != null && scene.length() > 0) stage.openScene(scene);
				break;
			default:
				break;
		}
	}

	// ------------------------------------------------------------------ frame

	public void onUpdate(float dt) {
		init();
		if (showPending) {
			showPending = false;
			hider.show(item);
		}
		if (dead) {
			if (!acted) {
				deathTimer -= dt;
				if (deathTimer <= 0) runDieAction();
			}
			return;
		}
		sinceHit += dt;
		if (stunLeft > 0) {
			stunLeft -= dt;
			if (stunLeft <= 0) endStun();
		}
		if (invul > 0) {
			invul -= dt;
			if (blinkWhileInvulnerable && invul > 0) {
				boolean show = ((int) (invul * 12f) & 1) == 0;
				item.getActor().getColor().a = show ? baseAlpha : baseAlpha * 0.25f;
				blinking = true;
			}
		}
		if (blinking && invul <= 0) {
			item.getActor().getColor().a = baseAlpha;
			blinking = false;
		}
		if (regenPerSecond > 0 && health < maxHealth && sinceHit >= regenDelay) health = Math.min(maxHealth, health + regenPerSecond * dt);
		refreshBar();
	}

	private void refreshBar() {
		if (bar == null || (shownHealth == health && shownMax == maxHealth)) return;
		bar.setMax(maxHealth);
		bar.setProgress(health);
		shownHealth = health;
		shownMax = maxHealth;
	}

	public void onDisable() {
		if (blinking) item.getActor().getColor().a = baseAlpha;
		blinking = false;
		endStun();
	}

	public void onDestroy() {
		stunned.clear();
	}
}
