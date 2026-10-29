package com.star4droid.template.behaviors.lib;

import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.BehaviorInfo;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.GameplaySupport;
import com.star4droid.template.behaviors.Header;
import com.star4droid.template.behaviors.Param;
import com.star4droid.template.behaviors.Sound;

import java.util.ArrayList;
import java.util.List;

@BehaviorInfo(name = "Goal", icon = "trophy", category = "Gameplay", targets = BehaviorInfo.Target.PHYSICS,
		events = {"onWin(PlayerItem winner)", "onBlocked(int missing)"},
		desc = "The finish line / win condition: the level is won when the player reaches it AND the conditions are met (all coins of a group collected, a score reached, all enemies gone). Shows a win panel and can open the next scene.")
public class Goal extends Behavior {
	@Header("Who wins")
	@Param(desc = "Elements that win by touching it.")
	public List<PlayerItem> targets = new ArrayList<PlayerItem>();
	@Param(desc = "Also elements whose name starts with one of these words (separate with commas).")
	public String targetPrefix = "player";
	@Param(desc = "Off: no touch is needed, the level is won as soon as the conditions below are met (e.g. collect all stars).")
	public boolean needTouch = true;

	@Header("Conditions")
	@Param(desc = "Group name of Collectibles that must ALL be collected. Empty = no condition.")
	public String collectGroup = "";
	@Param(desc = "A scene variable that must reach the number below, e.g. score or keys. Empty = no condition.")
	public String needVariable = "";
	@Param(desc = "The needed amount of the variable.", min = -1000000, max = 1000000)
	public float needAtLeast = 1;
	@Param(desc = "Elements that must all be destroyed first (bosses, enemies).")
	public List<PlayerItem> mustBeGone = new ArrayList<PlayerItem>();

	@Header("When won")
	@Param(desc = "An element to show (a 'You win' panel). It is hidden when the game starts.")
	public PlayerItem winPanel;
	@Param(desc = "Scene to open after the delay (the next level). Empty = stay.")
	public String nextScene = "";
	@Param(desc = "Wait this long before opening the scene.", unit = "s", min = 0, max = 30, step = 0.1)
	public float delay = 1;
	@Param(desc = "Forget the checkpoints when the level is won (so the next play starts at the beginning).")
	public boolean clearCheckpoints = true;
	@Sound
	@Param(desc = "Played when the level is won.")
	public String winSound = "";

	private final GameplaySupport.NameMatcher matcher = new GameplaySupport.NameMatcher();
	private final GameplaySupport.Contacts contacts = new GameplaySupport.Contacts();
	private final GameplaySupport.Var var = new GameplaySupport.Var();
	private GameplaySupport.Hider panelHider;
	private boolean won, opened, forceWin;
	private float wait;
	private PlayerItem winner;

	public void onStart() {
		if (winPanel != null && alive(winPanel) && winPanel != item) {
			panelHider = new GameplaySupport.Hider();
			panelHider.hide(winPanel);
		}
	}

	@Block(label = "win now", desc = "Wins the level right away, whatever the conditions say.", labelAr = "افز الآن", descAr = "يفوز بالمستوى فوراً مهما كانت الشروط.", category = "Goal")
	public void winNow() {
		forceWin = true;
	}

	@Block(label = "reset goal", desc = "The level is not won any more (for retries).", labelAr = "أعد الهدف", descAr = "لم يعد المستوى مفوزاً به (للمحاولة من جديد).", category = "Goal")
	public void resetGoal() {
		won = false;
		opened = false;
		if (panelHider != null) panelHider.hide(winPanel);
	}

	@Block(label = "is won", desc = "True once the level was won.", labelAr = "تم الفوز", descAr = "صحيح بعد الفوز بالمستوى.", category = "Goal")
	public boolean isWon() {
		return won;
	}

	@Block(label = "conditions met", desc = "True when every condition (not the touch) is fulfilled.", labelAr = "الشروط متحققة", descAr = "صحيح عند تحقق كل الشروط (دون اللمس).", category = "Goal")
	public boolean conditionsMet() {
		return missing() == 0;
	}

	@Block(label = "set next scene", desc = "The scene to open after winning.", labelAr = "اضبط المشهد التالي", descAr = "المشهد الذي يفتح بعد الفوز.", category = "Goal", args = {"scene"})
	public void setNextScene(String name) {
		nextScene = name == null ? "" : name;
	}

	/** Number of conditions that are not met yet. */
	private int missing() {
		int m = 0;
		if (collectGroup.length() > 0) {
			int total = GameplaySupport.collectiblesTotal(stage, collectGroup);
			if (total == 0 || GameplaySupport.collectiblesTaken(stage, collectGroup) < total) m++;
		}
		if (needVariable.length() > 0 && var.bind(stage, needVariable).get() < needAtLeast) m++;
		for (int i = 0; i < mustBeGone.size(); i++) {
			if (alive(mustBeGone.get(i))) {
				m++;
				break;
			}
		}
		return m;
	}

	private void win(PlayerItem by) {
		if (won) return;
		won = true;
		winner = by;
		GameplaySupport.sound(stage, winSound);
		if (panelHider != null) panelHider.show(winPanel);
		if (clearCheckpoints) GameplaySupport.clearCheckpoint(stage, "");
		fire("onWin", by);
		wait = delay;
	}

	public void onCollisionBegin(PlayerItem other) {
		matcher.set(targetPrefix);
		if (GameplaySupport.isTarget(other, targets, matcher)) contacts.begin(other);
	}

	public void onCollisionEnd(PlayerItem other) {
		contacts.end(other);
	}

	public void onUpdate(float dt) {
		if (won) {
			if (!opened && nextScene.length() > 0) {
				wait -= dt;
				if (wait <= 0) {
					opened = true;
					stage.openScene(nextScene);
				}
			}
			return;
		}
		if (forceWin) {
			forceWin = false;
			win(null);
			return;
		}
		if (!needTouch) {
			if (missing() == 0) win(null);
			return;
		}
		contacts.prune();
		contacts.poll(item, targets);
		for (int i = 0; i < contacts.size() && !won; i++) {
			if (!contacts.live(i)) continue;
			PlayerItem t = contacts.item(i);
			float cool = contacts.timer(i) - dt;
			if (contacts.takeFresh(i)) cool = 0;
			if (cool <= 0) {
				int m = missing();
				if (m == 0) {
					win(t);
				} else {
					fire("onBlocked", m);
					cool = 1.5f; // tell again only after a moment while the player stays on the goal
				}
			}
			contacts.setTimer(i, cool);
		}
	}

	public void onDestroy() {
		contacts.clear();
	}
}
