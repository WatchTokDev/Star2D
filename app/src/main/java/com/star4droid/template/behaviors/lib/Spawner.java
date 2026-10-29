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

@BehaviorInfo(name = "Spawner", icon = "portal", category = "Combat", targets = BehaviorInfo.Target.BOTH,
		events = {"onSpawn(PlayerItem spawned)", "onWaveStart(int wave)", "onWaveDone(int wave)", "onAllWavesDone()"},
		desc = "Creates copies of a template element while the game runs: enemies, coins, rocks. Every few seconds or in waves, at the spawner, inside its area, on a ring around it or at spawn points, with a limit of how many live at once.")
public class Spawner extends Behavior {
	public enum SpawnArea { AT_SPAWNER, INSIDE_SELF, RING, POINTS }

	@Header("What")
	@Param(desc = "The element to copy. It is hidden when the game starts; every copy keeps its behaviors, events and properties.")
	public PlayerItem template;
	@Param(desc = "More elements to copy: each spawn picks one of all the templates at random.")
	public List<PlayerItem> moreTemplates = new ArrayList<PlayerItem>();
	@Param(desc = "Every copy removes itself after this long (0 = never).", unit = "s", min = 0, max = 3600, step = 0.5)
	public float spawnedLifetime = 0;

	@Header("When")
	@Param(desc = "Start spawning when the scene starts. Off: call start spawning.")
	public boolean autoStart = true;
	@Param(desc = "Wait this long before the first spawn.", unit = "s", min = 0, max = 600, step = 0.5)
	public float firstDelay = 0;
	@Param(desc = "Seconds between two spawns (in waves: between two members of one wave).", unit = "s", min = 0.05, max = 600, step = 0.05)
	public float interval = 2;
	@Param(desc = "No new copy while this many are alive. 0 = no limit (the engine stops at 300).", min = 0, max = 300)
	public int maxAlive = 10;
	@Param(desc = "Stop for good after this many spawns. 0 = never.", min = 0, max = 100000)
	public int totalLimit = 0;
	@Param(desc = "Each spawn (each wave in wave mode) makes the interval this much shorter.", unit = "%", min = 0, max = 50, step = 1)
	public float speedUp = 0;
	@Param(desc = "The interval never gets shorter than this.", unit = "s", min = 0.05, max = 60, step = 0.05)
	public float minInterval = 0.3f;

	@Header("Where")
	@Param(desc = "AT_SPAWNER: on the spawner. INSIDE_SELF: anywhere in its box. RING: around it. POINTS: at the marker elements below.")
	public SpawnArea area = SpawnArea.AT_SPAWNER;
	@Param(desc = "RING: nearest distance from the spawner.", unit = "px", min = 0, max = 10000)
	public float radiusMin = 0;
	@Param(desc = "RING: farthest distance from the spawner.", unit = "px", min = 0, max = 10000)
	public float radiusMax = 300;
	@Param(desc = "POINTS: elements that mark where copies appear.")
	public List<PlayerItem> points = new ArrayList<PlayerItem>();
	@Param(desc = "POINTS: pick a random point. Off: go through them in order.")
	public boolean randomPoint = true;

	@Header("Waves")
	@Param(desc = "Spawn in waves: a group, a pause, a bigger group...")
	public boolean useWaves = false;
	@Param(desc = "Members of the first wave.", min = 1, max = 300)
	public int waveSize = 5;
	@Param(desc = "Every wave has this many members more than the one before.", min = 0, max = 100)
	public int waveGrowth = 2;
	@Param(desc = "Number of waves. 0 = endless.", min = 0, max = 1000)
	public int waveCount = 3;
	@Param(desc = "Pause between two waves.", unit = "s", min = 0, max = 600, step = 0.5)
	public float waveDelay = 4;
	@Param(desc = "A wave is over (and the next may start) only when all its members are gone.")
	public boolean waitUntilCleared = true;
	@Param(desc = "Start again with the first wave after the last one.")
	public boolean loopWaves = false;
	@Sound
	@Param(desc = "Played at every spawn.")
	public String spawnSound = "";

	private final ArrayList<PlayerItem> living = new ArrayList<PlayerItem>();
	private final ArrayList<GameplaySupport.Template> templates = new ArrayList<GameplaySupport.Template>();
	private boolean running, finished, inWave, allDone;
	private float timer, gap, factor = 1;
	private int spawned, waveNo, toSpawn, pointIdx, pendingNow;
	private float pendingX, pendingY;
	private boolean pendingAt, clearPending;

	public void onStart() {
		hideTemplate(template);
		for (int i = 0; i < moreTemplates.size(); i++) hideTemplate(moreTemplates.get(i));
		restart();
		running = autoStart;
	}

	private void hideTemplate(PlayerItem t) {
		if (t == null || !alive(t) || templateOf(t) != null) return;
		templates.add(new GameplaySupport.Template(t, this));
	}

	private GameplaySupport.Template templateOf(PlayerItem t) {
		for (int i = 0; i < templates.size(); i++) if (templates.get(i).item() == t) return templates.get(i);
		return null;
	}

	private void restart() {
		timer = firstDelay;
		gap = firstDelay;
		factor = 1;
		spawned = 0;
		waveNo = 0;
		inWave = false;
		finished = false;
		allDone = false;
	}

	// ------------------------------------------------------------------ blocks

	@Block(label = "start spawning", desc = "Starts (or continues) spawning.", labelAr = "ابدأ التوليد", descAr = "يبدأ توليد النسخ (أو يتابع).", category = "Spawner")
	public void start() {
		running = true;
	}

	@Block(label = "stop spawning", desc = "Pauses spawning. The copies that exist stay.", labelAr = "أوقف التوليد", descAr = "يوقف التوليد مؤقتاً. النسخ الموجودة تبقى.", category = "Spawner")
	public void stop() {
		running = false;
	}

	@Block(label = "restart spawner", desc = "Counts from zero again (spawns, waves, speed-up) and starts.", labelAr = "أعد المولّد", descAr = "يبدأ العد من الصفر (التوليد، الموجات، التسريع) ويشغّله.", category = "Spawner")
	public void restartSpawner() {
		restart();
		running = true;
	}

	@Block(label = "spawn now", desc = "Spawns one copy right away, ignoring the interval and the alive limit.", labelAr = "ولّد الآن", descAr = "يولّد نسخة واحدة فوراً دون اعتبار للفاصل أو الحد.", category = "Spawner")
	public void spawnNow() {
		pendingNow++;
	}

	@Block(label = "spawn at", desc = "Spawns one copy with its center at this position (px).", labelAr = "ولّد عند", descAr = "يولّد نسخة مركزها عند هذا الموضع (بكسل).", category = "Spawner", args = {"x", "y"})
	public void spawnAt(float x, float y) {
		pendingAt = true;
		pendingX = x;
		pendingY = y;
	}

	@Block(label = "next wave now", desc = "Skips the pause and starts the next wave.", labelAr = "الموجة التالية الآن", descAr = "يتجاوز الاستراحة ويبدأ الموجة التالية.", category = "Spawner")
	public void nextWave() {
		if (!inWave) gap = 0;
	}

	@Block(label = "remove all spawned", desc = "Removes every copy this spawner made.", labelAr = "أزل كل النسخ", descAr = "يزيل كل النسخ التي ولّدها هذا المولّد.", category = "Spawner")
	public void clearSpawned() {
		clearPending = true;
	}

	@Block(label = "set spawn interval", desc = "Seconds between two spawns.", labelAr = "اضبط فاصل التوليد", descAr = "الثواني بين كل توليدين.", category = "Spawner", args = {"seconds"})
	public void setInterval(float seconds) {
		interval = Math.max(0.05f, seconds);
	}

	@Block(label = "set max alive", desc = "No new copy while this many are alive (0 = no limit).", labelAr = "اضبط الحد الأقصى الحي", descAr = "لا نسخ جديدة طالما هذا العدد حيّ (0 = بلا حد).", category = "Spawner", args = {"count"})
	public void setMaxAlive(int count) {
		maxAlive = Math.max(0, count);
	}

	@Block(label = "alive count", desc = "How many copies of this spawner are alive.", labelAr = "عدد النسخ الحية", descAr = "عدد نسخ هذا المولّد الحية الآن.", category = "Spawner")
	public int getAliveCount() {
		prune();
		return living.size();
	}

	@Block(label = "spawned count", desc = "How many copies were made so far.", labelAr = "عدد ما ولّد", descAr = "عدد النسخ التي تم توليدها حتى الآن.", category = "Spawner")
	public int getSpawnedCount() {
		return spawned;
	}

	@Block(label = "current wave", desc = "Number of the wave in progress (1 = first), 0 before the first.", labelAr = "الموجة الحالية", descAr = "رقم الموجة الجارية (1 = الأولى)، 0 قبل الأولى.", category = "Spawner")
	public int getWave() {
		return inWave ? waveNo + 1 : waveNo;
	}

	@Block(label = "is spawning", desc = "True while the spawner is running.", labelAr = "يولّد", descAr = "صحيح طالما المولّد يعمل.", category = "Spawner")
	public boolean isSpawning() {
		return running && !finished;
	}

	// ------------------------------------------------------------------ spawning

	private void prune() {
		for (int i = living.size() - 1; i >= 0; i--) if (!alive(living.get(i))) living.remove(i);
	}

	private PlayerItem pickTemplate() {
		int n = (template != null ? 1 : 0) + moreTemplates.size();
		if (n == 0) return null;
		int k = n == 1 ? 0 : (int) (Math.random() * n);
		PlayerItem t = null;
		if (template != null) {
			if (k == 0) t = template;
			k--;
		}
		if (t == null && k >= 0 && k < moreTemplates.size()) t = moreTemplates.get(k);
		if (t == null) t = template != null ? template : moreTemplates.get(0);
		return alive(t) ? t : null;
	}

	private PlayerItem spawn(boolean useXY, float xx, float yy) {
		PlayerItem t = pickTemplate();
		if (t == null) return null;
		float x = centerX(), y = centerY();
		if (useXY) {
			x = xx;
			y = yy;
		} else if (area == SpawnArea.INSIDE_SELF) {
			x += (float) (Math.random() - 0.5) * widthPx();
			y += (float) (Math.random() - 0.5) * heightPx();
		} else if (area == SpawnArea.RING) {
			double a = Math.random() * Math.PI * 2;
			float r = radiusMin + (float) Math.random() * Math.max(0f, radiusMax - radiusMin);
			x += (float) Math.cos(a) * r;
			y += (float) Math.sin(a) * r;
		} else if (area == SpawnArea.POINTS && !points.isEmpty()) {
			PlayerItem p = null;
			for (int tries = 0; tries < points.size() && !alive(p); tries++) {
				p = randomPoint ? points.get((int) (Math.random() * points.size())) : points.get(pointIdx++ % points.size());
			}
			if (alive(p)) {
				x = cx(p);
				y = cy(p);
			}
		}
		GameplaySupport.Template tp = templateOf(t);
		PlayerItem c = tp != null ? tp.spawn(x, y) : GameplaySupport.spawnClone(t, x, y);
		if (c == null) return null;
		if (t == item && c.hasBehavior("Spawner")) c.removeBehavior("Spawner"); // a copy of the spawner itself must not spawn
		if (spawnedLifetime > 0) {
			Lifetime l = new Lifetime();
			l.seconds = spawnedLifetime;
			l.fadeTime = Math.min(0.5f, spawnedLifetime * 0.3f);
			c.addBehavior(l);
		}
		living.add(c);
		spawned++;
		GameplaySupport.sound(stage, spawnSound);
		fire("onSpawn", c);
		return c;
	}

	private boolean canSpawn() {
		int cap = maxAlive > 0 ? maxAlive : 300;
		return living.size() < cap && (totalLimit <= 0 || spawned < totalLimit);
	}

	private float currentInterval() {
		return Math.max(minInterval, interval * factor);
	}

	private void speedUpStep() {
		if (speedUp > 0) factor *= 1f - speedUp / 100f;
	}

	public void onUpdate(float dt) {
		if (clearPending) {
			clearPending = false;
			for (int i = 0; i < living.size(); i++) GameplaySupport.remove(living.get(i));
			living.clear();
		}
		prune();
		while (pendingNow > 0) {
			pendingNow--;
			spawn(false, 0, 0);
		}
		if (pendingAt) {
			pendingAt = false;
			spawn(true, pendingX, pendingY);
		}
		if (!running || finished) return;
		if (totalLimit > 0 && spawned >= totalLimit) {
			finished = true;
			return;
		}
		if (!useWaves) {
			timer -= dt;
			if (timer <= 0) {
				if (canSpawn()) {
					spawn(false, 0, 0);
					timer = currentInterval();
					speedUpStep();
				} else {
					timer = 0; // blocked by the alive limit: spawn as soon as there is room
				}
			}
			return;
		}
		updateWaves(dt);
	}

	private void updateWaves(float dt) {
		if (!inWave) {
			if (allDone) return;
			gap -= dt;
			if (gap <= 0 && (!waitUntilCleared || living.isEmpty())) {
				inWave = true;
				toSpawn = waveSize + waveGrowth * waveNo;
				timer = 0;
				fire("onWaveStart", waveNo + 1);
			}
			return;
		}
		if (toSpawn > 0) {
			timer -= dt;
			if (timer <= 0 && canSpawn()) {
				spawn(false, 0, 0);
				toSpawn--;
				timer = currentInterval();
			}
			return;
		}
		if (!waitUntilCleared || living.isEmpty()) {
			inWave = false;
			waveNo++;
			gap = waveDelay;
			speedUpStep();
			fire("onWaveDone", waveNo);
			if (waveCount > 0 && waveNo >= waveCount) {
				fire("onAllWavesDone");
				if (loopWaves) {
					waveNo = 0;
					factor = 1;
				} else {
					allDone = true;
				}
			}
		}
	}

	public void onDestroy() {
		for (int i = 0; i < templates.size(); i++) templates.get(i).restore();
		living.clear();
	}
}
