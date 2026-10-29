package com.star4droid.template.behaviors.lib;

import com.badlogic.gdx.physics.box2d.Body;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Requires;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Door", icon = "door-open", category = "Gameplay", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onOpen(PlayerItem who)", "onLocked(PlayerItem who)"},
		desc = "A door, portal or exit: when the player touches it (or presses a button next to it) it opens another scene or teleports the player to another element. Can need a key (a scene variable) first.")
@Requires(body = true, note = "Needs a physical element; make it a Sensor so the player can stand in it.")
public class Door extends Behavior {
	public enum DoorMode { OPEN_SCENE, TELEPORT }
	public enum Trigger { ON_TOUCH, WHEN_PRESSED }

	@Header("Who")
	@Param(desc = "Elements that can use it.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words (separate with commas).")
	public String targetPrefix = "player";

	@Header("What it does")
	@Param(desc = "OPEN_SCENE opens another scene, TELEPORT moves the player to the destination element.")
	public DoorMode mode = DoorMode.OPEN_SCENE;
	@Param(desc = "OPEN_SCENE: the scene to open (exact name).")
	public String scene = "";
	@Param(desc = "TELEPORT: the player appears at the center of this element (another Door makes a two-way portal).")
	public PlayerItem destination;
	@Param(desc = "Wait this long after the door opens (time for a sound or an animation).", unit = "s", min = 0, max = 10, step = 0.1)
	public float delay = 0;

	@Header("How it opens")
	@Param(desc = "ON_TOUCH: at once. WHEN_PRESSED: the player must also hold the button below (or press Up / W on a keyboard).")
	public Trigger trigger = Trigger.ON_TOUCH;
	@Param(desc = "The button element to hold with WHEN_PRESSED (optional).")
	public PlayerItem useButton;
	@Param(desc = "A scene variable that must reach the number below, e.g. keys. Empty = always open.")
	public String needVariable = "";
	@Param(desc = "The needed amount of the variable.", min = 0, max = 1000000)
	public float needAtLeast = 1;
	@Param(desc = "Using the door takes that amount away from the variable (the key is used up).")
	public boolean consume = false;
	@Sound
	@Param(desc = "Played when the door opens.")
	public String openSound = "";
	@Sound
	@Param(desc = "Played when the player touches the door but the key is missing.")
	public String lockedSound = "";

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private final GameplaySupport.Var var = new GameplaySupport.Var();
	private PlayerItem going;
	private float wait;
	private boolean done;

	@Block(label = "use door", desc = "This element uses the door now (checks the key first).", labelAr = "استخدم الباب", descAr = "هذا العنصر يستخدم الباب الآن (بعد فحص المفتاح).", category = "Door", args = {"who"})
	public void use(PlayerItem who) {
		if (who != null) tryOpen(who);
	}

	@Block(label = "set door scene", desc = "The scene this door opens.", labelAr = "اضبط مشهد الباب", descAr = "المشهد الذي يفتحه هذا الباب.", category = "Door", args = {"scene"})
	public void setScene(String name) {
		scene = name == null ? "" : name;
	}

	@Block(label = "can open", desc = "True if the key condition is met.", labelAr = "يمكن فتحه", descAr = "صحيح إذا تحقق شرط المفتاح.", category = "Door")
	public boolean canOpen() {
		return needVariable.length() == 0 || var.bind(stage, needVariable).get() >= needAtLeast;
	}

	private boolean accepts(PlayerItem o) {
		matcher.set(targetPrefix);
		return GameplaySupport.isTarget(o, targets, matcher);
	}

	public void onCollisionBegin(PlayerItem other) {
		if (accepts(other)) contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	private boolean tryOpen(PlayerItem who) {
		if (going != null || done) return false;
		if (!canOpen()) {
			GameplaySupport.sound(stage, lockedSound);
			fire("onLocked", who);
			return false;
		}
		if (consume && needVariable.length() > 0) var.bind(stage, needVariable).add(-needAtLeast);
		GameplaySupport.sound(stage, openSound);
		fire("onOpen", who);
		going = who;
		wait = delay;
		return true;
	}

	private void go() {
		PlayerItem who = going;
		going = null;
		if (mode == DoorMode.TELEPORT) {
			if (destination != null && alive(destination) && alive(who)) {
				GameplaySupport.lock(who, 1f);
				GameplaySupport.moveCenter(who, cx(destination), cy(destination));
				Body b = who.getBody();
				if (b != null) {
					b.setLinearVelocity(0, 0);
					b.setAngularVelocity(0);
				}
			}
		} else if (scene.length() > 0) {
			done = true;
			stage.openScene(scene);
		}
	}

	public void onUpdate(float dt) {
		if (going != null) {
			wait -= dt;
			if (wait <= 0) go();
			return;
		}
		contacts.prune();
		contacts.poll(item, targets);
		for (int i = 0; i < contacts.size() && going == null; i++) {
			if (!contacts.live(i)) continue;
			PlayerItem t = contacts.item(i);
			boolean fresh = contacts.takeFresh(i);
			if (GameplaySupport.locked(t)) continue;
			float cool = contacts.timer(i) - dt;
			contacts.setTimer(i, cool);
			if (trigger == Trigger.ON_TOUCH) {
				if (fresh) tryOpen(t);
			} else if (cool <= 0 && (isPressed(useButton) || keyDown(KEY_UP, KEY_W))) {
				contacts.setTimer(i, 0.8f); // one use per press, and the "locked" sound is not repeated every frame
				tryOpen(t);
			}
		}
	}

	public void onDestroy() {
		contacts.clear();
	}
}
