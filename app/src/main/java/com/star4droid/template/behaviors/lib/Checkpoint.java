package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Image;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Checkpoint", icon = "flag-variant", category = "Gameplay", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onActivated(PlayerItem by)"},
		desc = "A flag that remembers where to come back to: when the player touches it, the Respawn behavior returns there after death or a fall. Works across scene restarts (and, if you want, across app launches).")
@Requires(body = true, note = "Needs a physical element; make it a Sensor so the player runs through it.")
public class Checkpoint extends Behavior {
	@Header("Who activates it")
	@Param(desc = "Elements that activate it.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words (separate with commas).")
	public String targetPrefix = "player";

	@Header("Respawn point")
	@Param(desc = "Checkpoints and Respawn behaviors with the same channel work together. Use different channels for different players.")
	public String channel = "";
	@Param(desc = "The player comes back this far to the right of the flag's center (negative = left).", unit = "px", min = -2000, max = 2000)
	public float offsetX = 0;
	@Param(desc = "The player comes back this far above the flag's center.", unit = "px", min = -2000, max = 2000)
	public float offsetY = 0;
	@Param(desc = "Keep the checkpoint after the app is closed (Preferences). Off: it lasts until the game is closed or forgetCheckpoints is called.")
	public boolean saveToDisk = false;

	@Header("Look")
	@Image
	@Param(desc = "Picture after it is activated (optional), e.g. a raised flag.")
	public String activeImage = "";
	@Sound
	@Param(desc = "Played when it is activated.")
	public String activateSound = "";

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private String origImage = "";

	public void onStart() {
		try {
			if (item.getProperties() instanceof com.star4droid.star2d.ElementDefs.BoxDef) origImage = ((com.star4droid.star2d.ElementDefs.BoxDef) item.getProperties()).image;
		} catch (RuntimeException ignored) {
		}
		if (isActive() && activeImage.length() > 0) stage.setImage(item, activeImage);
	}

	@Block(label = "activate checkpoint", desc = "Makes this the place to come back to.", labelAr = "فعّل نقطة الحفظ", descAr = "يجعل هذا المكان نقطة العودة.", category = "Checkpoint")
	public void activate() {
		GameplaySupport.setCheckpoint(stage, channel, centerX() + offsetX, centerY() + offsetY, saveToDisk);
		if (activeImage.length() > 0) stage.setImage(item, activeImage);
		GameplaySupport.sound(stage, activateSound);
	}

	@Block(label = "forget checkpoints", desc = "Erases the saved respawn point of this channel (use it for a new game).", labelAr = "انسَ نقاط الحفظ", descAr = "يمسح نقطة العودة المحفوظة لهذه القناة (للعبة جديدة).", category = "Checkpoint")
	public void forgetCheckpoints() {
		GameplaySupport.clearCheckpoint(stage, channel);
		if (origImage != null && origImage.length() > 0) stage.setImage(item, origImage);
	}

	@Block(label = "is active checkpoint", desc = "True if this is the checkpoint the player returns to.", labelAr = "نقطة الحفظ الحالية", descAr = "صحيح إذا كانت هذه نقطة العودة الحالية.", category = "Checkpoint")
	public boolean isActive() {
		float[] p = GameplaySupport.getCheckpoint(stage, channel, saveToDisk);
		return p != null && Math.abs(p[0] - (centerX() + offsetX)) < 1f && Math.abs(p[1] - (centerY() + offsetY)) < 1f;
	}

	public void onCollisionBegin(PlayerItem other) {
		matcher.set(targetPrefix);
		if (GameplaySupport.isTarget(other, targets, matcher)) contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	public void onUpdate(float dt) {
		contacts.prune();
		contacts.poll(item, targets);
		for (int i = 0; i < contacts.size(); i++) {
			if (!contacts.live(i) || !contacts.takeFresh(i)) continue;
			if (isActive()) continue;
			activate();
			fire("onActivated", contacts.item(i));
		}
	}

	public void onDestroy() {
		contacts.clear();
	}
}
