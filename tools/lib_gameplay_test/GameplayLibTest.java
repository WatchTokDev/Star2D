import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.ReflectScanner;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.template.Items.StageImp;
import com.star4droid.star2d.ElementDefs.ElementEvent;
import com.star4droid.template.Utils.ItemScript;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.*;
import com.star4droid.template.behaviors.lib.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Run by tools/test_lib_gameplay.py. Checks the annotations of the gameplay / combat / camera behaviors of
 * behaviors/lib/ (K4 set) with the same metadata readers the editor uses, and runs headless smoke tests of the
 * logic that does not need a physics world (Health, Timer, Lifetime, number/time formatting, scene variables, camera rig).
 */
public class GameplayLibTest {
	static int checks = 0, failures = 0;
	static final String[] IDS = {"Health", "DamageOnTouch", "Destructible", "Projectile", "Spawner", "Collectible", "PowerUp", "Checkpoint", "Respawn",
			"Lifetime", "Timer", "ScoreCounter", "Door", "CameraFollow", "CameraShake", "Turret", "Goal"};
	static final Set<String> EVENT_TYPES = new HashSet<String>(Arrays.asList("PlayerItem", "int", "float", "double", "boolean", "String"));
	static final Set<String> BLOCK_TYPES = new HashSet<String>(Arrays.asList("int", "long", "float", "double", "boolean", "String", "PlayerItem", "Color"));

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			failures++;
			System.out.println("FAIL " + what);
		}
	}

	static String read(File f) throws Exception {
		return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
	}

	public static void main(String[] a) throws Exception {
		String root = a[0];
		Set<String> icons = loadIcons(new File(root, "app/src/main/assets/icons/catalog.json"));
		check(icons.size() > 1000, "icon catalog loaded (" + icons.size() + ")");
		for (String id : IDS) metadata(root, id, icons);
		smokeSupport();
		smokeHealth();
		smokeTimer();
		smokeLifetime();
		smokeRig();
		smokeVar();
		System.out.println(checks + " checks, " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}

	static Set<String> loadIcons(File f) throws Exception {
		Set<String> out = new HashSet<String>();
		JsonObject o = new JsonParser().parse(read(f)).getAsJsonObject();
		JsonArray arr = o.getAsJsonArray("icons");
		for (JsonElement e : arr) out.add(e.getAsJsonObject().get("i").getAsString());
		return out;
	}

	// ------------------------------------------------------------------ annotations

	static void metadata(String root, String id, Set<String> icons) throws Exception {
		Class<?> c;
		try {
			c = Class.forName("com.star4droid.template.behaviors.lib." + id);
		} catch (ClassNotFoundException ex) {
			check(false, id + ": class not found");
			return;
		}
		check(Behavior.class.isAssignableFrom(c), id + ": extends Behavior");
		check(!Modifier.isAbstract(c.getModifiers()) && Modifier.isPublic(c.getModifiers()), id + ": public and concrete");
		BehaviorInfo info = c.getAnnotation(BehaviorInfo.class);
		check(info != null, id + ": @BehaviorInfo");
		if (info == null) return;
		check(info.name().length() > 0, id + ": name");
		check(info.desc().length() >= 40, id + ": description is a real sentence (" + info.desc().length() + " chars)");
		check(icons.contains(info.icon()), id + ": icon '" + info.icon() + "' is in the catalog");
		check(info.category().length() > 0 && !info.category().equals("General"), id + ": category " + info.category());

		File src = new File(root, "app/src/main/java/com/star4droid/template/behaviors/lib/" + id + ".java");
		String text = read(src);

		// public fields
		Object inst = c.newInstance();
		for (Field f : c.getDeclaredFields()) {
			int m = f.getModifiers();
			if (!Modifier.isPublic(m) || Modifier.isStatic(m) || Modifier.isFinal(m) || f.isSynthetic()) continue;
			String fn = id + "." + f.getName();
			if (f.isAnnotationPresent(Hidden.class)) continue;
			Param p = f.getAnnotation(Param.class);
			check(p != null, fn + ": public field has @Param (or @Hidden)");
			if (p == null) continue;
			Class<?> t = f.getType();
			boolean number = t == int.class || t == float.class || t == double.class || t == long.class;
			boolean isList = List.class.isAssignableFrom(t);
			boolean image = f.isAnnotationPresent(Image.class), sound = f.isAnnotationPresent(Sound.class);
			boolean selfExplaining = p.unit().length() > 0 && number;
			check(p.desc().length() > 0 || p.label().length() > 0 && number || selfExplaining || t.isEnum() && f.getName().length() > 2 && p.desc().length() > 0, fn + ": @Param has a description");
			if (number) {
				check(p.min() > -1e8 && p.max() < 1e8, fn + ": min/max given (" + p.min() + ".." + p.max() + ")");
				Object v = f.get(inst);
				double d = ((Number) v).doubleValue();
				check(d >= p.min() && d <= p.max(), fn + ": default " + d + " within " + p.min() + ".." + p.max());
				check(p.step() >= 0, fn + ": step");
			}
			if (t.isEnum()) check(f.get(inst) != null, fn + ": enum has a default");
			if (t == String.class && !image && !sound) check(f.get(inst) != null, fn + ": String default not null");
			if (isList) check(f.get(inst) != null, fn + ": list initialised");
			if (image || sound) check(t == String.class, fn + ": @Image/@Sound on a String");
		}

		// events
		Set<String> declared = new HashSet<String>();
		for (String sig : info.events()) {
			BehaviorMeta.EventSpec e = BehaviorMeta.EventSpec.parse(sig);
			check(e.name.startsWith("on") && e.name.length() > 2, id + ": event name " + sig);
			declared.add(e.name);
			int inner = sig.indexOf('(') < 0 ? 0 : sig.substring(sig.indexOf('(') + 1, sig.lastIndexOf(')')).trim().length() == 0 ? 0 : sig.substring(sig.indexOf('(') + 1, sig.lastIndexOf(')')).split(",").length;
			check(e.args.size() == inner, id + ": event args parse " + sig);
			for (String[] arg : e.args) check(EVENT_TYPES.contains(arg[0]), id + ": event arg type " + arg[0] + " in " + sig);
		}
		// every declared event is fired somewhere, every fired event is declared
		java.util.regex.Matcher fm = java.util.regex.Pattern.compile("fire\\(\"(on\\w+)\"").matcher(text);
		Set<String> fired = new HashSet<String>();
		while (fm.find()) fired.add(fm.group(1));
		for (String d : declared) check(fired.contains(d), id + ": declared event " + d + " is fired");
		for (String f : fired) check(declared.contains(f), id + ": fired event " + f + " is declared");

		// blocks
		Set<String> names = new HashSet<String>();
		int blocks = 0;
		for (Method m : c.getDeclaredMethods()) {
			Block b = m.getAnnotation(Block.class);
			if (b == null) continue;
			blocks++;
			String bn = id + "." + m.getName();
			check(Modifier.isPublic(m.getModifiers()), bn + ": @Block method is public");
			check(names.add(m.getName() + m.getParameterTypes().length), bn + ": unique method name");
			check(b.label().length() > 0 && b.desc().length() > 0, bn + ": label and desc");
			check(b.labelAr().length() > 0 && b.descAr().length() > 0, bn + ": Arabic label and desc");
			check(b.category().length() > 0, bn + ": category");
			Class<?>[] pt = m.getParameterTypes();
			for (Class<?> p : pt) check(BLOCK_TYPES.contains(p.getSimpleName()), bn + ": parameter type " + p.getSimpleName());
			check(b.args().length == 0 || b.args().length == pt.length, bn + ": args names count (" + b.args().length + " vs " + pt.length + ")");
			Class<?> rt = m.getReturnType();
			check(rt == void.class || BLOCK_TYPES.contains(rt.getSimpleName()), bn + ": return type " + rt.getSimpleName());
		}
		check(blocks >= 3, id + ": has useful blocks (" + blocks + ")");

		// the editor reads the same data from the source text and from the class
		BehaviorMeta s = SourceScanner.behavior(text, src.getPath());
		BehaviorMeta r = ReflectScanner.behavior(c);
		check(s != null && r != null, id + ": both scanners found it");
		if (s == null || r == null) return;
		check(s.name.equals(r.name) && s.icon.equals(r.icon) && s.desc.equals(r.desc) && s.category.equals(r.category) && s.targets.equals(r.targets), id + ": source and class agree on the info");
		check(s.events.size() == r.events.size(), id + ": event count agrees");
		check(s.vars.size() == r.vars.size(), id + ": parameter count agrees " + s.vars.size() + "/" + r.vars.size());
		for (int i = 0; i < Math.min(s.vars.size(), r.vars.size()); i++) {
			VarSpec x = s.vars.get(i), y = r.vars.get(i);
			boolean same = x.name.equals(y.name) && x.kind == y.kind && x.list == y.list && x.min == y.min && x.max == y.max && x.unit.equals(y.unit) && x.desc.equals(y.desc);
			check(same, id + "." + x.name + ": source/class agree " + x + " | " + y);
			Object dx = x.def, dy = y.def;
			boolean sameDef = (dx == null && (dy == null || dy instanceof List)) || (dx != null && dx.equals(dy)) || (dx instanceof Number && dy instanceof Number && Math.abs(((Number) dx).doubleValue() - ((Number) dy).doubleValue()) < 1e-4)
					|| (dx instanceof List && dy instanceof List && ((List<?>) dx).size() == ((List<?>) dy).size());
			check(sameDef, id + "." + x.name + ": default agrees " + dx + " | " + dy);
		}
	}

	// ------------------------------------------------------------------ headless logic

	/** An element with no stage and no physics: enough for the logic of Health / Timer / Lifetime. */
	static class Stub extends Actor implements PlayerItem {
		String text = "";
		public void update() {}
		public Body getBody() { return null; }
		public void setScript(ItemScript s) {}
		@SuppressWarnings("unchecked")
		public <T extends ItemScript> T getScript() { return null; }
		public com.star4droid.template.Utils.ChildsHolder getChildsHolder() { return null; }
		public com.star4droid.star2d.ElementDefs.ItemDef getProperties() { return null; }
		public PlayerItem getClone(String n) { return null; }
		public ElementEvent getElementEvents() { return null; }
		public void setItemText(String t) { text = t; }
	}

	static class Counter implements Behavior.Handler {
		int n;
		Object[] last;
		public void run(PlayerItem current, Object[] args) {
			n++;
			last = args;
		}
	}

	static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-3;
	}

	static <T extends Behavior> T attach(T b, Stub s) {
		b.item = s;
		return b;
	}

	static void smokeSupport() {
		check("1,234.50".equals(GameplaySupport.number(1234.5, 2, 0, true)), "number thousands: " + GameplaySupport.number(1234.5, 2, 0, true));
		check("0005".equals(GameplaySupport.number(5, 0, 4, false)), "number pad: " + GameplaySupport.number(5, 0, 4, false));
		check("-3.5".equals(GameplaySupport.number(-3.5, 1, 0, false)), "number negative: " + GameplaySupport.number(-3.5, 1, 0, false));
		check("1,000,000".equals(GameplaySupport.number(1000000, 0, 0, true)), "number million: " + GameplaySupport.number(1000000, 0, 0, true));
		check("0".equals(GameplaySupport.number(0.2, 0, 0, false)), "number rounds");
		check("1:05".equals(GameplaySupport.time(65, 0, false)), "time m:ss " + GameplaySupport.time(65, 0, false));
		check("65.3".equals(GameplaySupport.time(65.3f, 2, false)), "time tenths " + GameplaySupport.time(65.3f, 2, false));
		check("0".equals(GameplaySupport.time(0.5f, 1, true)) && "1".equals(GameplaySupport.time(0.2f, 1, false)), "countdown rounds up, stopwatch down");
		check("02:05.50".equals(GameplaySupport.time(125.5f, 3, true)), "time cc " + GameplaySupport.time(125.5f, 3, true));
		check(GameplaySupport.timeKey(2.5f, 1, false) != GameplaySupport.timeKey(1.5f, 1, false) && GameplaySupport.timeKey(2.4f, 1, false) == GameplaySupport.timeKey(2.1f, 1, false), "timeKey changes once a second");

		GameplaySupport.NameMatcher m = new GameplaySupport.NameMatcher().set("Enemy, boss;trap");
		check(m.matches("enemy_3") && m.matches("Boss1") && m.matches("trap") && !m.matches("player") && !m.matches(null), "name matcher");
		check(new GameplaySupport.NameMatcher().set("").isEmpty() && new GameplaySupport.NameMatcher().set("*").matches("anything"), "name matcher empty / star");

		GameplaySupport.Contacts c = new GameplaySupport.Contacts();
		Stub a = new Stub(), b = new Stub();
		c.begin(a);
		c.begin(a);
		c.begin(b);
		check(c.size() == 2 && c.takeFresh(0) && !c.takeFresh(0) && c.takeFresh(1), "contacts: fresh once");
		c.end(a);
		check(c.live(0), "contacts: two fixtures, one ended -> still touching");
		c.end(a);
		check(c.size() == 2 && !c.live(0) && c.live(1) && c.item(1) == b, "contacts: an ended contact is only marked (safe while a behavior walks the list)");
		Stub d = new Stub();
		c.begin(d);
		c.end(d);
		check(c.size() == 3 && c.live(2) && c.takeFresh(2) && !c.live(2), "contacts: began and ended inside one frame is still read once");
		c.begin(a);
		check(c.live(0) && c.takeFresh(0), "contacts: touching again after leaving counts as a new touch");
		c.setTimer(1, 2.5f);
		check(near(c.timer(1), 2.5), "contacts: timer");
		c.clear();
		check(c.size() == 0, "contacts: clear");

		check(GameplaySupport.nextId() != GameplaySupport.nextId(), "nextId unique");
		check(GameplaySupport.damageable(a) == null, "damageable of an element without script");
		check(GameplaySupport.hit(a, 5, null, 0, 0, 0, 0) == GameplaySupport.NOT_DAMAGEABLE, "hit on a dead / foreign element");
	}

	static void smokeHealth() {
		Stub s = new Stub();
		Health h = attach(new Health(), s);
		h.dieAction = Health.DieAction.NOTHING;
		h.invulnerableTime = 0.5f;
		h.regenPerSecond = 10;
		h.regenDelay = 1;
		Counter dmg = new Counter(), heal = new Counter(), death = new Counter(), revive = new Counter();
		h.on("onDamage", dmg).on("onHeal", heal).on("onDeath", death).on("onRevive", revive);
		h.onStart();
		check(near(h.getHealth(), 100) && !h.isDead(), "health starts full");
		check(h.takeDamage(30, null, 0, 0, 0) && near(h.getHealth(), 70), "damage 30 -> 70");
		check(dmg.n == 1 && near(((Number) dmg.last[0]).doubleValue(), 30), "onDamage fired with the amount");
		check(!h.takeDamage(10, null, 0, 0, 0) && near(h.getHealth(), 70) && h.isInvulnerable(), "invulnerability blocks the second hit");
		h.onUpdate(0.3f);
		check(s.getColor().a < 1f || h.isInvulnerable(), "blinking while invulnerable");
		h.onUpdate(0.3f);
		check(!h.isInvulnerable() && near(s.getColor().a, 1.0), "alpha restored after invulnerability");
		check(h.takeDamage(20, null, 0, 0, 0) && near(h.getHealth(), 50), "damage again -> 50");
		h.onUpdate(0.6f);
		h.onUpdate(0.3f);
		check(near(h.getHealth(), 50), "no regeneration before the delay");
		h.onUpdate(1.0f);
		check(h.getHealth() > 55 && h.getHealth() < 65, "regeneration after the delay: " + h.getHealth());
		h.heal(500);
		check(near(h.getHealth(), 100) && heal.n == 1, "heal is capped at the maximum, onHeal fired");
		check(near(h.getHealthPercent(), 100), "health percent");
		h.onUpdate(1f);
		check(h.takeDamage(1000, null, 0, 0, 0) && h.isDead() && death.n == 1 && near(h.getHealth(), 0), "lethal damage -> dead, onDeath once");
		check(!h.takeDamage(5, null, 0, 0, 0) && death.n == 1, "dead ignores damage");
		h.revive(0.5f);
		check(!h.isDead() && near(h.getHealth(), 50) && revive.n == 1, "revive to 50%");
		h.setMaxHealth(200, true);
		check(near(h.getHealth(), 200), "set max health + fill");
		h.makeInvulnerable(2);
		check(!h.takeDamage(1, null, 0, 0, 0), "makeInvulnerable");
		h.enabled = false;
		h.onUpdate(5f);
		h.enabled = true;
		h.kill();
		check(h.isDead() && death.n == 2, "kill()");
	}

	static void smokeTimer() {
		Stub s = new Stub();
		Timer t = attach(new Timer(), s);
		t.seconds = 3;
		t.format = Timer.TimeFormat.SECONDS;
		t.prefix = "T=";
		t.warnAt = 1;
		Counter timeout = new Counter(), tick = new Counter(), warn = new Counter();
		t.on("onTimeout", timeout).on("onTick", tick).on("onWarning", warn);
		t.onStart();
		check("T=3".equals(s.text), "countdown shows the start: " + s.text);
		t.onUpdate(1f);
		check("T=2".equals(s.text) && near(t.getTime(), 2), "countdown 2: " + s.text);
		t.onUpdate(1f);
		check(warn.n == 1 && near(s.getColor().r, 1.0) && s.getColor().g < 0.5f, "warning fired and text turned red");
		t.onUpdate(1.2f);
		check("T=0".equals(s.text) && timeout.n == 1 && !t.isRunning(), "timeout: " + s.text + " " + timeout.n);
		t.onUpdate(1f);
		check(timeout.n == 1, "stays stopped");
		t.restart();
		check(t.isRunning() && "T=3".equals(s.text) && near(s.getColor().g, 1.0), "restart brings back the time and the color");
		t.pause();
		t.onUpdate(1f);
		check(near(t.getTime(), 3), "pause");
		t.addTime(10);
		check(near(t.getTime(), 13), "add time");

		Stub s2 = new Stub();
		Timer w = attach(new Timer(), s2);
		w.mode = Timer.TimerMode.STOPWATCH;
		w.seconds = 0;
		w.format = Timer.TimeFormat.M_SS;
		w.onStart();
		check("0:00".equals(s2.text), "stopwatch starts at 0:00: " + s2.text);
		w.onUpdate(0.9f);
		check("0:00".equals(s2.text), "stopwatch rounds down: " + s2.text);
		w.onUpdate(61f);
		check("1:01".equals(s2.text), "stopwatch 1:01: " + s2.text);

		Stub s3 = new Stub();
		Timer l = attach(new Timer(), s3);
		l.seconds = 1;
		l.loop = true;
		Counter lt = new Counter();
		l.on("onTimeout", lt);
		l.onStart();
		l.onUpdate(1.1f);
		check(lt.n == 1 && l.isRunning() && near(l.getTime(), 1), "loop restarts after the timeout");
	}

	static void smokeLifetime() {
		Stub s = new Stub();
		Lifetime l = attach(new Lifetime(), s);
		l.seconds = 1;
		l.fadeTime = 0.5f;
		l.shrink = true;
		l.endAction = Lifetime.EndAction.HIDE;
		Counter ex = new Counter();
		l.on("onExpire", ex);
		l.onStart();
		l.onUpdate(0.4f);
		check(near(s.getColor().a, 1.0) && near(l.getTimeLeft(), 0.6), "full alpha before the fade");
		l.onUpdate(0.35f);
		check(s.getColor().a < 0.6f && s.getColor().a > 0.4f && s.getScaleX() < 0.6f, "fading and shrinking: " + s.getColor().a);
		l.extend(1f);
		check(near(l.getTimeLeft(), 1.25), "extend");
		l.onUpdate(1.3f);
		check(ex.n == 1, "onExpire fired once");
		l.onUpdate(1f);
		check(ex.n == 1, "expired for good");
	}

	static void smokeRig() {
		OrthographicCamera cam = new OrthographicCamera();
		GameplaySupport.CameraRig r = new GameplaySupport.CameraRig();
		r.write(cam, 10, 20, 1, 2);
		check(near(cam.position.x, 11) && near(cam.position.y, 22), "rig writes base + offset");
		check(near(r.baseX(cam), 10) && near(r.baseY(cam), 20), "rig gives the base back");
		cam.position.x = 50; // somebody else moved the camera
		check(near(r.baseX(cam), 50), "foreign camera move wins");
		check(GameplaySupport.noise(1.3f, 1f) >= -1.0f && GameplaySupport.noise(1.3f, 1f) <= 1.0f, "noise range");
	}

	/** Stands in for a generated scene class: variables are package-private fields of a StageImp subclass. */
	public static class FakeScene extends StageImp {
		int score = 5;
		float speed = 1.5f;
		String label = "7";
		boolean flag;
	}

	@SuppressWarnings("unchecked")
	static void smokeVar() {
		try {
			Field uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
			uf.setAccessible(true);
			Object unsafe = uf.get(null);
			Object scene = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, FakeScene.class);
			StageImp st = (StageImp) scene;
			FakeScene fs = (FakeScene) scene;
			fs.score = 5;
			fs.speed = 1.5f;
			fs.label = "7"; // Unsafe.allocateInstance skips the field initializers
			GameplaySupport.Var v = new GameplaySupport.Var().bind(st, "score");
			check(v.isSceneVariable() && near(v.get(), 5), "int scene variable read");
			v.add(3);
			check(((FakeScene) scene).score == 8, "int scene variable write");
			v.set(2.6);
			check(((FakeScene) scene).score == 3, "int is rounded");
			GameplaySupport.Var f = new GameplaySupport.Var().bind(st, "speed");
			f.add(0.5);
			check(near(((FakeScene) scene).speed, 2.0), "float scene variable");
			GameplaySupport.Var t = new GameplaySupport.Var().bind(st, "label");
			check(near(t.get(), 7), "String variable holding a number");
			t.set(12);
			check("12".equals(((FakeScene) scene).label), "String variable written without .0");
			GameplaySupport.Var b = new GameplaySupport.Var().bind(st, "flag");
			b.set(1);
			check(((FakeScene) scene).flag && near(b.get(), 1), "boolean variable");
			GameplaySupport.Var none = new GameplaySupport.Var().bind(st, "undeclared");
			none.add(4);
			none.add(1);
			check(!none.isSceneVariable() && near(none.get(), 5), "undeclared variable falls back to a hidden per-scene store");
			check(near(new GameplaySupport.Var().bind(st, "").get(), 0), "empty name");
			GameplaySupport.setCheckpoint(st, "a", 10, 20, false);
			float[] p = GameplaySupport.getCheckpoint(st, "a", false);
			check(p != null && near(p[0], 10) && near(p[1], 20) && GameplaySupport.getCheckpoint(st, "b", false) == null, "checkpoint store per channel");
			GameplaySupport.clearCheckpoint(st, "a");
			check(GameplaySupport.getCheckpoint(st, "a", false) == null, "checkpoint cleared");
		} catch (Throwable ex) {
			check(false, "scene variable test setup: " + ex);
		}
	}
}
