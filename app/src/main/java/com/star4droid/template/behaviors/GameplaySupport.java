package com.star4droid.template.behaviors;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.lib.Health;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Shared machinery of the gameplay behaviors (Health, DamageOnTouch, Spawner, CameraFollow...). It is NOT a behavior: it
 * lives next to the framework so that every behavior of lib/ can use it, and a project's own behaviors may call it too.
 *
 * Contents: scene variables by name, name matchers, contact tracking, damage dispatch, hide/show, checkpoints,
 * collectible counters, the shared camera rig and a few small helpers. Pure libGDX / Box2D, no android.* imports.
 */
public final class GameplaySupport {
	private GameplaySupport() {}

	static final float W = Behavior.W;

	// ------------------------------------------------------------------ names

	/**
	 * "enemy, boss;trap" -> matches the element names that START with one of the words (case-insensitive). Create one per
	 * behavior and call {@link #set} with the (possibly changed) parameter every time: it parses only when the text changed.
	 */
	public static final class NameMatcher {
		private String src = "";
		private String[] parts = new String[0];

		public NameMatcher set(String spec) {
			if (spec == null) spec = "";
			if (spec == src || spec.equals(src)) return this;
			src = spec;
			ArrayList<String> out = new ArrayList<String>();
			int start = -1;
			for (int i = 0; i <= spec.length(); i++) {
				char c = i < spec.length() ? spec.charAt(i) : ',';
				boolean sep = c == ',' || c == ';' || c == ' ' || c == '|' || c == '\n';
				if (!sep && start < 0) start = i;
				if (sep && start >= 0) {
					out.add(spec.substring(start, i).toLowerCase());
					start = -1;
				}
			}
			parts = out.toArray(new String[out.size()]);
			return this;
		}

		public boolean isEmpty() {
			return parts.length == 0;
		}

		public boolean matches(String name) {
			if (name == null) return false;
			for (int i = 0; i < parts.length; i++) {
				String p = parts[i];
				if (p.equals("*") || name.regionMatches(true, 0, p, 0, p.length())) return true;
			}
			return false;
		}
	}

	/** Fills out with the elements of the game whose name matches (the scene is walked once per call: use it every few tenths of a second, not every frame). */
	public static void scan(StageImp s, NameMatcher m, PlayerItem self, ArrayList<PlayerItem> out) {
		out.clear();
		if (s == null || m.isEmpty() || s.getGameStage() == null) return;
		com.badlogic.gdx.utils.Array<Actor> as = s.getGameStage().getActors();
		Actor me = self == null ? null : self.getActor();
		for (int i = 0; i < as.size; i++) {
			Actor a = as.get(i);
			if (a == me || !(a instanceof PlayerItem)) continue;
			String n = a.getName();
			if (n != null && m.matches(n)) out.add((PlayerItem) a);
		}
	}

	/** In the explicit list, or the name matches the prefixes. */
	public static boolean isTarget(PlayerItem it, List<PlayerItem> list, NameMatcher m) {
		if (it == null) return false;
		if (list != null && list.contains(it)) return true;
		return !m.isEmpty() && m.matches(it.getName());
	}

	/** True when neither a list nor a prefix is given. */
	public static boolean noFilter(List<PlayerItem> list, NameMatcher m) {
		return (list == null || list.isEmpty()) && m.isEmpty();
	}

	// ------------------------------------------------------------------ scene variables

	private static final WeakHashMap<StageImp, HashMap<String, Double>> STORE = new WeakHashMap<StageImp, HashMap<String, Double>>();

	private static HashMap<String, Double> store(StageImp s) {
		HashMap<String, Double> m = STORE.get(s);
		if (m == null) {
			m = new HashMap<String, Double>();
			STORE.put(s, m);
		}
		return m;
	}

	/**
	 * A number of the scene by name: the scene variable (a field of the generated scene class: int, long, float, double,
	 * boolean or String holding a number) when it exists, otherwise a hidden per-scene store, so a behavior also works
	 * before the variable is declared.
	 */
	public static final class Var {
		private String name = "";
		private Field field;
		private StageImp st;
		private boolean searched;

		public Var bind(StageImp stage, String n) {
			if (n == null) n = "";
			if (searched && stage == st && n.equals(name)) return this;
			searched = true;
			st = stage;
			name = n;
			field = null;
			if (stage == null || n.length() == 0) return this;
			Class<?> c = stage.getClass();
			while (c != null && c != Object.class) {
				try {
					Field f = c.getDeclaredField(n);
					Class<?> t = f.getType();
					if (t == int.class || t == long.class || t == float.class || t == double.class || t == short.class || t == boolean.class || t == String.class) {
						f.setAccessible(true);
						field = f;
					}
					break;
				} catch (NoSuchFieldException e) {
					c = c.getSuperclass();
				} catch (Throwable e) {
					break;
				}
			}
			return this;
		}

		public boolean isBound() {
			return name.length() > 0;
		}

		public boolean isSceneVariable() {
			return field != null;
		}

		public double get() {
			if (st == null || name.length() == 0) return 0;
			if (field != null) {
				try {
					Class<?> t = field.getType();
					if (t == int.class) return field.getInt(st);
					if (t == long.class) return field.getLong(st);
					if (t == float.class) return field.getFloat(st);
					if (t == double.class) return field.getDouble(st);
					if (t == short.class) return field.getShort(st);
					if (t == boolean.class) return field.getBoolean(st) ? 1 : 0;
					Object o = field.get(st);
					return o == null ? 0 : Double.parseDouble(o.toString().trim());
				} catch (Throwable e) {
					return 0;
				}
			}
			Double d = store(st).get(name);
			return d == null ? 0 : d.doubleValue();
		}

		public void set(double v) {
			if (st == null || name.length() == 0) return;
			if (field != null) {
				try {
					Class<?> t = field.getType();
					if (t == int.class) field.setInt(st, (int) Math.round(v));
					else if (t == long.class) field.setLong(st, Math.round(v));
					else if (t == float.class) field.setFloat(st, (float) v);
					else if (t == double.class) field.setDouble(st, v);
					else if (t == short.class) field.setShort(st, (short) Math.round(v));
					else if (t == boolean.class) field.setBoolean(st, v != 0);
					else field.set(st, v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v));
				} catch (Throwable e) {
				}
				return;
			}
			store(st).put(name, v);
		}

		public void add(double d) {
			set(get() + d);
		}
	}

	// ------------------------------------------------------------------ number / time text (no String.format: runs on the game thread)

	/** 1234.5 -> "1,234.50" (decimals 2, thousands true). pad = minimum digits before the point (zeros). */
	public static String number(double v, int decimals, int pad, boolean thousands) {
		boolean neg = v < 0;
		if (neg) v = -v;
		double scale = 1;
		for (int i = 0; i < decimals; i++) scale *= 10;
		long scaled = Math.round(v * scale);
		long whole = (long) (scaled / scale);
		long frac = scaled - (long) (whole * scale);
		StringBuilder sb = new StringBuilder(24);
		String w = Long.toString(whole);
		for (int i = w.length(); i < pad; i++) sb.append('0');
		if (thousands && sb.length() == 0) {
			int first = w.length() % 3;
			if (first > 0) sb.append(w, 0, first);
			for (int i = first; i < w.length(); i += 3) {
				if (sb.length() > 0) sb.append(',');
				sb.append(w, i, i + 3);
			}
		} else {
			sb.append(w);
		}
		if (decimals > 0) {
			sb.append('.');
			String f = Long.toString(frac);
			for (int i = f.length(); i < decimals; i++) sb.append('0');
			sb.append(f);
		}
		return neg && scaled != 0 ? "-" + sb : sb.toString();
	}

	/** style 0 = m:ss, 1 = seconds, 2 = seconds with tenths, 3 = mm:ss.cc. Countdowns round up (0:01 until it is over), stopwatches down. */
	public static String time(float seconds, int style, boolean roundDown) {
		if (seconds < 0) seconds = 0;
		switch (style) {
			case 1:
				return Integer.toString(units(seconds, 1, roundDown));
			case 2: {
				int t = units(seconds, 10, roundDown);
				return (t / 10) + "." + (t % 10);
			}
			case 3: {
				int c = (int) (seconds * 100 + 1e-3);
				int s = c / 100, m = s / 60;
				return two(m) + ":" + two(s % 60) + "." + two(c % 100);
			}
			default: {
				int s = units(seconds, 1, roundDown);
				return (s / 60) + ":" + two(s % 60);
			}
		}
	}

	/** Key that changes exactly when {@link #time} would print something else (so the text is rewritten only then). */
	public static int timeKey(float seconds, int style, boolean roundDown) {
		if (seconds < 0) seconds = 0;
		switch (style) {
			case 2:
				return units(seconds, 10, roundDown);
			case 3:
				return (int) (seconds * 100 + 1e-3);
			default:
				return units(seconds, 1, roundDown);
		}
	}

	private static int units(float seconds, int perSecond, boolean roundDown) {
		double v = seconds * perSecond;
		return roundDown ? (int) Math.floor(v + 1e-3) : (int) Math.ceil(v - 1e-3);
	}

	private static String two(int v) {
		return v < 10 ? "0" + v : Integer.toString(v);
	}

	// ------------------------------------------------------------------ geometry / movement helpers for ANY element

	/** Moves ANY element so its center is at (x, y) pixels (Behavior.setCenter only moves the behavior's own element). */
	public static void moveCenter(PlayerItem it, float x, float y) {
		Body b = it.getBody();
		if (b != null) {
			b.setTransform(x * W, y * W, b.getAngle());
			b.setAwake(true);
		} else {
			Actor a = it.getActor();
			a.setPosition(x - a.getWidth() * 0.5f, y - a.getHeight() * 0.5f);
		}
	}

	/** Linear velocity in px/s of a physical element (y up); 0 for UI. */
	public static float velX(StageImp stage, PlayerItem it) {
		Body b = it.getBody();
		return b == null ? 0 : b.getLinearVelocity().x / (W / Math.max(1, stage.getSteps()));
	}

	public static float velY(StageImp stage, PlayerItem it) {
		Body b = it.getBody();
		return b == null ? 0 : b.getLinearVelocity().y / (W / Math.max(1, stage.getSteps()));
	}

	/** Sets the velocity (px/s) of a physical, non static element. */
	public static void setVel(StageImp stage, PlayerItem it, float vx, float vy) {
		Body b = it.getBody();
		if (b == null || b.getType() == BodyDef.BodyType.StaticBody) return;
		float k = W / Math.max(1, stage.getSteps());
		b.setLinearVelocity(vx * k, vy * k);
		b.setAwake(true);
	}

	private static int ids;

	/** A number that is different every call (names of clones / bullets). */
	public static int nextId() {
		return ++ids;
	}

	/** Cheap clock in seconds (for cooldowns shared between behaviors). */
	public static double now() {
		return System.nanoTime() * 1e-9;
	}

	/** Plays a project sound, never throws (a missing file must not crash the game). */
	public static void sound(StageImp stage, String name) {
		if (stage == null || name == null || name.length() == 0) return;
		try {
			stage.startSound(name);
		} catch (Throwable ignored) {
		}
	}

	/** Removes an element from the game once (safe to call twice, safe on null). */
	public static void remove(PlayerItem it) {
		if (it != null && Behavior.alive(it)) {
			try {
				it.destroy();
			} catch (Throwable ignored) {
			}
		}
	}

	/**
	 * Copy of an element placed with its center at (x, y) px, visible, active and at rest, with the same behaviors and
	 * events as the original (see PlayerItem.getClone). Names start with the template's name so name prefixes keep working.
	 * Returns null when the element cannot be cloned. Never call it from a collision callback.
	 */
	public static PlayerItem spawnClone(PlayerItem template, float x, float y) {
		if (template == null || !Behavior.alive(template)) return null;
		try {
			PlayerItem c = template.getClone(template.getName() + "_c" + nextId());
			if (c == null) return null;
			c.getActor().setVisible(true);
			Body b = c.getBody();
			if (b != null) {
				b.setActive(true);
				b.setLinearVelocity(0, 0);
				b.setAngularVelocity(0);
			}
			moveCenter(c, x, y);
			return c;
		} catch (Throwable e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ hiding without freezing (invisible actors are not updated by the engine)

	/**
	 * Hides an element by alpha 0 + inactive body + no touches, and brings it back. The actor stays "visible" for the
	 * engine on purpose: an invisible actor is skipped by the draw loop, so its behaviors would stop (a hidden collectible
	 * could not respawn, a hidden spawner template could not spawn).
	 */
	public static final class Hider {
		private boolean hidden;
		private float alpha = 1;
		private Touchable touch = Touchable.enabled;
		private boolean wasActive = true;

		public boolean isHidden() {
			return hidden;
		}

		public void hide(PlayerItem it) {
			if (hidden || !Behavior.alive(it)) return;
			Actor a = it.getActor();
			alpha = a.getColor().a;
			touch = a.getTouchable();
			a.getColor().a = 0f;
			a.setTouchable(Touchable.disabled);
			Body b = it.getBody();
			if (b != null) {
				wasActive = b.isActive();
				b.setActive(false);
			}
			hidden = true;
		}

		public void show(PlayerItem it) {
			if (!hidden || !Behavior.alive(it)) {
				hidden = false;
				return;
			}
			Actor a = it.getActor();
			a.getColor().a = alpha;
			a.setTouchable(touch);
			Body b = it.getBody();
			if (b != null) b.setActive(wasActive);
			hidden = false;
		}
	}

	/**
	 * An element used as a pattern to copy (Spawner template, Destructible drop). It is hidden and its behaviors are
	 * switched off (a pattern bullet must not fly away, a pattern with a Lifetime must not expire), and {@link #spawn}
	 * makes copies that have the behaviors in their original on/off state.
	 */
	public static final class Template {
		private final PlayerItem item;
		private final Hider hider = new Hider();
		private final ArrayList<Behavior> bs = new ArrayList<Behavior>();
		private boolean[] prev = new boolean[0];

		public Template(PlayerItem it, Behavior except) {
			item = it;
			ItemScript sc = it.getScript();
			ArrayList<Behavior> all = sc == null ? null : sc.getBehaviors();
			if (all != null) {
				for (int i = 0; i < all.size(); i++) if (all.get(i) != except) bs.add(all.get(i));
				prev = new boolean[bs.size()];
				for (int i = 0; i < bs.size(); i++) prev[i] = bs.get(i).enabled;
				for (int i = 0; i < bs.size(); i++) bs.get(i).setEnabled(false);
			}
			hider.hide(it);
		}

		public PlayerItem item() {
			return item;
		}

		public PlayerItem spawn(float x, float y) {
			for (int i = 0; i < bs.size(); i++) bs.get(i).enabled = prev[i];
			PlayerItem c = spawnClone(item, x, y);
			for (int i = 0; i < bs.size(); i++) bs.get(i).enabled = false;
			return c;
		}

		/** Gives the original back (visible, behaviors on again). */
		public void restore() {
			hider.show(item);
			for (int i = 0; i < bs.size(); i++) bs.get(i).setEnabled(prev[i]);
		}
	}

	// ------------------------------------------------------------------ contacts

	/**
	 * Who touches an element right now. Filled by contact callbacks (physics contacts) AND by polling the boxes of an
	 * explicit list (Box2D gives no contact between two non dynamic bodies, e.g. a kinematic bullet and a static wall).
	 * Never act inside the contact callbacks (the physics world is locked there): record with {@link #begin}/{@link #end}
	 * and read in onUpdate.
	 */
	public static final class Contacts {
		private static final class E {
			PlayerItem it;
			int n;
			float timer;
			boolean over, fresh, gone;
		}

		private final ArrayList<E> list = new ArrayList<E>();

		private E find(PlayerItem o) {
			for (int i = 0; i < list.size(); i++) if (list.get(i).it == o) return list.get(i);
			return null;
		}

		public void begin(PlayerItem o) {
			if (o == null) return;
			E e = find(o);
			if (e == null) {
				e = new E();
				e.it = o;
				e.fresh = true;
				list.add(e);
			} else if (e.gone) { // left and came back before the next frame: a new touch
				e.gone = false;
				e.n = 0;
				e.over = false;
				e.fresh = true;
				e.timer = 0;
			}
			e.n++;
		}

		/**
		 * A contact ended. The entry is only marked: ending a contact can happen while a behavior walks the list (it destroys
		 * or hides the other element, which ends its contacts at once), so removal waits for {@link #prune} at the next frame.
		 */
		public void end(PlayerItem o) {
			E e = find(o);
			if (e == null) return;
			if (e.n > 0) e.n--;
			if (e.n <= 0 && !e.over) e.gone = true;
		}

		/** Adds/removes the members of the list whose boxes overlap self. */
		public void poll(PlayerItem self, List<PlayerItem> candidates) {
			if (candidates == null) return;
			for (int i = 0; i < candidates.size(); i++) {
				PlayerItem c = candidates.get(i);
				if (c == null || c == self || !Behavior.alive(c)) continue;
				boolean ov = Behavior.overlaps(self, c);
				E e = find(c);
				if (ov) {
					if (e == null) {
						e = new E();
						e.it = c;
						e.fresh = true;
						list.add(e);
					} else if (e.gone) {
						e.gone = false;
						e.n = 0;
						e.fresh = true;
						e.timer = 0;
					}
					e.over = true;
				} else if (e != null) {
					e.over = false;
					if (e.n <= 0) list.remove(e);
				}
			}
		}

		/** Call at the start of the frame, before reading: forgets the destroyed elements and the contacts that ended. */
		public void prune() {
			for (int i = list.size() - 1; i >= 0; i--) {
				E e = list.get(i);
				if (!Behavior.alive(e.it) || (e.gone && !e.fresh)) list.remove(i); // (began and ended inside one frame: stays one frame, to be read as a fresh touch)
			}
		}

		public void clear() {
			list.clear();
		}

		public int size() {
			return list.size();
		}

		public PlayerItem item(int i) {
			return list.get(i).it;
		}

		/** False for a contact that already ended and was already read. Check it before {@link #takeFresh} in every loop. */
		public boolean live(int i) {
			E e = list.get(i);
			return !e.gone || e.fresh;
		}

		/** True once per contact: the first read after it began. */
		public boolean takeFresh(int i) {
			E e = list.get(i);
			boolean f = e.fresh;
			e.fresh = false;
			return f;
		}

		public boolean contains(PlayerItem o) {
			return find(o) != null;
		}

		/** A free float per contact (cooldowns of damage / use). */
		public float timer(int i) {
			return list.get(i).timer;
		}

		public void setTimer(int i, float v) {
			list.get(i).timer = v;
		}
	}

	// ------------------------------------------------------------------ damage

	public static final int NOT_DAMAGEABLE = 0, IGNORED = 1, HURT = 2;

	/** The first enabled Damageable behavior of an element (Health, Destructible...), or null. */
	public static Damageable damageable(PlayerItem it) {
		if (it == null) return null;
		ItemScript s = it.getScript();
		if (s == null) return null;
		ArrayList<Behavior> bs = s.getBehaviors();
		if (bs == null) return null;
		for (int i = 0; i < bs.size(); i++) {
			Behavior b = bs.get(i);
			if (b instanceof Damageable && b.enabled) return (Damageable) b;
		}
		return null;
	}

	/**
	 * Hurts an element. If it has no Damageable and autoHealth &gt; 0 a Health with that many points is added first
	 * (that is how a plain enemy gets "3 hit points" without configuring anything). Returns NOT_DAMAGEABLE, IGNORED or HURT.
	 */
	public static int hit(PlayerItem target, float amount, PlayerItem source, float pushX, float pushY, float stun, float autoHealth) {
		if (target == null || !Behavior.alive(target)) return NOT_DAMAGEABLE;
		Damageable d = damageable(target);
		if (d == null && autoHealth > 0) {
			Health h = new Health();
			h.maxHealth = autoHealth;
			h.health = autoHealth;
			target.addBehavior(h);
			d = h;
		}
		if (d == null) return NOT_DAMAGEABLE;
		return d.takeDamage(amount, source, pushX, pushY, stun) ? HURT : IGNORED;
	}

	// ------------------------------------------------------------------ checkpoints

	private static final HashMap<String, float[]> CHECKPOINTS = new HashMap<String, float[]>();

	private static String cpKey(StageImp s, String channel) {
		return s.getClass().getName() + "|" + (channel == null ? "" : channel);
	}

	/** Remembers a respawn point (center, px). disk = survive closing the game, otherwise only until the app closes. */
	public static void setCheckpoint(StageImp s, String channel, float x, float y, boolean disk) {
		if (s == null) return;
		String k = cpKey(s, channel);
		CHECKPOINTS.put(k, new float[]{x, y});
		if (disk) {
			try {
				s.setValue("cp:" + k, x + "," + y);
			} catch (Throwable ignored) {
			}
		}
	}

	/** {x, y} or null. */
	public static float[] getCheckpoint(StageImp s, String channel, boolean disk) {
		if (s == null) return null;
		String k = cpKey(s, channel);
		float[] p = CHECKPOINTS.get(k);
		if (p != null) return p;
		if (disk) {
			try {
				String v = s.getValue("cp:" + k);
				int c = v == null ? -1 : v.indexOf(',');
				if (c > 0) {
					p = new float[]{Float.parseFloat(v.substring(0, c)), Float.parseFloat(v.substring(c + 1))};
					CHECKPOINTS.put(k, p);
					return p;
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	public static void clearCheckpoint(StageImp s, String channel) {
		if (s == null) return;
		String k = cpKey(s, channel);
		CHECKPOINTS.remove(k);
		try {
			s.setValue("cp:" + k, "");
		} catch (Throwable ignored) {
		}
	}

	// ------------------------------------------------------------------ collectible counters (Collectible -> Goal)

	private static final WeakHashMap<StageImp, HashMap<String, int[]>> COUNTS = new WeakHashMap<StageImp, HashMap<String, int[]>>();

	private static int[] counts(StageImp s, String group) {
		HashMap<String, int[]> m = COUNTS.get(s);
		if (m == null) {
			m = new HashMap<String, int[]>();
			COUNTS.put(s, m);
		}
		int[] c = m.get(group);
		if (c == null) {
			c = new int[2];
			m.put(group, c);
		}
		return c;
	}

	public static void collectibleAdded(StageImp s, String group) {
		if (s != null) counts(s, group)[0]++;
	}

	public static void collectibleRemoved(StageImp s, String group, boolean wasCollected) {
		if (s == null) return;
		int[] c = counts(s, group);
		if (!wasCollected && c[0] > 0) c[0]--;
	}

	public static void collectibleTaken(StageImp s, String group) {
		if (s != null) counts(s, group)[1]++;
	}

	public static void collectibleUntaken(StageImp s, String group) {
		if (s == null) return;
		int[] c = counts(s, group);
		if (c[1] > 0) c[1]--;
	}

	public static int collectiblesTotal(StageImp s, String group) {
		return s == null ? 0 : counts(s, group)[0];
	}

	public static int collectiblesTaken(StageImp s, String group) {
		return s == null ? 0 : counts(s, group)[1];
	}

	// ------------------------------------------------------------------ teleport lock (doors / portals)

	private static final WeakHashMap<PlayerItem, Double> LOCKS = new WeakHashMap<PlayerItem, Double>();

	/** Marks an element as "just teleported": doors ignore it for a while (no ping-pong between two portals). */
	public static void lock(PlayerItem it, float seconds) {
		LOCKS.put(it, now() + seconds);
	}

	public static boolean locked(PlayerItem it) {
		Double t = LOCKS.get(it);
		return t != null && t.doubleValue() > now();
	}

	// ------------------------------------------------------------------ scenes

	/** Name used by "restart scene": the given one or the class name of the running scene (the engine lower-cases it). */
	public static String sceneName(StageImp s, String given) {
		if (given != null && given.length() > 0) return given;
		return s == null ? "" : s.getClass().getSimpleName();
	}

	// ------------------------------------------------------------------ the camera rig (CameraFollow + CameraShake + Health feedback)

	/**
	 * The camera is shared by CameraFollow (position) and CameraShake (offset on top of it). The rig remembers the offset
	 * it added and the position it left, so both can run in any order and any other code that moves the camera
	 * (setCameraXY, the stage follow...) simply wins for the frame without the shake drifting away.
	 */
	public static final class CameraRig {
		public float offX, offY;      // shake offset (world units) included in the camera position right now
		float lastX, lastY;
		boolean wrote;
		public float trauma;          // 0..1, added by shake(), consumed by CameraShake
		public boolean driven;        // a CameraShake exists in this scene

		/** Camera position without our shake offset (world units). */
		public float baseX(Camera cam) {
			return wrote && Math.abs(cam.position.x - lastX) < 1e-3f ? cam.position.x - offX : cam.position.x;
		}

		public float baseY(Camera cam) {
			return wrote && Math.abs(cam.position.y - lastY) < 1e-3f ? cam.position.y - offY : cam.position.y;
		}

		public void write(Camera cam, float bx, float by, float ox, float oy) {
			offX = ox;
			offY = oy;
			cam.position.x = bx + ox;
			cam.position.y = by + oy;
			lastX = cam.position.x;
			lastY = cam.position.y;
			wrote = true;
		}
	}

	private static final WeakHashMap<StageImp, CameraRig> RIGS = new WeakHashMap<StageImp, CameraRig>();

	public static CameraRig rig(StageImp s) {
		CameraRig r = RIGS.get(s);
		if (r == null) {
			r = new CameraRig();
			RIGS.put(s, r);
		}
		return r;
	}

	/** Adds screen shake (0..1) if the scene has a CameraShake behavior; does nothing otherwise. */
	public static void shake(StageImp s, float trauma) {
		if (s == null || trauma <= 0) return;
		CameraRig r = rig(s);
		if (!r.driven) return;
		r.trauma = Math.min(1f, r.trauma + trauma);
	}

	/** Half of the visible area in editor pixels (width, height) at the current zoom. */
	public static float halfViewW(StageImp s) {
		Camera c = s.getCamera();
		float z = c instanceof OrthographicCamera ? ((OrthographicCamera) c).zoom : 1f;
		return c.viewportWidth * z * 0.5f / W;
	}

	public static float halfViewH(StageImp s) {
		Camera c = s.getCamera();
		float z = c instanceof OrthographicCamera ? ((OrthographicCamera) c).zoom : 1f;
		return c.viewportHeight * z * 0.5f / W;
	}

	/** Smooth, allocation free pseudo noise in -1..1 (sum of sines with unrelated frequencies). */
	public static float noise(float t, float seed) {
		return (float) (Math.sin(t * 1.0 + seed * 12.9898) * 0.5 + Math.sin(t * 2.3 + seed * 78.233) * 0.3 + Math.sin(t * 4.1 + seed * 37.719) * 0.2);
	}
}
