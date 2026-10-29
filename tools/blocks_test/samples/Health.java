package com.star4droid.Game.Behaviors;

import com.badlogic.gdx.graphics.Color;
import com.star4droid.template.Items.Joystick;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.*;

import java.util.List;

/**
 * Sample project behavior used by tools/test_blocks_behaviors.py.
 * A comment that mentions @Block(label = "not a block") must be ignored.
 */
@BehaviorInfo(name = "Health", icon = "heart", category = "Combat", desc = "Hit points.")
public class Health extends Behavior {
	public float maxHealth = 10;
	public float health = 10;
	public boolean invincible = false;
	public String label = "hp";

	// @Block(label = "also ignored") public void ghost() {}

	@Block(label = "hurt", desc = "Takes damage. "
			+ "Dies at 0.", labelAr = "جرح", descAr = "يأخذ ضرراً \"ويموت\" عند الصفر.", category = "Health", args = {"amount"})
	public void damage(float amount) {
		if (!invincible) health -= amount;
	}

	@Block(label = "heal", desc = "Adds hit points (never above the maximum).")
	public void heal(final float amount) {
		health = Math.min(maxHealth, health + amount);
	}

	@Block(label = "health")
	public float getHealth() {
		return health;
	}

	@Block(label = "is dead", desc = "True when health is 0 or less.", labelAr = "ميت")
	public boolean isDead() {
		return health <= 0;
	}

	@Block(label = "set max health", args = {"max"})
	@Deprecated
	public void setMaxHealth(int max) {
		maxHealth = max;
	}

	@Block(label = "set invincible", args = {"on"})
	public void setInvincible(boolean on) {
		invincible = on;
	}

	@Block(label = "set label", args = {"text"})
	public void setLabel(String text) {
		label = text;
	}

	@Block(label = "label")
	public String getLabel() {
		return label;
	}

	@Block(label = "tint with", args = {"color"})
	public void tint(Color color) {
		if (item != null) item.getActor().setColor(color);
	}

	@Block(label = "follow")
	public void follow(PlayerItem leader, float distance, boolean smooth) {
	}

	@Block(label = "use joystick")
	public void useJoystick(Joystick j) {
	}

	@Block(label = "last attacker")
	public PlayerItem getAttacker() {
		return null;
	}

	@Block(label = "max health as long")
	public long maxAsLong() {
		return (long) maxHealth;
	}

	@Block(label = "damage twice")
	public void damage(float a, float b) {
		damage(a);
		damage(b);
	}

	@Block(label = "names") // unsupported: List parameter
	public void names(List<String> n) {
	}

	@Block(label = "static one")
	public static void staticOne() {
	}

	@Block(label = "private one")
	private void hidden() {
	}

	@Block(label = "generic return")
	public List<String> list() {
		return null;
	}

	public void notABlock() {
	}
}
