package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sound;

@BehaviorInfo(name = "Respawn", icon = "restart", category = "Gameplay", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onRespawn(float x, float y)", "onOutOfLives()"},
		desc = "Brings the player back to the last Checkpoint (or to where it started) after dying or falling out of the world, with a short invulnerability, an optional lives counter and game over scene.")
@Requires(body = true, note = "Needs a physical element. Set the die action of Health to RESPAWN to use it on death.")
public class Respawn extends Behavior {
	@Header("Where")
	@Param(desc = "Must match the channel of the Checkpoints it should use.")
	public String channel = "";
	@Param(desc = "At the start of the scene, move to the saved checkpoint if there is one (after a restart).")
	public boolean startAtCheckpoint = true;
	@Param(desc = "Also look for a checkpoint saved on disk.")
	public boolean saveToDisk = false;

	@Header("When")
	@Param(desc = "Respawn when the element falls below the line below (platformer pits).")
	public boolean respawnWhenFalling = false;
	@Param(desc = "Height of the line (editor pixels, y up). Below this the element respawns.", unit = "px", min = -100000, max = 100000)
	public float fallBelowY = -300;

	@Header("How")
	@Param(desc = "Wait this long (invisible) before coming back.", unit = "s", min = 0, max = 10, step = 0.1)
	public float delay = 0.5f;
	@Param(desc = "Nothing can hurt it for this long after coming back (needs Health).", unit = "s", min = 0, max = 30, step = 0.1)
	public float invulnerableTime = 2;
	@Param(desc = "Hit points are full again (needs Health).")
	public boolean restoreHealth = true;
	@Sound
	@Param(desc = "Played when it comes back.")
	public String respawnSound = "";

	@Header("Lives")
	@Param(desc = "Number of lives. 0 = unlimited. When the last one is lost the scene below opens.", min = 0, max = 99)
	public int lives = 0;
	@Param(desc = "Scene variable (int) that always shows the lives left, e.g. lives. Empty = none.")
	public String livesVariable = "";
	@Param(desc = "Scene to open when there are no lives left (game over). Empty = do nothing, only the event fires.")
	public String gameOverScene = "";

	private final GameplaySupport.Hider hider = new GameplaySupport.Hider();
	private final GameplaySupport.Var livesVar = new GameplaySupport.Var();
	private float startX, startY, wait;
	private boolean waiting, hidePending;
	private int livesLeft;

	public void onStart() {
		startX = centerX();
		startY = centerY();
		livesLeft = lives;
		syncLives();
		if (startAtCheckpoint) {
			float[] p = GameplaySupport.getCheckpoint(stage, channel, saveToDisk);
			if (p != null) moveTo(p[0], p[1]);
		}
	}

	private void syncLives() {
		if (livesVariable.length() > 0 && lives > 0) livesVar.bind(stage, livesVariable).set(livesLeft);
	}

	private void moveTo(float x, float y) {
		GameplaySupport.moveCenter(item, x, y);
		Body b = body();
		if (b != null) {
			b.setLinearVelocity(0, 0);
			b.setAngularVelocity(0);
		}
	}

	@Block(label = "respawn", desc = "Takes a life and brings the element back to the last checkpoint after the delay.", labelAr = "أعد الظهور", descAr = "يأخذ حياة ويعيد العنصر إلى آخر نقطة حفظ بعد التأخير.", category = "Respawn")
	public void respawn() {
		if (waiting) return;
		if (lives > 0) {
			livesLeft--;
			syncLives();
			if (livesLeft <= 0) {
				fire("onOutOfLives");
				if (gameOverScene.length() > 0) stage.openScene(gameOverScene);
				return;
			}
		}
		waiting = true;
		wait = delay;
		hidePending = true; // hidden in onUpdate (a block may call this inside a contact callback)
	}

	@Block(label = "set respawn point", desc = "Chooses the place to come back to (center, px).", labelAr = "اضبط نقطة العودة", descAr = "يحدد مكان العودة (المركز، بكسل).", category = "Respawn", args = {"x", "y"})
	public void setRespawnPoint(float x, float y) {
		GameplaySupport.setCheckpoint(stage, channel, x, y, saveToDisk);
	}

	@Block(label = "add life", desc = "Gives extra lives.", labelAr = "أضف حياة", descAr = "يمنح حيوات إضافية.", category = "Respawn", args = {"count"})
	public void addLife(int count) {
		if (lives <= 0) return;
		livesLeft += count;
		syncLives();
	}

	@Block(label = "lives left", desc = "Lives that remain (0 when lives are unlimited).", labelAr = "الحيوات المتبقية", descAr = "عدد الحيوات المتبقية (0 إذا كانت غير محدودة).", category = "Respawn")
	public int getLivesLeft() {
		return livesLeft;
	}

	@Block(label = "reset lives", desc = "Back to the full number of lives.", labelAr = "أعد الحيوات", descAr = "يعيد العدد الكامل للحيوات.", category = "Respawn")
	public void resetLives() {
		livesLeft = lives;
		syncLives();
	}

	@Block(label = "forget checkpoints", desc = "Erases the saved respawn point (new game).", labelAr = "انسَ نقاط الحفظ", descAr = "يمسح نقطة العودة المحفوظة (لعبة جديدة).", category = "Respawn")
	public void forgetCheckpoints() {
		GameplaySupport.clearCheckpoint(stage, channel);
	}

	public void onUpdate(float dt) {
		if (hidePending) {
			hidePending = false;
			hider.hide(item);
		}
		if (waiting) {
			wait -= dt;
			if (wait <= 0) comeBack();
			return;
		}
		if (respawnWhenFalling && centerY() < fallBelowY) respawn();
	}

	private void comeBack() {
		waiting = false;
		float[] p = GameplaySupport.getCheckpoint(stage, channel, saveToDisk);
		float x = p != null ? p[0] : startX, y = p != null ? p[1] : startY;
		moveTo(x, y);
		hider.show(item);
		Behavior hb = item.getBehavior("Health");
		if (hb instanceof Health) {
			Health h = (Health) hb;
			if (restoreHealth || h.isDead()) h.revive(1f);
			if (invulnerableTime > 0) h.makeInvulnerable(invulnerableTime);
		}
		GameplaySupport.sound(stage, respawnSound);
		fire("onRespawn", x, y);
	}

	public void onDisable() {
		if (waiting) {
			waiting = false;
			hidePending = false;
			hider.show(item);
		}
	}
}
