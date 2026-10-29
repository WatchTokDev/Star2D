import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.ReflectScanner;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.template.Items.ProgressItem;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.Behavior;
import com.star4droid.template.behaviors.Block;
import com.star4droid.template.behaviors.Easing;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Run by tools/test_lib_ui.py: checks the metadata of the UI / effects / input behaviors (annotations complete, icons exist, defaults in
 * range, scanner == reflection) and runs each one headless against fake items (no GL, no phone): timing, restore on disable, events,
 * touch handling and the "no allocation per frame" rule.
 */
public class LibUiTest {
	static final String[] NAMES = {"Pulse", "Blink", "FadeIn", "FadeOut", "Shake", "SlideIn", "Wobble", "Typewriter", "CountUp", "ButtonSound",
			"OpenSceneOnClick", "ToggleOnClick", "Draggable", "Parallax", "LongPress", "SwipeDetector", "ProgressBind", "TintCycle"};
	static final String PKG = "com.star4droid.template.behaviors.lib.";
	static int checks, failures;
	static String root;
	static final float DT = 1f / 60f;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			failures++;
			System.out.println("FAIL " + what);
		}
	}

	static boolean near(float a, float b, float eps) {
		return Math.abs(a - b) <= eps;
	}

	static String read(File f) throws Exception {
		return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
	}

	// ------------------------------------------------------------------ fakes

	static sun.misc.Unsafe unsafe;

	@SuppressWarnings("unchecked")
	static <T> T alloc(Class<T> c) throws Exception {
		return (T) unsafe.allocateInstance(c);
	}

	/** Scene stand-in: records what the behaviors ask from it. Created without running any StageImp constructor. */
	public static class FakeStage extends StageImp {
		String lastScene = "";
		int sceneCount, soundCount, releaseCount;
		String lastSound = "";
		float camXpx, camYpx;
		java.util.HashMap<String, String> values = new java.util.HashMap<String, String>();

		public boolean isPlaying() {
			return true;
		}

		public int getSteps() {
			return 1;
		}

		public float getCameraX() {
			return camXpx * 0.25f;
		}

		public float getCameraY() {
			return camYpx * 0.25f;
		}

		public void openScene(String scene) {
			lastScene = scene;
			sceneCount++;
		}

		public void startSound(String s) {
			lastSound = s;
			soundCount++;
		}

		public void releaseSound(String s) {
			releaseCount++;
		}

		public String getValue(String name) {
			String v = values.get(name);
			return v == null ? "" : v;
		}
	}

	/** A progress bar stand-in (the real one needs GL). */
	public static class FakeBar extends ProgressItem {
		FakeBar() {
			super((StageImp) null);
		}

		float progress, max = 100;
		Actor a;

		public Actor getActor() {
			return a;
		}

		public float getProgressF() {
			return progress;
		}

		public float getMaxF() {
			return max;
		}

		public ProgressItem setProgress(float p) {
			progress = p;
			return this;
		}

		public com.badlogic.gdx.physics.box2d.Body getBody() {
			return null;
		}
	}

	static class Item implements InvocationHandler {
		final Actor actor;
		final Body body;
		boolean destroyed;
		int progress;
		PlayerItem proxy;

		Item(Actor a, Body b) {
			actor = a;
			body = b;
			proxy = (PlayerItem) Proxy.newProxyInstance(PlayerItem.class.getClassLoader(), new Class[]{PlayerItem.class}, this);
		}

		public Object invoke(Object p, Method m, Object[] args) {
			String n = m.getName();
			if (n.equals("getActor")) return actor;
			if (n.equals("getBody")) return body;
			if (n.equals("getName")) return "it";
			if (n.equals("destroy")) {
				destroyed = true;
				return null;
			}
			if (n.equals("getProgress")) return progress;
			if (n.equals("hashCode")) return System.identityHashCode(p);
			if (n.equals("equals")) return p == args[0];
			if (n.equals("toString")) return "FakeItem";
			Class<?> r = m.getReturnType();
			if (r == int.class) return 0;
			if (r == float.class) return 0f;
			if (r == double.class) return 0d;
			if (r == boolean.class) return false;
			if (r == long.class) return 0L;
			return null;
		}
	}

	static float touchX, touchY;
	static boolean touched;
	static String lastError = "";

	static void installGdx() throws Exception {
		Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class[]{Application.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] a) {
				if (m.getName().equals("error")) lastError = String.valueOf(a[1]);
				return null;
			}
		});
		Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class[]{Graphics.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] a) {
				if (m.getName().equals("getDeltaTime")) return DT;
				return null;
			}
		});
		Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] a) {
				String n = m.getName();
				if (n.equals("isTouched")) return touched;
				if (n.equals("getX")) return (int) touchX;
				if (n.equals("getY")) return (int) touchY;
				if (n.equals("isKeyPressed")) return false;
				Class<?> r = m.getReturnType();
				if (r == boolean.class) return false;
				if (r == int.class) return 0;
				if (r == float.class) return 0f;
				return null;
			}
		});
	}

	static FakeStage newStage() throws Exception {
		FakeStage s = alloc(FakeStage.class);
		s.values = new java.util.HashMap<String, String>();
		s.lastScene = "";
		s.lastSound = "";
		return s;
	}

	static com.badlogic.gdx.scenes.scene2d.Group sceneRoot;

	/** A real scene2d root group that belongs to a (GL-less) Stage, so actors added to it count as alive. */
	static void makeRoot() throws Exception {
		com.badlogic.gdx.scenes.scene2d.Stage stage = alloc(com.badlogic.gdx.scenes.scene2d.Stage.class);
		Field rf = com.badlogic.gdx.scenes.scene2d.Stage.class.getDeclaredField("root");
		rf.setAccessible(true);
		sceneRoot = new com.badlogic.gdx.scenes.scene2d.Group();
		rf.set(stage, sceneRoot);
		Field sf = Actor.class.getDeclaredField("stage");
		sf.setAccessible(true);
		sf.set(sceneRoot, stage);
	}

	static Actor box(float x, float y, float w, float h) {
		Actor a = new Actor();
		a.setBounds(x, y, w, h);
		a.setOrigin(w / 2, h / 2);
		sceneRoot.addActor(a);
		return a;
	}

	static Label label(String text) throws Exception {
		BitmapFont font = new BitmapFont(new BitmapFont.BitmapFontData(), new Array<TextureRegion>(new TextureRegion[]{new TextureRegion()}), false);
		Label.LabelStyle st = new Label.LabelStyle(font, Color.WHITE);
		Label l = new Label(text, st);
		sceneRoot.addActor(l);
		return l;
	}

	static <T extends Behavior> T make(Class<T> c, Item it, FakeStage st) throws Exception {
		T b = c.newInstance();
		b.item = it.proxy;
		b.stage = st;
		return b;
	}

	static void run(Behavior b, float seconds) {
		int n = Math.round(seconds / DT);
		for (int i = 0; i < n; i++) b.engineUpdate();
	}

	static int counter;
	static Behavior.Handler counter() {
		counter = 0;
		return new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				counter++;
			}
		};
	}

	static InputEvent ev(float stageX, float stageY, int pointer) {
		InputEvent e = new InputEvent();
		e.setStageX(stageX);
		e.setStageY(stageY);
		e.setPointer(pointer);
		return e;
	}

	static float cx(Actor a) {
		return a.getX() + a.getWidth() / 2;
	}

	static float cy(Actor a) {
		return a.getY() + a.getHeight() / 2;
	}

	// ------------------------------------------------------------------ metadata

	static Set<String> iconIds() throws Exception {
		String js = read(new File(root, "app/src/main/assets/icons/catalog.json"));
		Matcher m = Pattern.compile("\"i\"\\s*:\\s*\"([^\"]+)\"").matcher(js);
		Set<String> ids = new HashSet<String>();
		while (m.find()) ids.add(m.group(1));
		return ids;
	}

	static void metadata() throws Exception {
		Set<String> icons = iconIds();
		check(icons.size() > 1000, "icon catalog read: " + icons.size());
		Set<String> seenNames = new HashSet<String>();
		for (String n : NAMES) {
			Class<?> c = Class.forName(PKG + n);
			File src = new File(root, "app/src/main/java/com/star4droid/template/behaviors/lib/" + n + ".java");
			String code = read(src);
			check(Behavior.class.isAssignableFrom(c), n + ": extends Behavior");
			BehaviorMeta ref = ReflectScanner.behavior(c);
			BehaviorMeta sc = SourceScanner.behavior(code, src.getPath());
			check(ref != null && sc != null, n + ": both scanners find the class");
			if (ref == null || sc == null) continue;
			check(ref.name.length() > 0 && seenNames.add(ref.name), n + ": unique display name '" + ref.name + "'");
			check(icons.contains(ref.icon), n + ": icon '" + ref.icon + "' is in the catalog");
			check(ref.desc.length() >= 30 && ref.desc.length() <= 260, n + ": description length " + ref.desc.length());
			check(ref.category.length() > 0 && !ref.category.equals("General"), n + ": category " + ref.category);
			check(ref.targets.equals("BOTH") || ref.targets.equals("UI") || ref.targets.equals("PHYSICS"), n + ": targets");
			// source scanner == reflection
			check(sc.name.equals(ref.name) && sc.icon.equals(ref.icon) && sc.desc.equals(ref.desc) && sc.category.equals(ref.category) && sc.targets.equals(ref.targets), n + ": info (source scanner == reflection): '" + sc.desc + "' / '" + ref.desc + "'");
			check(sc.events.size() == ref.events.size(), n + ": events equal");
			check(sc.vars.size() == ref.vars.size(), n + ": var count " + sc.vars.size() + " vs " + ref.vars.size());
			for (int i = 0; i < Math.min(sc.vars.size(), ref.vars.size()); i++) {
				VarSpec x = sc.vars.get(i), y = ref.vars.get(i);
				check(x.name.equals(y.name) && x.kind == y.kind && x.list == y.list && x.options.equals(y.options) && x.min == y.min && x.max == y.max && x.step == y.step && x.unit.equals(y.unit) && x.desc.equals(y.desc), n + "." + x.name + ": spec equal " + x + " / " + y);
				Object dx = x.def, dy = y.def;
				boolean same = (dx == null && (dy == null || dy instanceof List)) || (dx != null && dx.equals(dy)) || (dx instanceof Number && dy instanceof Number && Math.abs(((Number) dx).doubleValue() - ((Number) dy).doubleValue()) < 1e-4);
				check(same, n + "." + x.name + ": default equal " + dx + " / " + dy);
			}
			// every public field is a documented parameter with a sane default
			for (Field f : c.getFields()) {
				int mod = f.getModifiers();
				if (java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isFinal(mod)) continue;
				boolean hidden = f.getAnnotation(com.star4droid.template.behaviors.Hidden.class) != null;
				if (hidden || f.getDeclaringClass() == Behavior.class) continue;
				check(f.getAnnotation(com.star4droid.template.behaviors.Param.class) != null, n + "." + f.getName() + ": has @Param");
			}
			for (VarSpec v : ref.vars) {
				check(v.desc.length() > 0, n + "." + v.name + ": has a description");
				if (v.isNumber() && v.def instanceof Number) {
					double d = ((Number) v.def).doubleValue();
					check(d >= v.min && d <= v.max, n + "." + v.name + ": default " + d + " within " + v.min + ".." + v.max);
					check(v.min > -1e8 && v.max < 1e8 || v.name.equals("from") || v.name.equals("to") || v.name.equals("minValue") || v.name.equals("maxValue"), n + "." + v.name + ": has a real range");
				}
				if (v.kind == VarSpec.Kind.ENUM) check(v.def != null && v.options.contains(String.valueOf(v.def)), n + "." + v.name + ": enum default in options");
			}
			// nested Ease enums == Easing.NAMES, in order
			for (Class<?> k : c.getDeclaredClasses()) {
				if (k.getSimpleName().equals("Ease")) {
					List<String> names = new ArrayList<String>();
					for (Object o : k.getEnumConstants()) names.add(o.toString());
					check(names.equals(Arrays.asList(Easing.NAMES)), n + ": Ease constants match Easing.NAMES");
				}
			}
			// events: declared <=> fired
			Set<String> fired = new HashSet<String>();
			Matcher fm = Pattern.compile("fire\\(\"(\\w+)\"").matcher(code);
			while (fm.find()) fired.add(fm.group(1));
			Set<String> declared = new HashSet<String>();
			for (BehaviorMeta.EventSpec e : ref.events) declared.add(e.name);
			check(declared.equals(fired), n + ": events declared " + declared + " == fired " + fired);
			// blocks
			Set<String> labels = new HashSet<String>();
			for (Method m : c.getDeclaredMethods()) {
				Block b = m.getAnnotation(Block.class);
				if (b == null) continue;
				check(java.lang.reflect.Modifier.isPublic(m.getModifiers()), n + "." + m.getName() + ": block method is public");
				check(b.label().length() > 0 && b.desc().length() > 0 && b.labelAr().length() > 0 && b.descAr().length() > 0, n + "." + m.getName() + ": block has label+desc in English and Arabic");
				check(labels.add(b.label()), n + ": block label unique '" + b.label() + "'");
				check(b.category().length() > 0, n + "." + m.getName() + ": block category");
				Class<?>[] pt = m.getParameterTypes();
				check(b.args().length == 0 || b.args().length == pt.length, n + "." + m.getName() + ": args count");
				for (Class<?> t : pt)
					check(t == int.class || t == long.class || t == float.class || t == double.class || t == boolean.class || t == String.class || t == PlayerItem.class || t == Color.class, n + "." + m.getName() + ": parameter type " + t);
			}
			check(!labels.isEmpty(), n + ": has at least one block");
			check(!code.contains("android."), n + ": no android imports");
			String noComments = code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
			check(!noComments.contains("->") && !noComments.contains("::"), n + ": no lambdas (Java 7)");
		}
	}

	// ------------------------------------------------------------------ behavior simulations

	static void testPulse() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(100, 100, 80, 40), null);
		Class<?> c = Class.forName(PKG + "Pulse");
		Behavior b = make((Class<? extends Behavior>) c, it, st);
		b.setParam("speed", 1f);
		b.setParam("minScale", 0.5f);
		b.setParam("maxScale", 1.5f);
		b.setParam("lowOpacity", 0.5f);
		b.engineStart();
		float min = 9, max = 0;
		for (int i = 0; i < 120; i++) {
			b.engineUpdate();
			float s = it.actor.getScaleX();
			min = Math.min(min, s);
			max = Math.max(max, s);
		}
		check(near(min, 0.5f, 0.03f) && near(max, 1.5f, 0.03f), "Pulse: scale oscillates 0.5..1.5 (" + min + ".." + max + ")");
		check(cx(it.actor) == 140 && cy(it.actor) == 120, "Pulse: never moves the element");
		b.setEnabled(false);
		check(it.actor.getScaleX() == 1 && it.actor.getScaleY() == 1 && it.actor.getColor().a == 1, "Pulse: disable restores scale and opacity");
		// pulse once
		Item it2 = new Item(box(0, 0, 10, 10), null);
		Behavior b2 = make((Class<? extends Behavior>) c, it2, st);
		b2.setParam("autoPlay", false);
		b2.on("onPulse", counter());
		b2.engineStart();
		run(b2, 1);
		check(it2.actor.getScaleX() == 1, "Pulse: autoPlay=false does nothing");
		invoke(b2, "pulseOnce");
		run(b2, 2);
		check(counter == 1 && it2.actor.getScaleX() == 1 && !(Boolean) invoke(b2, "isPulsing"), "Pulse: pulse once fires onPulse once and stops (" + counter + ")");
	}

	static Object invoke(Behavior b, String method, Object... args) throws Exception {
		for (Method m : b.getClass().getMethods()) {
			if (m.getName().equals(method) && m.getParameterTypes().length == args.length) {
				Class<?>[] pt = m.getParameterTypes();
				Object[] a = args.clone();
				for (int i = 0; i < a.length; i++) {
					if (a[i] instanceof Number && pt[i] == float.class) a[i] = ((Number) a[i]).floatValue();
					if (a[i] instanceof Number && pt[i] == int.class) a[i] = ((Number) a[i]).intValue();
				}
				return m.invoke(b, a);
			}
		}
		throw new RuntimeException("no method " + method);
	}

	static void testBlink() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(0, 0, 10, 10), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Blink"), it, st);
		b.setParam("onTime", 0.5f);
		b.setParam("offTime", 0.5f);
		b.setParam("count", 2);
		b.on("onDone", counter());
		b.engineStart();
		run(b, 0.25f);
		check(it.actor.getColor().a == 1, "Blink: visible during the on time");
		run(b, 0.5f);
		check(it.actor.getColor().a == 0, "Blink: invisible during the off time, got " + it.actor.getColor().a);
		run(b, 2.5f);
		check(counter == 1 && it.actor.getColor().a == 1 && !(Boolean) invoke(b, "isBlinking"), "Blink: after 2 blinks onDone fired once and the element is visible (" + counter + ")");
		it.actor.setColor(1, 1, 1, 0.4f);
		Item it2 = new Item(box(0, 0, 10, 10), null);
		it2.actor.setColor(1, 1, 1, 0.4f);
		Behavior b2 = make((Class<? extends Behavior>) Class.forName(PKG + "Blink"), it2, st);
		b2.engineStart();
		run(b2, 0.9f);
		b2.setEnabled(false);
		check(near(it2.actor.getColor().a, 0.4f, 1e-4f), "Blink: disable restores the saved opacity 0.4, got " + it2.actor.getColor().a);
	}

	static void testFade() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(0, 0, 10, 10), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "FadeIn"), it, st);
		b.setParam("duration", 1f);
		b.setParam("delay", 0.5f);
		b.setParam("ease", "LINEAR");
		b.on("onDone", counter());
		b.engineStart();
		check(it.actor.getColor().a == 0, "FadeIn: starts invisible");
		run(b, 1.0f);
		check(near(it.actor.getColor().a, 0.5f, 0.03f), "FadeIn: halfway after delay+0.5s, got " + it.actor.getColor().a);
		run(b, 0.6f);
		check(it.actor.getColor().a == 1 && counter == 1, "FadeIn: ends at 1 and fires onDone once");
		// FadeOut: hide
		Item o = new Item(box(0, 0, 10, 10), null);
		Behavior fo = make((Class<? extends Behavior>) Class.forName(PKG + "FadeOut"), o, st);
		fo.setParam("delay", 0f);
		fo.setParam("duration", 0.5f);
		fo.setParam("then", "HIDE");
		fo.on("onDone", counter());
		fo.engineStart();
		run(fo, 0.3f);
		check(o.actor.getColor().a < 1 && o.actor.getColor().a > 0 && o.actor.isVisible(), "FadeOut: fading");
		run(fo, 0.4f);
		check(o.actor.getColor().a == 0 && !o.actor.isVisible() && counter == 1, "FadeOut: hidden at the end, onDone once");
		fo.setEnabled(false);
		check(o.actor.isVisible() && o.actor.getColor().a == 1, "FadeOut: disable shows the element again with its saved opacity");
		// FadeOut: destroy + click trigger
		Item d = new Item(box(0, 0, 10, 10), null);
		Behavior fd = make((Class<? extends Behavior>) Class.forName(PKG + "FadeOut"), d, st);
		fd.setParam("trigger", "ON_CLICK");
		fd.setParam("delay", 0f);
		fd.setParam("duration", 0.2f);
		fd.setParam("then", "DESTROY");
		fd.engineStart();
		run(fd, 1);
		check(!d.destroyed && d.actor.getColor().a == 1, "FadeOut: ON_CLICK waits for the click");
		fd.onClick();
		run(fd, 0.5f);
		check(d.destroyed, "FadeOut: DESTROY removes the element");
		// scale fade
		Item s = new Item(box(0, 0, 10, 10), null);
		Behavior fs = make((Class<? extends Behavior>) Class.forName(PKG + "FadeIn"), s, st);
		fs.setParam("fromScale", 0.5f);
		fs.setParam("ease", "LINEAR");
		fs.engineStart();
		check(near(s.actor.getScaleX(), 0.5f, 1e-4f), "FadeIn: starts at fromScale");
		run(fs, 1);
		check(near(s.actor.getScaleX(), 1f, 1e-4f), "FadeIn: ends at normal size");
	}

	static void testShake() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(100, 100, 40, 40), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Shake"), it, st);
		b.setParam("trigger", "ON_START");
		b.setParam("intensity", 10f);
		b.setParam("duration", 0.5f);
		b.setParam("rotation", 5f);
		b.on("onDone", counter());
		b.engineStart();
		float maxD = 0, maxR = 0;
		for (int i = 0; i < 20; i++) {
			b.engineUpdate();
			maxD = Math.max(maxD, Math.max(Math.abs(cx(it.actor) - 120), Math.abs(cy(it.actor) - 120)));
			maxR = Math.max(maxR, Math.abs(it.actor.getRotation()));
		}
		check(maxD > 0.5f && maxD <= 10.001f, "Shake: moves within the intensity, max " + maxD);
		check(maxR > 0.05f && maxR <= 5.001f, "Shake: rotates within the angle, max " + maxR);
		run(b, 0.6f);
		check(counter == 1 && near(cx(it.actor), 120, 1e-3f) && near(cy(it.actor), 120, 1e-3f) && near(it.actor.getRotation(), 0, 1e-3f), "Shake: ends exactly in place, onDone once");
		// touch trigger, horizontal only
		Item t = new Item(box(0, 0, 40, 40), null);
		Behavior bt = make((Class<? extends Behavior>) Class.forName(PKG + "Shake"), t, st);
		bt.setParam("axis", "HORIZONTAL");
		bt.engineStart();
		run(bt, 0.3f);
		check(cx(t.actor) == 20, "Shake: ON_TOUCH waits for a touch");
		bt.onTouchStart(ev(0, 0, 0));
		float dy = 0;
		for (int i = 0; i < 15; i++) {
			bt.engineUpdate();
			dy = Math.max(dy, Math.abs(cy(t.actor) - 20));
		}
		check(dy == 0, "Shake: HORIZONTAL never moves up/down");
		// shake keeps up with a moving element
		Item mv = new Item(box(0, 0, 40, 40), null);
		Behavior bm = make((Class<? extends Behavior>) Class.forName(PKG + "Shake"), mv, st);
		bm.setParam("trigger", "ON_START");
		bm.setParam("loop", true);
		bm.engineStart();
		run(bm, 0.2f);
		mv.actor.moveBy(100, 0);
		run(bm, 0.2f);
		bm.setEnabled(false);
		check(near(cx(mv.actor), 120, 1e-2f), "Shake: disable leaves the element where it was moved to (+100), got " + cx(mv.actor));
	}

	static void testSlide() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(100, 100, 40, 40), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "SlideIn"), it, st);
		b.setParam("from", "LEFT");
		b.setParam("distance", 400f);
		b.setParam("duration", 1f);
		b.setParam("ease", "LINEAR");
		b.on("onDone", counter());
		b.engineStart();
		check(near(cx(it.actor), 120 - 400, 1e-3f) && it.actor.getColor().a == 0, "SlideIn: starts 400px left and invisible");
		run(b, 0.5f);
		check(near(cx(it.actor), 120 - 200, 3f), "SlideIn: halfway at 0.5s, x=" + cx(it.actor));
		run(b, 0.6f);
		check(near(cx(it.actor), 120, 1e-3f) && it.actor.getColor().a == 1 && counter == 1, "SlideIn: arrives in place, onDone once");
		invoke(b, "slideOut");
		run(b, 1.1f);
		check(near(cx(it.actor), 120 - 400, 1e-2f) && it.actor.getColor().a == 0 && counter == 2, "SlideIn: slide out goes back 400px and fades");
		b.setEnabled(false);
		check(near(cx(it.actor), 120, 1e-3f) && it.actor.getColor().a == 1, "SlideIn: disable restores the saved place");
		// directions
		String[] dirs = {"RIGHT", "TOP", "BOTTOM"};
		float[][] expect = {{400, 0}, {0, 400}, {0, -400}};
		for (int i = 0; i < 3; i++) {
			Item d = new Item(box(0, 0, 40, 40), null);
			Behavior bd = make((Class<? extends Behavior>) Class.forName(PKG + "SlideIn"), d, st);
			bd.setParam("from", dirs[i]);
			bd.engineStart();
			check(near(cx(d.actor) - 20, expect[i][0], 1e-3f) && near(cy(d.actor) - 20, expect[i][1], 1e-3f), "SlideIn: direction " + dirs[i]);
		}
	}

	static void testWobble() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(0, 0, 40, 40), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Wobble"), it, st);
		b.setParam("angle", 10f);
		b.setParam("speed", 1f);
		b.setParam("duration", 2f);
		b.on("onDone", counter());
		b.engineStart();
		float max = 0;
		for (int i = 0; i < 100; i++) {
			b.engineUpdate();
			max = Math.max(max, Math.abs(it.actor.getRotation()));
		}
		check(near(max, 10, 0.3f), "Wobble: rotates +-10, max " + max);
		run(b, 1f);
		check(counter == 1 && near(it.actor.getRotation(), 0, 1e-3f), "Wobble: ends straight, onDone once");
		Item o = new Item(box(0, 0, 40, 40), null);
		o.actor.setRotation(30);
		Behavior bo = make((Class<? extends Behavior>) Class.forName(PKG + "Wobble"), o, st);
		bo.engineStart();
		run(bo, 0.7f);
		bo.setEnabled(false);
		check(near(o.actor.getRotation(), 30, 1e-3f), "Wobble: disable restores the saved rotation 30, got " + o.actor.getRotation());
		// origin of a corner-pivot element (Text / Progress) is centered while it scales / rotates and given back
		Item corner = new Item(box(0, 0, 40, 20), null);
		corner.actor.setOrigin(0, 0);
		Behavior bc = make((Class<? extends Behavior>) Class.forName(PKG + "Wobble"), corner, st);
		bc.engineStart();
		check(corner.actor.getOriginX() == 20 && corner.actor.getOriginY() == 10, "Wobble: pivots around the center");
		bc.setEnabled(false);
		check(corner.actor.getOriginX() == 0 && corner.actor.getOriginY() == 0, "Wobble: gives the pivot back");
	}

	static void testPhysical() throws Exception {
		World w = new World(new Vector2(0, 0), true);
		BodyDef bd = new BodyDef();
		bd.type = BodyDef.BodyType.KinematicBody;
		bd.position.set(10, 10);
		Body body = w.createBody(bd);
		FakeStage st = newStage();
		Item it = new Item(box(0, 0, 10, 10), body);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Wobble"), it, st);
		b.setParam("angle", 20f);
		b.setParam("speed", 1f);
		b.engineStart();
		run(b, 0.25f);
		check(Math.abs(body.getAngle()) > 0.3f, "Wobble on a physical element turns the body, angle=" + body.getAngle());
		b.setEnabled(false);
		check(near(body.getAngle(), 0, 1e-4f), "Wobble on a physical element: disable restores the body angle");
		Behavior sh = make((Class<? extends Behavior>) Class.forName(PKG + "Shake"), it, st);
		sh.setParam("trigger", "ON_START");
		sh.setParam("loop", true);
		sh.setParam("intensity", 20f);
		sh.engineStart();
		run(sh, 0.3f);
		check(Math.abs(body.getPosition().x - 10) > 0.01f, "Shake moves a physical element");
		sh.setEnabled(false);
		check(near(body.getPosition().x, 10, 1e-3f) && near(body.getPosition().y, 10, 1e-3f), "Shake: disable restores a physical element's position");
		// Draggable on a physical element freezes its velocity while held
		Behavior dr = make((Class<? extends Behavior>) Class.forName(PKG + "Draggable"), it, st);
		dr.engineStart();
		w.destroyBody(body);
	}

	static void testTint() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(0, 0, 10, 10), null);
		it.actor.setColor(0.2f, 0.4f, 0.6f, 0.9f);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "TintCycle"), it, st);
		b.setParam("colorA", "#FF0000");
		b.setParam("colorB", "#0000FF");
		b.setParam("speed", 1f);
		b.engineStart();
		run(b, 0.5f);
		Color c = it.actor.getColor();
		check(near(c.r, 0, 0.05f) && near(c.b, 1, 0.05f) && near(c.a, 0.9f, 1e-4f), "TintCycle: halfway = colorB and the opacity is kept, got " + c);
		b.setParam("mode", "RAINBOW");
		run(b, 0.3f);
		check(c.r != 0.2f || c.g != 0.4f, "TintCycle: rainbow changes the color");
		b.setEnabled(false);
		check(near(c.r, 0.2f, 1e-4f) && near(c.g, 0.4f, 1e-4f) && near(c.b, 0.6f, 1e-4f) && near(c.a, 0.9f, 1e-4f), "TintCycle: disable restores the tint, got " + c);
	}

	static void testParallax() throws Exception {
		FakeStage st = newStage();
		Item ui = new Item(box(0, 0, 100, 100), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Parallax"), ui, st);
		b.setParam("factorX", 0.5f);
		b.engineStart();
		st.camXpx = 1000;
		b.engineUpdate(); // takes the camera as the start
		check(ui.actor.getX() == 0, "Parallax: first frame does not jump");
		st.camXpx = 1200;
		b.engineUpdate();
		check(near(ui.actor.getX(), -100, 1e-2f), "Parallax (UI): camera +200 -> layer drifts -100 on screen, x=" + ui.actor.getX());
		b.setEnabled(false);
		check(near(ui.actor.getX(), 0, 1e-3f), "Parallax: disable restores");
		// loop
		Item lp = new Item(box(0, 0, 100, 100), null);
		Behavior bl = make((Class<? extends Behavior>) Class.forName(PKG + "Parallax"), lp, st);
		bl.setParam("factorX", 0f);
		bl.setParam("loopWidth", 300f);
		st.camXpx = 0;
		bl.engineStart();
		bl.engineUpdate();
		st.camXpx = 400;
		bl.engineUpdate();
		check(near(lp.actor.getX(), -100, 1e-2f), "Parallax: loop wraps -400 into -100 (width 300), x=" + lp.actor.getX());
		// physical: world element follows the camera by factor
		World w = new World(new Vector2(0, 0), true);
		BodyDef bd = new BodyDef();
		bd.type = BodyDef.BodyType.KinematicBody;
		bd.position.set(50, 0);
		Body body = w.createBody(bd);
		Item ph = new Item(box(0, 0, 10, 10), body);
		Behavior bp = make((Class<? extends Behavior>) Class.forName(PKG + "Parallax"), ph, st);
		bp.setParam("factorX", 0.75f);
		st.camXpx = 0;
		bp.engineStart();
		bp.engineUpdate();
		st.camXpx = 100;
		bp.engineUpdate();
		check(near(body.getPosition().x / 0.25f, 200 + 75, 0.1f), "Parallax (world): follows the camera by 0.75, x=" + body.getPosition().x / 0.25f);
		w.destroyBody(body);
	}

	static void testText() throws Exception {
		FakeStage st = newStage();
		Label l = label("Hello, world");
		Item it = new Item(l, null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Typewriter"), it, st);
		b.setParam("speed", 10f);
		b.setParam("punctuationPause", 0f);
		b.on("onDone", counter());
		b.engineStart();
		check(l.getText().length() == 0, "Typewriter: starts empty, got '" + l.getText() + "'");
		run(b, 0.52f);
		String half = l.getText().toString();
		check(half.equals("Hello"), "Typewriter: 5 letters after 0.5s at 10/s, got '" + half + "'");
		run(b, 1.5f);
		check(l.getText().toString().equals("Hello, world") && counter == 1, "Typewriter: full text and onDone once, got '" + l.getText() + "'");
		invoke(b, "typeText", "Bye");
		run(b, 0.22f);
		check(l.getText().toString().equals("By"), "Typewriter: type text block, got '" + l.getText() + "'");
		b.onClick();
		check(l.getText().toString().equals("Bye") && counter == 2, "Typewriter: tap shows everything");
		// the pause after punctuation
		Label l2 = label("a. b");
		Item it2 = new Item(l2, null);
		Behavior b2 = make((Class<? extends Behavior>) Class.forName(PKG + "Typewriter"), it2, st);
		b2.setParam("speed", 20f);
		b2.setParam("punctuationPause", 1f);
		b2.engineStart();
		run(b2, 0.2f);
		String s = l2.getText().toString();
		check(s.equals("a."), "Typewriter: stops after the dot, got '" + s + "'");
		run(b2, 0.5f);
		check(l2.getText().toString().equals("a."), "Typewriter: still waiting");
		run(b2, 1f);
		check(l2.getText().toString().equals("a. b"), "Typewriter: continues after the pause");
		b2.setEnabled(false);
		// sound
		Label l3 = label("abcdef");
		Behavior b3 = make((Class<? extends Behavior>) Class.forName(PKG + "Typewriter"), new Item(l3, null), st);
		b3.setParam("tickSound", "tick.wav");
		b3.setParam("tickEvery", 2);
		b3.engineStart();
		int before = st.soundCount;
		run(b3, 1.0f);
		check(st.soundCount - before == 3 && st.lastSound.equals("tick.wav"), "Typewriter: tick sound every 2 letters, " + (st.soundCount - before));

		// CountUp formatting
		Label c1 = label("0");
		Behavior cu = make((Class<? extends Behavior>) Class.forName(PKG + "CountUp"), new Item(c1, null), st);
		cu.setParam("from", 0f);
		cu.setParam("to", 1234567.891f);
		cu.setParam("decimals", 1);
		cu.setParam("prefix", "$");
		cu.setParam("suffix", " pts");
		cu.setParam("thousandsSeparator", true);
		cu.setParam("duration", 1f);
		cu.setParam("ease", "LINEAR");
		cu.on("onDone", counter());
		cu.engineStart();
		check(c1.getText().toString().equals("$0.0 pts"), "CountUp: start text, got '" + c1.getText() + "'");
		run(cu, 1.2f);
		check(c1.getText().toString().startsWith("$1,234,5") && c1.getText().toString().endsWith(" pts") && counter == 1, "CountUp: end text '" + c1.getText() + "'");
		invoke(cu, "setNumber", -1500.25f);
		check(c1.getText().toString().equals("-$1,500.3 pts") || c1.getText().toString().equals("$-1,500.3 pts") || c1.getText().toString().equals("$-1,500.2 pts"), "CountUp: negative number '" + c1.getText() + "'");
		Label c2 = label("");
		Behavior c2b = make((Class<? extends Behavior>) Class.forName(PKG + "CountUp"), new Item(c2, null), st);
		c2b.setParam("to", 100f);
		c2b.setParam("duration", 1f);
		c2b.setParam("ease", "LINEAR");
		c2b.engineStart();
		run(c2b, 0.5f);
		check(c2.getText().toString().equals("50"), "CountUp: halfway '50', got '" + c2.getText() + "'");
		invoke(c2b, "countTo", 0f);
		run(c2b, 2f);
		check(c2.getText().toString().equals("0"), "CountUp: count to 0, got '" + c2.getText() + "'");
		check(((Number) invoke(c2b, "getNumber")).floatValue() == 0f, "CountUp: shown number block");
	}

	static void testInput() throws Exception {
		FakeStage st = newStage();
		// ButtonSound
		Item it = new Item(box(0, 0, 10, 10), null);
		Behavior bs = make((Class<? extends Behavior>) Class.forName(PKG + "ButtonSound"), it, st);
		bs.setParam("sound", "sounds/click.wav");
		bs.setParam("releaseSound", "up.wav");
		bs.engineStart();
		bs.onTouchStart(ev(0, 0, 0));
		check(st.soundCount == 1 && st.lastSound.equals("click.wav") && st.releaseCount == 1, "ButtonSound: plays on touch start (path 'sounds/' removed), restart=" + st.releaseCount);
		bs.onTouchStart(ev(0, 0, 0));
		check(st.soundCount == 1, "ButtonSound: cooldown ignores a double hit");
		run(bs, 0.2f);
		bs.onTouchEnd(ev(0, 0, 0));
		check(st.soundCount == 2 && st.lastSound.equals("up.wav"), "ButtonSound: release sound");
		bs.setParam("when", "CLICK");
		run(bs, 0.2f);
		bs.onTouchStart(ev(0, 0, 0));
		check(st.soundCount == 2, "ButtonSound: CLICK mode ignores the touch start");
		bs.onClick();
		check(st.soundCount == 3 && st.lastSound.equals("click.wav"), "ButtonSound: plays on click");

		// OpenSceneOnClick
		FakeStage s2 = newStage();
		Item bt = new Item(box(0, 0, 10, 10), null);
		Behavior os = make((Class<? extends Behavior>) Class.forName(PKG + "OpenSceneOnClick"), bt, s2);
		os.setParam("scene", "level2");
		os.on("onOpening", counter());
		os.engineStart();
		os.onTouchStart(ev(0, 0, 0));
		check(s2.sceneCount == 0, "OpenScene: CLICK mode ignores the touch start");
		os.onClick();
		check(s2.sceneCount == 1 && s2.lastScene.equals("level2") && counter == 1, "OpenScene: opens the scene on click");
		FakeStage s3 = newStage();
		Behavior od = make((Class<? extends Behavior>) Class.forName(PKG + "OpenSceneOnClick"), bt, s3);
		od.setParam("scene", "menu");
		od.setParam("delay", 0.5f);
		od.engineStart();
		od.onClick();
		run(od, 0.3f);
		check(s3.sceneCount == 0, "OpenScene: waits for the delay");
		run(od, 0.4f);
		check(s3.sceneCount == 1, "OpenScene: opens after the delay");
		run(od, 1f);
		check(s3.sceneCount == 1, "OpenScene: opens only once");
		// with overlay
		FakeStage s4 = newStage();
		Item overlay = new Item(box(0, 0, 1000, 1000), null);
		overlay.actor.getColor().a = 0;
		overlay.actor.setVisible(false);
		Behavior oo = make((Class<? extends Behavior>) Class.forName(PKG + "OpenSceneOnClick"), bt, s4);
		oo.setParam("scene", "next");
		oo.setParam("fadeTime", 0.5f);
		oo.setParam("fadeOverlay", overlay.proxy);
		oo.engineStart();
		oo.onClick();
		run(oo, 0.25f);
		check(overlay.actor.isVisible() && near(overlay.actor.getColor().a, 0.5f, 0.05f) && s4.sceneCount == 0, "OpenScene: overlay fades in, a=" + overlay.actor.getColor().a);
		run(oo, 0.4f);
		check(s4.sceneCount == 1, "OpenScene: opens when the fade ends");
		// ToggleOnClick
		FakeStage s5 = newStage();
		Item p1 = new Item(box(0, 0, 10, 10), null), p2 = new Item(box(0, 0, 10, 10), null);
		Behavior tg = make((Class<? extends Behavior>) Class.forName(PKG + "ToggleOnClick"), bt, s5);
		((List<PlayerItem>) tg.getParam("targets")).add(p1.proxy);
		((List<PlayerItem>) tg.getParam("targets")).add(p2.proxy);
		tg.setParam("startHidden", true);
		tg.on("onToggled", counter());
		tg.engineStart();
		check(!p1.actor.isVisible() && !p2.actor.isVisible(), "Toggle: startHidden hides the targets");
		tg.onClick();
		check(p1.actor.isVisible() && p2.actor.isVisible() && counter == 1, "Toggle: click shows them");
		tg.onClick();
		check(!p1.actor.isVisible() && counter == 2, "Toggle: click hides them again");
		tg.setParam("action", "SHOW");
		tg.onClick();
		tg.onClick();
		check(p1.actor.isVisible() && p2.actor.isVisible() && (Boolean) invoke(tg, "isShown"), "Toggle: SHOW always shows");
	}

	static void testDraggable() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(100, 100, 40, 40), null); // center 120,120
		Item area = new Item(box(0, 0, 300, 300), null);
		Item target = new Item(box(400, 400, 60, 60), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Draggable"), it, st);
		b.setParam("dragScale", 1f);
		b.on("onDragStart", counter());
		b.engineStart();
		InputListener l = (InputListener) it.actor.getListeners().first();
		check(l.touchDown(ev(125, 125, 0), 5, 5, 0, 0), "Draggable: accepts the touch (and so receives the drag)");
		check(counter == 1 && (Boolean) invoke(b, "isDragging"), "Draggable: onDragStart");
		l.touchDragged(ev(175, 145, 0), 0, 0, 0);
		run(b, 0.05f);
		check(near(cx(it.actor), 170, 1e-3f) && near(cy(it.actor), 140, 1e-3f), "Draggable: follows the finger keeping the grab point, at " + cx(it.actor) + "," + cy(it.actor));
		l.touchUp(ev(175, 145, 0), 0, 0, 0, 0);
		check(!(Boolean) invoke(b, "isDragging") && near(cx(it.actor), 170, 1e-3f), "Draggable: stays where dropped");
		// area clamp
		b.setParam("area", area.proxy);
		l.touchDown(ev(170, 140, 0), 0, 0, 0, 0);
		l.touchDragged(ev(9000, 9000, 0), 0, 0, 0);
		run(b, 0.05f);
		check(near(cx(it.actor), 280, 1e-3f) && near(cy(it.actor), 280, 1e-3f), "Draggable: stays inside the area, at " + cx(it.actor) + "," + cy(it.actor));
		l.touchUp(ev(9000, 9000, 0), 0, 0, 0, 0);
		// lock + grid
		b.setParam("area", null);
		b.setParam("lockY", true);
		b.setParam("gridSize", 50f);
		l.touchDown(ev(280, 280, 0), 0, 0, 0, 0);
		l.touchDragged(ev(333, 10, 0), 0, 0, 0);
		run(b, 0.05f);
		check(near(cy(it.actor), 280, 1e-3f) && near(cx(it.actor), 333, 1e-3f), "Draggable: lockY keeps the height");
		l.touchUp(ev(333, 10, 0), 0, 0, 0, 0);
		check(near(cx(it.actor), 350, 1e-3f), "Draggable: snaps to the grid, x=" + cx(it.actor));
		// a second finger is ignored while one drags
		b.setParam("lockY", false);
		l.touchDown(ev(350, 280, 0), 0, 0, 0, 0);
		check(!l.touchDown(ev(0, 0, 1), 0, 0, 1, 0), "Draggable: second finger ignored");
		l.touchUp(ev(350, 280, 0), 0, 0, 0, 0);
		// drop on a target
		b.setParam("gridSize", 0f);
		((List<PlayerItem>) b.getParam("dropTargets")).add(target.proxy);
		Item holder = it;
		final int[] dropOn = {0};
		b.on("onDropOn", new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				dropOn[0]++;
			}
		});
		l.touchDown(ev(350, 280, 0), 0, 0, 0, 0);
		l.touchDragged(ev(420, 420, 0), 0, 0, 0);
		run(b, 0.05f);
		l.touchUp(ev(420, 420, 0), 0, 0, 0, 0);
		check(dropOn[0] == 1 && near(cx(it.actor), 430, 1e-3f) && near(cy(it.actor), 430, 1e-3f), "Draggable: onDropOn + snaps to the target center, at " + cx(it.actor) + "," + cy(it.actor));
		// snap back
		b.setParam("snapBack", true);
		b.setParam("snapBackTime", 0.5f);
		b.setParam("dropTargets", new ArrayList<PlayerItem>());
		invoke(b, "setHomeHere");
		l.touchDown(ev(430, 430, 0), 0, 0, 0, 0);
		l.touchDragged(ev(100, 50, 0), 0, 0, 0);
		run(b, 0.05f);
		l.touchUp(ev(100, 50, 0), 0, 0, 0, 0);
		run(b, 0.3f);
		float mid = cx(it.actor);
		check(mid > 110 && mid < 430, "Draggable: travelling home, x=" + mid);
		run(b, 0.5f);
		check(near(cx(it.actor), 430, 1e-3f) && near(cy(it.actor), 430, 1e-3f), "Draggable: back home");
		// disabled -> ignores touches
		b.setEnabled(false);
		check(!l.touchDown(ev(430, 430, 0), 0, 0, 0, 0), "Draggable: disabled ignores touches");
		b.setEnabled(true);
		b.engineDestroy();
		check(it.actor.getListeners().size == 0, "Draggable: destroy removes its listener");
	}

	static void testGestures() throws Exception {
		FakeStage st = newStage();
		Item it = new Item(box(0, 0, 100, 100), null);
		// LongPress
		Behavior lp = make((Class<? extends Behavior>) Class.forName(PKG + "LongPress"), it, st);
		lp.setParam("holdTime", 0.5f);
		final int[] ev = new int[2];
		lp.on("onLongPress", new Behavior.Handler() {
			public void run(PlayerItem c, Object[] a) {
				ev[0]++;
			}
		});
		lp.on("onShortPress", new Behavior.Handler() {
			public void run(PlayerItem c, Object[] a) {
				ev[1]++;
			}
		});
		lp.engineStart();
		touchX = 100;
		touchY = 100;
		lp.onTouchStart(ev(0, 0, 0));
		run(lp, 0.3f);
		lp.onTouchEnd(ev(0, 0, 0));
		check(ev[0] == 0 && ev[1] == 1, "LongPress: a quick tap is a short press");
		lp.onTouchStart(ev(0, 0, 0));
		run(lp, 0.7f);
		check(ev[0] == 1, "LongPress: fires once after the hold time, got " + ev[0] + " short " + ev[1]);
		lp.onTouchEnd(ev(0, 0, 0));
		check(ev[0] == 1 && ev[1] == 1, "LongPress: releasing after a long press is not a short press");
		lp.onTouchStart(ev(0, 0, 0));
		run(lp, 0.2f);
		touchX = 300;
		run(lp, 0.5f);
		lp.onTouchEnd(ev(0, 0, 0));
		check(ev[0] == 1 && ev[1] == 1, "LongPress: moving away cancels");
		lp.setParam("repeat", true);
		touchX = 100;
		lp.onTouchStart(ev(0, 0, 0));
		run(lp, 1.3f);
		check(ev[0] == 3, "LongPress: repeat fires every hold time, " + ev[0]);

		// SwipeDetector
		Behavior sw = make((Class<? extends Behavior>) Class.forName(PKG + "SwipeDetector"), it, st);
		final java.util.ArrayList<String> got = new java.util.ArrayList<String>();
		for (final String e : new String[]{"onSwipeLeft", "onSwipeRight", "onSwipeUp", "onSwipeDown"})
			sw.on(e, new Behavior.Handler() {
				public void run(PlayerItem c, Object[] a) {
					got.add(e);
				}
			});
		sw.engineStart();
		float[][] moves = {{300, 300, 100, 300}, {100, 300, 300, 300}, {300, 300, 300, 100}, {300, 100, 300, 300}, {300, 300, 330, 310}};
		String[] want = {"onSwipeLeft", "onSwipeRight", "onSwipeUp", "onSwipeDown"};
		for (int i = 0; i < moves.length; i++) {
			touched = true;
			touchX = moves[i][0];
			touchY = moves[i][1];
			run(sw, 0.05f);
			touchX = moves[i][2];
			touchY = moves[i][3];
			run(sw, 0.05f);
			touched = false;
			run(sw, 0.05f);
		}
		check(got.equals(Arrays.asList(want)), "SwipeDetector: left/right/up/down and ignores a tiny move: " + got);
		// slow drag is not a swipe
		got.clear();
		touched = true;
		touchX = 0;
		touchY = 0;
		run(sw, 0.05f);
		touchX = 500;
		run(sw, 2f);
		touched = false;
		run(sw, 0.05f);
		check(got.isEmpty(), "SwipeDetector: too slow is ignored");
		// while moving
		sw.setParam("fireWhileMoving", true);
		touched = true;
		touchX = 0;
		run(sw, 0.05f);
		touchX = 200;
		run(sw, 0.05f);
		check(got.size() == 1, "SwipeDetector: fires before the finger lifts, " + got);
		touchX = 400;
		run(sw, 0.05f);
		touched = false;
		run(sw, 0.05f);
		check(got.size() == 1, "SwipeDetector: only once per swipe");
		// only on element
		got.clear();
		sw.setParam("fireWhileMoving", false);
		sw.setParam("onlyOnElement", true);
		touched = true;
		touchX = 0;
		touchY = 0;
		run(sw, 0.05f);
		touchX = 300;
		run(sw, 0.05f);
		touched = false;
		run(sw, 0.05f);
		check(got.isEmpty(), "SwipeDetector: a swipe that starts elsewhere is ignored in 'only on element' mode");
		sw.onTouchStart(ev(0, 0, 0));
		touched = true;
		touchX = 0;
		run(sw, 0.05f);
		touchX = 300;
		run(sw, 0.05f);
		touched = false;
		run(sw, 0.05f);
		check(got.size() == 1, "SwipeDetector: a swipe that starts on the element counts");
		touched = false;
	}

	static void testProgress() throws Exception {
		FakeStage st = newStage();
		FakeBar bar = alloc(FakeBar.class);
		bar.max = 200;
		bar.a = box(0, 0, 100, 10);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "ProgressBind"), new Item(bar.a, null), st);
		b.item = bar;
		b.setParam("valueName", "hp");
		b.setParam("maxValue", 50f);
		b.setParam("smoothing", 0f);
		b.on("onFull", counter());
		final int[] empty = new int[1];
		b.on("onEmpty", new Behavior.Handler() {
			public void run(PlayerItem c, Object[] a) {
				empty[0]++;
			}
		});
		b.engineStart();
		st.values.put("hp", "25");
		run(b, 0.3f);
		check(near(bar.progress, 100, 0.01f), "ProgressBind: saved 25 of 50 -> half of the bar's max, got " + bar.progress);
		st.values.put("hp", "999");
		run(b, 0.3f);
		check(near(bar.progress, 200, 0.01f) && counter == 1, "ProgressBind: clamped to full, onFull once");
		st.values.put("hp", "0");
		run(b, 0.3f);
		check(bar.progress == 0 && empty[0] == 1, "ProgressBind: empty + onEmpty");
		// smoothing
		b.setParam("smoothing", 5f);
		st.values.put("hp", "50");
		run(b, 0.2f);
		check(bar.progress > 20 && bar.progress < 200, "ProgressBind: smooth, " + bar.progress);
		run(b, 3f);
		check(near(bar.progress, 200, 0.5f), "ProgressBind: smooth arrives");
		// from a text
		Label txt = label("Score: 1,250 pts");
		Item ti = new Item(txt, null);
		FakeBar bar2 = alloc(FakeBar.class);
		bar2.a = box(0, 0, 10, 10);
		bar2.max = 100;
		Behavior b2 = make((Class<? extends Behavior>) Class.forName(PKG + "ProgressBind"), new Item(bar2.a, null), st);
		b2.item = bar2;
		b2.setParam("source", "OTHER_TEXT");
		b2.setParam("other", ti.proxy);
		b2.setParam("maxValue", 5000f);
		b2.setParam("smoothing", 0f);
		b2.engineStart();
		run(b2, 0.3f);
		check(near(bar2.progress, 25, 0.01f), "ProgressBind: number read from a text 1250/5000, got " + bar2.progress);
		// manual + other bar
		b2.setParam("source", "MANUAL");
		invoke(b2, "setBarValue", 2500f);
		run(b2, 0.3f);
		check(near(bar2.progress, 50, 0.01f), "ProgressBind: manual");
		FakeBar src = alloc(FakeBar.class);
		src.progress = 40;
		src.a = box(0, 0, 10, 10);
		b2.setParam("source", "OTHER_PROGRESS");
		b2.setParam("other", src);
		b2.setParam("maxValue", 80f);
		run(b2, 0.3f);
		check(near(bar2.progress, 50, 0.01f), "ProgressBind: other progress 40/80, got " + bar2.progress);
	}

	// ------------------------------------------------------------------ no allocation per frame

	static long allocated() {
		return ((com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().getId());
	}

	static void allocCheck(String name, Behavior b, float perFrameLimit) {
		for (int i = 0; i < 1500; i++) b.onUpdate(DT); // warm up (JIT, lazy fields)
		long a0 = allocated();
		int n = 6000;
		for (int i = 0; i < n; i++) b.onUpdate(DT);
		float per = (allocated() - a0) / (float) n;
		System.out.println("  alloc " + name + ": " + per + " B/frame");
		check(per <= perFrameLimit, name + ": allocates " + per + " bytes per frame (limit " + perFrameLimit + ")");
	}

	static void testAllocations() throws Exception {
		FakeStage st = newStage();
		Class<? extends Behavior> c;
		Item it = new Item(box(100, 100, 40, 40), null);
		Behavior b = make((Class<? extends Behavior>) Class.forName(PKG + "Pulse"), it, st);
		b.engineStart();
		allocCheck("Pulse", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "Blink"), new Item(box(0, 0, 4, 4), null), st);
		b.setParam("smooth", true);
		b.engineStart();
		allocCheck("Blink", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "Shake"), new Item(box(0, 0, 4, 4), null), st);
		b.setParam("trigger", "ON_START");
		b.setParam("loop", true);
		b.setParam("rotation", 4f);
		b.engineStart();
		allocCheck("Shake", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "Wobble"), new Item(box(0, 0, 4, 4), null), st);
		b.setParam("squash", 0.1f);
		b.engineStart();
		allocCheck("Wobble", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "SlideIn"), new Item(box(0, 0, 4, 4), null), st);
		b.setParam("duration", 1000f);
		b.engineStart();
		allocCheck("SlideIn", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "FadeIn"), new Item(box(0, 0, 4, 4), null), st);
		b.setParam("duration", 1000f);
		b.engineStart();
		allocCheck("FadeIn", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "TintCycle"), new Item(box(0, 0, 4, 4), null), st);
		b.engineStart();
		allocCheck("TintCycle", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "TintCycle"), new Item(box(0, 0, 4, 4), null), st);
		b.setParam("mode", "RAINBOW");
		b.engineStart();
		allocCheck("TintCycle rainbow", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "Parallax"), new Item(box(0, 0, 4, 4), null), st);
		b.engineStart();
		st.camXpx = 5;
		allocCheck("Parallax", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "CountUp"), new Item(label("0"), null), st);
		b.setParam("to", 1e9f);
		b.setParam("duration", 100000f);
		b.setParam("decimals", 2);
		b.setParam("thousandsSeparator", true);
		b.engineStart();
		allocCheck("CountUp", b, 8);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "LongPress"), new Item(box(0, 0, 4, 4), null), st);
		b.engineStart();
		allocCheck("LongPress (idle)", b, 1);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "SwipeDetector"), new Item(box(0, 0, 4, 4), null), st);
		b.engineStart();
		touched = false;
		allocCheck("SwipeDetector (idle)", b, 40); // the fake Gdx.input (a java Proxy) allocates for every call
		FakeBar bar = alloc(FakeBar.class);
		bar.a = box(0, 0, 10, 10);
		b = make((Class<? extends Behavior>) Class.forName(PKG + "ProgressBind"), new Item(bar.a, null), st);
		b.item = bar;
		b.setParam("source", "MANUAL");
		b.setParam("smoothing", 3f);
		b.engineStart();
		invoke(b, "setBarValue", 70f);
		allocCheck("ProgressBind", b, 1);
	}

	static String fmt(Object o) {
		if (o instanceof Double) {
			double d = (Double) o;
			return d == Math.rint(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf((float) d);
		}
		return String.valueOf(o);
	}

	/** Prints the reference tables of docs/behaviors/ui-effects.md (generated from the annotations, so they never go stale). */
	static void docs() throws Exception {
		StringBuilder md = new StringBuilder();
		md.append("| id | icon | category | description |\n|---|---|---|---|\n");
		List<BehaviorMeta> metas = new ArrayList<BehaviorMeta>();
		List<Class<?>> classes = new ArrayList<Class<?>>();
		for (String n : NAMES) {
			Class<?> c = Class.forName(PKG + n);
			classes.add(c);
			BehaviorMeta m = ReflectScanner.behavior(c);
			metas.add(m);
			md.append("| `" + m.id + "` | `" + m.icon + "` | " + m.category + " | " + m.desc.replace("|", "/") + " |\n");
		}
		md.append("\n");
		for (int i = 0; i < metas.size(); i++) {
			BehaviorMeta m = metas.get(i);
			md.append("## " + m.name + "  (`" + m.id + "`)\n\n" + m.desc + "\n\n");
			md.append("- Icon: `" + m.icon + "` · Category: " + m.category + " · Works on: " + (m.targets.equals("BOTH") ? "any element" : m.targets) + "\n");
			if (!m.events.isEmpty()) {
				md.append("- Events:");
				for (BehaviorMeta.EventSpec e : m.events) {
					md.append(" `" + e.name + "(");
					for (int k = 0; k < e.args.size(); k++) md.append((k > 0 ? ", " : "") + e.args.get(k)[0] + " " + e.args.get(k)[1]);
					md.append(")`");
				}
				md.append("\n");
			}
			md.append("\n| parameter | default | range | description |\n|---|---|---|---|\n");
			for (VarSpec v : m.vars) {
				String range = "";
				if (v.kind == VarSpec.Kind.ENUM) range = v.options.toString().replace("[", "").replace("]", "");
				else if (v.isNumber()) range = fmt(v.min) + " .. " + fmt(v.max) + (v.unit.length() > 0 ? " " + v.unit : "");
				else if (v.kind == VarSpec.Kind.BOOL) range = "on / off";
				else if (v.kind == VarSpec.Kind.REF) range = v.list ? "list of elements" : "element";
				else if (v.kind == VarSpec.Kind.SOUND) range = "sound file";
				String def = v.def == null ? "–" : fmt(v.def);
				if (v.list) def = "empty";
				if (v.kind == VarSpec.Kind.REF && !v.list) def = "none";
				if (v.kind == VarSpec.Kind.STRING && def.length() == 0) def = "(empty)";
				if (v.kind == VarSpec.Kind.SOUND && def.length() == 0) def = "(none)";
				md.append("| `" + v.name + "` | " + def + " | " + range + " | " + v.desc.replace("|", "/") + " |\n");
			}
			md.append("\nBlocks:\n\n");
			for (Method me : classes.get(i).getDeclaredMethods()) {
				Block b = me.getAnnotation(Block.class);
				if (b == null) continue;
				StringBuilder args = new StringBuilder();
				String[] an = b.args();
				for (int k = 0; k < me.getParameterTypes().length; k++) args.append(k > 0 ? ", " : "").append(k < an.length ? an[k] : me.getParameterTypes()[k].getSimpleName());
				md.append("- **" + b.label() + "**" + (args.length() > 0 ? " (" + args + ")" : "") + (me.getReturnType() == void.class ? "" : " → " + me.getReturnType().getSimpleName()) + " – " + b.desc() + " · " + b.labelAr() + "\n");
			}
			md.append("\n");
		}
		System.out.print(md);
	}

	public static void main(String[] a) throws Exception {
		root = a[0];
		if (a.length > 1 && a[1].equals("--docs")) {
			docs();
			return;
		}
		Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		uf.setAccessible(true);
		unsafe = (sun.misc.Unsafe) uf.get(null);
		GdxNativesLoader.load();
		installGdx();
		makeRoot();
		metadata();
		String only = a.length > 1 ? a[1] : "";
		String[] sims = {"Pulse", "Blink", "Fade", "Shake", "Slide", "Wobble", "Physical", "Tint", "Parallax", "Text", "Input", "Draggable", "Gestures", "Progress", "Allocations"};
		for (String s : sims) {
			if (only.length() > 0 && !only.equals(s)) continue;
			try {
				LibUiTest.class.getDeclaredMethod("test" + s).invoke(null);
			} catch (java.lang.reflect.InvocationTargetException ex) {
				failures++;
				System.out.println("FAIL test" + s + " threw " + ex.getCause());
				StackTraceElement[] tr = ex.getCause().getStackTrace();
				for (int i = 0; i < Math.min(6, tr.length); i++) System.out.println("    at " + tr[i]);
			}
		}
		System.out.println((failures == 0 ? "OK" : "FAILED") + ": " + checks + " checks, " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}
}
