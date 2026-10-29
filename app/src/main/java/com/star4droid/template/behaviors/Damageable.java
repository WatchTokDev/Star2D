package com.star4droid.template.behaviors;

import com.star4droid.template.Utils.PlayerItem;

/**
 * Implemented by the behaviors that can be hurt (Health, Destructible). Everything that deals damage (DamageOnTouch,
 * Projectile, Turret, the Shooter-like code of your own behaviors) calls {@link GameplaySupport#hit} which finds the
 * Damageable of the target, so a new kind of "life" only has to implement this interface.
 */
public interface Damageable {
	/**
	 * @param amount damage points (&gt; 0)
	 * @param source who hurts (may be null)
	 * @param pushX  knockback speed in px/s added to the target (x right), 0 = none
	 * @param pushY  knockback speed in px/s (y up)
	 * @param stunSeconds how long the target's movement behaviors stay off after the hit (0 = not at all)
	 * @return true if the damage was taken, false if it was ignored (dead, invulnerable, disabled)
	 */
	boolean takeDamage(float amount, PlayerItem source, float pushX, float pushY, float stunSeconds);

	boolean isDead();
}
