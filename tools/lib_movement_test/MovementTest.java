import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.behaviors.BehaviorMeta;
import com.star4droid.star2d.behaviors.ReflectScanner;
import com.star4droid.star2d.behaviors.SourceScanner;
import com.star4droid.star2d.behaviors.VarSpec;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;
import com.star4droid.template.behaviors.*;
import com.star4droid.template.behaviors.lib.*;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

/**
 * Run by tools/test_lib_movement.py. Part 1: annotation completeness of the movement/AI/vehicle behaviors (icon in the catalog, descriptions,
 * @Param on every public field with a sane range, @Block texts, events fired = events declared, no android.*, textual scanner == reflection).
 * Part 2: a headless Box2D smoke simulation (no GL): fake stage + fake elements, Gdx.input replaced by a scripted proxy.
 */
public class MovementTest {
	static final String[] NAMES = {"TopDownPlayer", "SpaceShip", "Car", "Follow", "Patrol", "Chase", "MovingPlatform", "JumpPad", "Bounce", "WrapScreen",
			"LimitToScreen", "LookAtTarget", "Orbit", "TapToMove", "TiltMove"};
	static final float W = 0.25f;
	static int failures = 0, checks = 0;

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
		metadata(root);
		simulate();
		System.out.println(checks + " checks, " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ================================================================= part 1: metadata

	static void metadata(String root) throws Exception {
		Set<String> icons = new HashSet<String>();
		JsonObject cat = new Gson().fromJson(read(new File(root, "app/src/main/assets/icons/catalog.json")), JsonObject.class);
		for (JsonElement e : cat.getAsJsonArray("icons")) icons.add(e.getAsJsonObject().get("i").getAsString());
		Set<String> categories = new HashSet<String>(Arrays.asList("Movement", "AI", "Vehicles"));
		File lib = new File(root, "app/src/main/java/com/star4droid/template/behaviors/lib");
		Set<String> usedIcons = new HashSet<String>();
		for (String id : NAMES) {
			File f = new File(lib, id + ".java");
			check(f.exists(), id + ": file exists");
			if (!f.exists()) continue;
			String src = read(f);
			Class<?> c = Class.forName("com.star4droid.template.behaviors.lib." + id);
			BehaviorMeta m = ReflectScanner.behavior(c);
			check(m != null, id + ": reflection metadata");
			if (m == null) continue;
			check(m.name.length() >= 3, id + ": name");
			check(m.desc.length() >= 40 && m.desc.length() <= 420, id + ": desc length " + m.desc.length());
			check(icons.contains(m.icon), id + ": icon '" + m.icon + "' is in the catalog");
			check(usedIcons.add(m.icon), id + ": icon '" + m.icon + "' is unique inside the movement library");
			check(categories.contains(m.category), id + ": category " + m.category);
			String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
			check(!code.contains("android.") && !code.contains("->") && !code.contains("::"), id + ": no android.* / lambdas");
			check(src.contains("import com.star4droid.template.behaviors.Block;"), id + ": has blocks");
			boolean controller = Arrays.asList("TopDownPlayer", "SpaceShip", "Car", "TiltMove", "TapToMove").contains(id);
			check(controller == m.exclusive.equals("movement"), id + ": @Exclusive(\"movement\") exactly on controllers (" + m.exclusive + ")");
			if (Arrays.asList("MovingPlatform", "Orbit", "SpaceShip", "TopDownPlayer", "Bounce").contains(id))
				check(!m.sets.isEmpty(), id + ": @Sets present");
			if (Arrays.asList("Car", "MovingPlatform", "JumpPad").contains(id)) check(m.reqBody, id + ": @Requires(body)");

			// events
			Set<String> declared = new HashSet<String>();
			for (BehaviorMeta.EventSpec e : m.events) declared.add(e.name);
			Matcher fm = Pattern.compile("\"(on[A-Z]\\w*)\"").matcher(code);
			Set<String> fired = new HashSet<String>();
			while (fm.find()) fired.add(fm.group(1));
			for (String e : fired) check(declared.contains(e), id + ": fires undeclared event " + e);
			for (String e : declared) check(fired.contains(e), id + ": declared event never fired " + e);

			// parameters
			Object inst = c.newInstance();
			int nFields = 0;
			for (Field fl : c.getFields()) {
				int mod = fl.getModifiers();
				if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || fl.isAnnotationPresent(Hidden.class) || fl.getDeclaringClass() == Behavior.class) continue;
				nFields++;
				Param p = fl.getAnnotation(Param.class);
				check(p != null, id + "." + fl.getName() + ": has @Param");
				if (p == null) continue;
				check(p.desc().length() >= 8, id + "." + fl.getName() + ": param desc");
				Class<?> t = fl.getType();
				boolean num = t == float.class || t == int.class || t == double.class;
				if (num) {
					check(p.min() > -1e8 && p.max() < 1e8 && p.max() > p.min(), id + "." + fl.getName() + ": explicit min/max");
					double v = ((Number) fl.get(inst)).doubleValue();
					check(v >= p.min() - 1e-6 && v <= p.max() + 1e-6, id + "." + fl.getName() + ": default " + v + " within [" + p.min() + "," + p.max() + "]");
					check(!(t == float.class && (fl.getName().toLowerCase().contains("speed") || fl.getName().toLowerCase().contains("distance") || fl.getName().toLowerCase().contains("range")) && p.unit().isEmpty()),
							id + "." + fl.getName() + ": unit");
				}
			}
			check(nFields >= 3, id + ": at least 3 parameters (" + nFields + ")");

			// blocks
			int blocks = 0;
			for (Method me : c.getDeclaredMethods()) {
				Block b = me.getAnnotation(Block.class);
				if (b == null) continue;
				blocks++;
				String w = id + "." + me.getName();
				check(b.label().length() >= 3 && b.desc().length() >= 8 && b.labelAr().length() >= 2 && b.descAr().length() >= 4 && b.category().length() >= 3, w + ": @Block texts");
				check(b.args().length == me.getParameterTypes().length, w + ": @Block args names " + b.args().length + " vs " + me.getParameterTypes().length);
				check(Modifier.isPublic(me.getModifiers()), w + ": public");
			}
			check(blocks >= 3, id + ": at least 3 blocks (" + blocks + ")");

			// the textual scanner (project/AI view) must agree with reflection
			BehaviorMeta s = SourceScanner.behavior(src, f.getPath());
			check(s != null, id + ": text scanner finds the class");
			if (s != null) {
				check(s.name.equals(m.name) && s.icon.equals(m.icon) && s.desc.equals(m.desc) && s.category.equals(m.category) && s.targets.equals(m.targets), id + ": scanner info equal");
				check(s.exclusive.equals(m.exclusive) && s.reqBody == m.reqBody && s.reqType.equals(m.reqType) && s.sets.equals(m.sets), id + ": scanner exclusive/requires/sets");
				check(s.events.size() == m.events.size(), id + ": scanner events");
				check(s.vars.size() == m.vars.size(), id + ": scanner var count " + s.vars.size() + " vs " + m.vars.size());
				for (int i = 0; i < Math.min(s.vars.size(), m.vars.size()); i++) {
					VarSpec x = s.vars.get(i), y = m.vars.get(i);
					boolean same = x.name.equals(y.name) && x.kind == y.kind && x.list == y.list && x.array == y.array && x.elemType.equals(y.elemType.replace('$', '.'))
							&& x.header.equals(y.header) && x.desc.equals(y.desc) && x.unit.equals(y.unit) && x.min == y.min && x.max == y.max && x.step == y.step;
					check(same, id + "." + x.name + ": scanner spec == reflection  " + x + " / " + y);
					Object dx = x.def, dy = y.def;
					boolean sameDef = (dx == null && (dy == null || (dy instanceof List && ((List<?>) dy).isEmpty() && x.list))) || (dx != null && dx.equals(dy))
							|| (dx instanceof Number && dy instanceof Number && Math.abs(((Number) dx).doubleValue() - ((Number) dy).doubleValue()) < 1e-4);
					check(sameDef, id + "." + x.name + ": default scanner == reflection " + dx + " / " + dy);
				}
			}
		}
	}

	// ================================================================= part 2: simulation

	static final Set<Integer> keys = new HashSet<Integer>();
	static boolean touched, accel;
	static float touchX, touchY, accX, accY;

	static Object zero(Class<?> t) {
		if (t == boolean.class) return false;
		if (t == int.class) return 0;
		if (t == float.class) return 0f;
		if (t == long.class) return 0L;
		if (t == double.class) return 0d;
		return null;
	}

	static void installGdx() {
		Gdx.input = (Input) Proxy.newProxyInstance(MovementTest.class.getClassLoader(), new Class[] {Input.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] args) {
				String n = m.getName();
				if (n.equals("isKeyPressed")) return keys.contains((Integer) args[0]);
				if (n.equals("isTouched")) return touched;
				if (n.equals("getX")) return (int) touchX;
				if (n.equals("getY")) return (int) touchY;
				if (n.equals("isPeripheralAvailable")) return accel;
				if (n.equals("getAccelerometerX")) return accX;
				if (n.equals("getAccelerometerY")) return accY;
				return zero(m.getReturnType());
			}
		});
		Gdx.graphics = (com.badlogic.gdx.Graphics) Proxy.newProxyInstance(MovementTest.class.getClassLoader(), new Class[] {com.badlogic.gdx.Graphics.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] args) {
				String n = m.getName();
				if (n.equals("getWidth") || n.equals("getBackBufferWidth")) return 720;
				if (n.equals("getHeight") || n.equals("getBackBufferHeight")) return 1560;
				if (n.equals("getDeltaTime")) return DT;
				return zero(m.getReturnType());
			}
		});
		Gdx.gl = (com.badlogic.gdx.graphics.GL20) Proxy.newProxyInstance(MovementTest.class.getClassLoader(), new Class[] {com.badlogic.gdx.graphics.GL20.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] args) {
				return zero(m.getReturnType());
			}
		});
		Gdx.gl20 = (com.badlogic.gdx.graphics.GL20) Gdx.gl;
		Gdx.app = (Application) Proxy.newProxyInstance(MovementTest.class.getClassLoader(), new Class[] {Application.class}, new InvocationHandler() {
			public Object invoke(Object p, Method m, Object[] args) {
				if (m.getName().equals("error") || m.getName().equals("log")) System.out.println("  [gdx " + m.getName() + "] " + Arrays.toString(args));
				return zero(m.getReturnType());
			}
		});
	}

	/** A fake element: a Box2D body (world units) or a UI actor (px). */
	static class Item extends Actor implements PlayerItem {
		Body body;
		Actor actor = this;

		public void update() {}

		public Body getBody() {
			return body;
		}

		public com.star4droid.star2d.ElementDefs.ElementEvent getElementEvents() {
			return null;
		}

		public void setScript(com.star4droid.template.Utils.ItemScript script) {}

		@SuppressWarnings("unchecked")
		public <T extends com.star4droid.template.Utils.ItemScript> T getScript() {
			return null;
		}

		public com.star4droid.template.Utils.ChildsHolder getChildsHolder() {
			return null;
		}

		public com.star4droid.star2d.ElementDefs.ItemDef getProperties() {
			return null;
		}

		public PlayerItem getClone(String newName) {
			return null;
		}
	}

	static StageImp stage;
	static final com.badlogic.gdx.graphics.g2d.Batch BATCH = (com.badlogic.gdx.graphics.g2d.Batch) Proxy.newProxyInstance(MovementTest.class.getClassLoader(),
			new Class[] {com.badlogic.gdx.graphics.g2d.Batch.class}, new InvocationHandler() {
				public Object invoke(Object p, Method m, Object[] args) {
					return zero(m.getReturnType());
				}
			});
	static final float DT = 1f / 60f;

	static void newStage(float gx, float gy) {
		stage = new StageImp();
		stage.world.dispose();
		stage.world = new World(new Vector2(gx, gy), true);
		stage.setSteps(1);
		stage.GameStage = new Stage(new FitViewport(720, 1560), BATCH);
		stage.GameStage.getViewport().update(720, 1560, true);
		OrthographicCamera cam = (OrthographicCamera) stage.GameStage.getCamera();
		cam.zoom = W;
		cam.position.set(720 * W / 2f, 1560 * W / 2f, 0);
		cam.update();
		stage.UiStage = new Stage(new FitViewport(720, 1560), BATCH);
		stage.UiStage.getViewport().update(720, 1560, true);
		keys.clear();
		touched = false;
		accel = false;
		accX = 0;
		accY = 0;
	}

	static Item phys(float x, float y, float wPx, float hPx, BodyDef.BodyType type, boolean circle, float friction) {
		Item it = new Item();
		BodyDef bd = new BodyDef();
		bd.type = type;
		bd.position.set(x * W, y * W);
		it.body = stage.world.createBody(bd);
		FixtureDef fd = new FixtureDef();
		if (circle) {
			CircleShape cs = new CircleShape();
			cs.setRadius(wPx * 0.5f * W);
			fd.shape = cs;
		} else {
			PolygonShape ps = new PolygonShape();
			ps.setAsBox(wPx * 0.5f * W, hPx * 0.5f * W);
			fd.shape = ps;
		}
		fd.density = 1f;
		fd.friction = friction;
		fd.restitution = 0f;
		it.body.createFixture(fd);
		it.actor.setSize(wPx * W, hPx * W);
		it.actor.setName("item");
		stage.GameStage.addActor(it);
		return it;
	}

	static Item ui(float x, float y, float wPx, float hPx) {
		Item it = new Item();
		it.actor.setSize(wPx, hPx);
		it.actor.setPosition(x - wPx / 2, y - hPx / 2);
		stage.UiStage.addActor(it);
		return it;
	}

	static <T extends Behavior> T attach(T b, Item it) {
		b.item = it;
		b.stage = stage;
		b.engineStart();
		return b;
	}

	static void run(float seconds, Behavior... bs) {
		int frames = Math.round(seconds / DT);
		for (int i = 0; i < frames; i++) {
			for (Behavior b : bs) b.onUpdate(DT);
			stage.world.step(DT, 6, 2);
		}
	}

	static float px(Item it) {
		return it.body.getPosition().x / W;
	}

	static float py(Item it) {
		return it.body.getPosition().y / W;
	}

	static float vxPx(Item it) {
		return it.body.getLinearVelocity().x / W;
	}

	static float vyPx(Item it) {
		return it.body.getLinearVelocity().y / W;
	}

	static float deg(Item it) {
		return (float) Math.toDegrees(it.body.getAngle());
	}

	static boolean finite(float... v) {
		for (float f : v) if (Float.isNaN(f) || Float.isInfinite(f)) return false;
		return true;
	}

	static int count(final int[] c) {
		return c[0];
	}

	static Behavior.Handler counter(final int[] c) {
		return new Behavior.Handler() {
			public void run(PlayerItem current, Object[] args) {
				c[0]++;
			}
		};
	}

	static void simulate() {
		com.badlogic.gdx.utils.GdxNativesLoader.load();
		installGdx();
		topDown();
		spaceShip();
		patrol();
		car();
		chase();
		movingPlatform();
		jumpPad();
		bounce();
		wrapAndLimit();
		orbitLookFollow();
		tapAndTilt();
	}

	static void topDown() {
		newStage(0, 0);
		Item p = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		TopDownPlayer t = attach(new TopDownPlayer(), p);
		keys.add(Input.Keys.RIGHT);
		run(1f, t);
		check(px(p) - 360 > 150 && px(p) - 360 < 215 && Math.abs(py(p) - 780) < 1, "TopDownPlayer: right ~200px/s -> dx=" + (px(p) - 360) + " dy=" + (py(p) - 780));
		keys.clear();
		run(0.5f, t);
		check(Math.abs(vxPx(p)) < 2, "TopDownPlayer: friction stops it " + vxPx(p));
		float y0 = py(p);
		keys.add(Input.Keys.UP);
		run(1f, t);
		check(py(p) - y0 > 150, "TopDownPlayer: UP moves y up " + (py(p) - y0));
		check(finite(px(p), py(p)), "TopDownPlayer: finite");
		check(t.isMoving(), "TopDownPlayer: isMoving");

		newStage(0, 0);
		p = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		t = attach(new TopDownPlayer(), p);
		t.directions = TopDownPlayer.Directions.FOUR;
		keys.add(Input.Keys.RIGHT);
		keys.add(Input.Keys.UP);
		run(0.6f, t);
		check(Math.abs(py(p) - 780) < 1 && px(p) > 400, "TopDownPlayer FOUR: only one axis (dx=" + (px(p) - 360) + ", dy=" + (py(p) - 780) + ")");

		newStage(0, 0);
		p = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		t = attach(new TopDownPlayer(), p);
		t.directions = TopDownPlayer.Directions.EIGHT;
		t.rotateToDirection = true;
		keys.add(Input.Keys.UP);
		run(0.5f, t);
		check(Math.abs(deg(p) - 90) < 1, "TopDownPlayer: faces up -> " + deg(p));
		check(p.body.isFixedRotation(), "TopDownPlayer: fixed rotation set at runtime (@Sets)");

		newStage(0, 0);
		Item u = ui(200, 200, 50, 50);
		t = attach(new TopDownPlayer(), u);
		keys.add(Input.Keys.RIGHT);
		run(1f, t);
		float ux = u.actor.getX() + 25;
		check(ux - 200 > 150 && ux - 200 < 215, "TopDownPlayer UI: moves right " + (ux - 200));
	}

	static void spaceShip() {
		newStage(0, 0);
		Item p = phys(360, 780, 40, 60, BodyDef.BodyType.DynamicBody, false, 0f);
		SpaceShip s = attach(new SpaceShip(), p);
		keys.add(Input.Keys.UP);
		run(1f, s);
		check(py(p) - 780 > 60 && Math.abs(px(p) - 360) < 1, "SpaceShip: thrust goes up (nose 90) dy=" + (py(p) - 780) + " dx=" + (px(p) - 360));
		keys.clear();
		keys.add(Input.Keys.LEFT);
		run(0.45f, s);
		check(deg(p) > 80 && deg(p) < 100, "SpaceShip: LEFT turns counter-clockwise ~90deg -> " + deg(p));
		keys.clear();
		float x0 = px(p);
		keys.add(Input.Keys.UP);
		run(2f, s);
		check(px(p) < x0 - 20, "SpaceShip: after turning left the thrust pushes left dx=" + (px(p) - x0));
		check(Math.hypot(vxPx(p), vyPx(p)) <= s.maxSpeed + 1, "SpaceShip: speed limit " + Math.hypot(vxPx(p), vyPx(p)));
		check(finite(px(p), py(p)), "SpaceShip: finite");
		// boost
		newStage(0, 0);
		p = phys(360, 780, 40, 60, BodyDef.BodyType.DynamicBody, false, 0f);
		s = attach(new SpaceShip(), p);
		final int[] boosts = {0};
		s.on("onBoostStart", counter(boosts));
		keys.add(Input.Keys.UP);
		keys.add(Input.Keys.SHIFT_LEFT);
		run(1f, s);
		check(boosts[0] == 1, "SpaceShip: onBoostStart fired once " + boosts[0]);
		check(Math.hypot(vxPx(p), vyPx(p)) > s.maxSpeed, "SpaceShip: boost exceeds the normal limit");
	}

	static void patrol() {
		newStage(0, 0);
		Item e = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		Patrol pa = attach(new Patrol(), e);
		final int[] turns = {0};
		pa.on("onTurn", counter(turns));
		pa.waitTime = 0.2f;
		float min = 1e9f, max = -1e9f;
		for (int i = 0; i < 600; i++) {
			run(DT, pa);
			min = Math.min(min, px(e));
			max = Math.max(max, px(e));
		}
		check(min > 360 - 100 - 8 && max < 360 + 100 + 8 && max - min > 180, "Patrol: stays in the range [" + min + "," + max + "]");
		check(turns[0] >= 3, "Patrol: turned " + turns[0] + " times");
		check(finite(px(e)), "Patrol: finite");

		// wall
		newStage(0, 0);
		e = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		phys(470, 780, 20, 300, BodyDef.BodyType.StaticBody, false, 0f); // wall, left face at x=460
		pa = attach(new Patrol(), e);
		final int[] turns2 = {0};
		pa.on("onTurn", counter(turns2));
		pa.rangeForward = 500;
		max = -1e9f;
		for (int i = 0; i < 240; i++) {
			run(DT, pa);
			max = Math.max(max, px(e));
		}
		check(max < 442 && turns2[0] >= 1, "Patrol: turns at a wall max=" + max + " turns=" + turns2[0]);

		// ledge: ground only under x < 400
		newStage(0, 0);
		phys(300, 740, 200, 20, BodyDef.BodyType.StaticBody, false, 0f); // top at 750, spans 200..400
		e = phys(300, 770, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		pa = attach(new Patrol(), e);
		pa.turnAtLedges = true;
		pa.turnAtWalls = false;
		pa.rangeForward = 500;
		pa.rangeBack = 500;
		max = -1e9f;
		for (int i = 0; i < 240; i++) {
			run(DT, pa);
			max = Math.max(max, px(e));
		}
		check(max < 400 + 5, "Patrol: turns at a ledge max=" + max);

		// waypoints
		newStage(0, 0);
		Item a = ui(500, 780, 10, 10), b = ui(300, 780, 10, 10);
		e = phys(300, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		pa = attach(new Patrol(), e);
		pa.mode = Patrol.Mode.WAYPOINTS;
		pa.waypoints.add(a);
		pa.waypoints.add(b);
		pa.waitTime = 0.1f;
		final int[] reached = {0};
		pa.on("onReachWaypoint", counter(reached));
		max = -1e9f;
		for (int i = 0; i < 480; i++) {
			run(DT, pa);
			max = Math.max(max, px(e));
		}
		check(max > 495 && max < 510 && reached[0] >= 3, "Patrol waypoints: reaches the points max=" + max + " reached=" + reached[0]);

		// UI element
		newStage(0, 0);
		Item u = ui(200, 200, 40, 40);
		pa = attach(new Patrol(), u);
		min = 1e9f;
		max = -1e9f;
		for (int i = 0; i < 400; i++) {
			run(DT, pa);
			float c = u.actor.getX() + 20;
			min = Math.min(min, c);
			max = Math.max(max, c);
		}
		check(min > 92 && max < 308 && max - min > 150, "Patrol UI: range [" + min + "," + max + "]");
	}

	static void car() {
		newStage(0, 0);
		Item p = phys(360, 780, 40, 80, BodyDef.BodyType.DynamicBody, false, 0f);
		Car c = attach(new Car(), p);
		keys.add(Input.Keys.UP);
		run(1.5f, c);
		check(c.getCarSpeed() > 150 && py(p) - 780 > 100 && Math.abs(px(p) - 360) < 1, "Car: accelerates forward (nose 90 = up) speed=" + c.getCarSpeed() + " dy=" + (py(p) - 780));
		check(p.body.isFixedRotation() && p.body.getGravityScale() == 0, "Car: top-down fixes rotation and gravity at runtime");
		keys.add(Input.Keys.LEFT);
		run(1f, c);
		check(deg(p) > 10 && c.getSteerAngle() > 5 && finite(px(p), py(p), deg(p)), "Car: steering left turns counter-clockwise angle=" + deg(p) + " steer=" + c.getSteerAngle());
		check(px(p) < 360 - 5, "Car: the car curves to the left, x=" + px(p));
		keys.clear();
		keys.add(Input.Keys.DOWN);
		float s1 = c.getCarSpeed();
		run(0.6f, c);
		check(c.getCarSpeed() < s1 - 100, "Car: brake slows down " + s1 + " -> " + c.getCarSpeed());
		run(3f, c);
		check(c.getCarSpeed() < 0 && c.getCarSpeed() >= -c.reverseSpeed - 1, "Car: reverses after stopping " + c.getCarSpeed());
		// drift with the handbrake
		newStage(0, 0);
		p = phys(360, 780, 40, 80, BodyDef.BodyType.DynamicBody, false, 0f);
		c = attach(new Car(), p);
		final int[] drift = {0};
		c.on("onDriftStart", counter(drift));
		keys.add(Input.Keys.UP);
		run(1.5f, c);
		keys.add(Input.Keys.LEFT);
		keys.add(Input.Keys.SPACE);
		run(1f, c);
		check(drift[0] >= 1, "Car: handbrake + steering drifts (event) " + drift[0]);

		// side scroller with real wheels and joints
		newStage(0, -60);
		Item ground = phys(360, 100, 3000, 20, BodyDef.BodyType.StaticBody, false, 1f);
		Item body = phys(360, 145, 100, 20, BodyDef.BodyType.DynamicBody, false, 0.3f);
		Item w1 = phys(325, 126, 30, 30, BodyDef.BodyType.DynamicBody, true, 1f);
		Item w2 = phys(395, 126, 30, 30, BodyDef.BodyType.DynamicBody, true, 1f);
		for (Item w : new Item[] {w1, w2}) {
			RevoluteJointDef jd = new RevoluteJointDef();
			jd.initialize(body.body, w.body, w.body.getPosition());
			stage.world.createJoint(jd);
			Filter f = new Filter();
			f.groupIndex = -1;
			w.body.getFixtureList().get(0).setFilterData(f);
			body.body.getFixtureList().get(0).setFilterData(f);
		}
		c = attach(new Car(), body);
		c.mode = Car.Mode.SIDE_SCROLLER;
		c.wheels.add(w1);
		c.wheels.add(w2);
		c.maxSpeed = 200;
		keys.add(Input.Keys.RIGHT);
		run(3f, c);
		check(px(body) - 360 > 100 && finite(px(body), py(body)), "Car side-scroller: wheel motors drive the car to the right dx=" + (px(body) - 360));
		check(c.isGrounded(), "Car side-scroller: wheels touch the ground");
		float xr = px(body);
		keys.clear();
		keys.add(Input.Keys.LEFT);
		run(4f, c);
		check(vxPx(body) < -100, "Car side-scroller: LEFT reverses the car vx=" + vxPx(body));
		// in the air left/right rotates the body
		body.body.setTransform(body.body.getPosition().x, 900 * W, 0);
		w1.body.setTransform(body.body.getPosition().x - 35 * W, 881 * W, 0);
		w2.body.setTransform(body.body.getPosition().x + 35 * W, 881 * W, 0);
		keys.clear();
		keys.add(Input.Keys.RIGHT);
		run(0.6f, c);
		check(!c.isGrounded() && body.body.getAngularVelocity() < -1, "Car side-scroller: right in the air rotates clockwise av=" + body.body.getAngularVelocity());
	}

	static void chase() {
		newStage(0, 0);
		Item target = phys(560, 780, 30, 30, BodyDef.BodyType.StaticBody, false, 0f);
		Item e = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		Chase ch = attach(new Chase(), e);
		ch.target = target;
		final int[] seen = {0}, lost = {0}, reach = {0};
		ch.on("onSeeTarget", counter(seen));
		ch.on("onLoseTarget", counter(lost));
		ch.on("onReachTarget", counter(reach));
		ch.stopDistance = 45;
		run(3f, ch);
		check(seen[0] == 1 && px(e) > 505 && px(e) < 526 && reach[0] >= 1, "Chase: runs to the target and stops at ~45px x=" + px(e) + " seen=" + seen[0] + " reach=" + reach[0]);
		target.body.setTransform(2000 * W, 780 * W, 0);
		run(0.2f, ch);
		check(lost[0] == 1 && !ch.isChasing(), "Chase: gives up when far lost=" + lost[0]);
		// too far at the start: idles
		newStage(0, 0);
		target = phys(1500, 780, 30, 30, BodyDef.BodyType.StaticBody, false, 0f);
		e = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		ch = attach(new Chase(), e);
		ch.target = target;
		run(1f, ch);
		check(Math.abs(px(e) - 360) < 0.01f, "Chase: stays put when the target is out of sight");
		// line of sight
		newStage(0, 0);
		target = phys(560, 780, 30, 30, BodyDef.BodyType.StaticBody, false, 0f);
		phys(460, 780, 20, 400, BodyDef.BodyType.StaticBody, false, 0f);
		e = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		ch = attach(new Chase(), e);
		ch.target = target;
		ch.needLineOfSight = true;
		run(1f, ch);
		check(Math.abs(px(e) - 360) < 0.01f, "Chase: a wall blocks the line of sight");
		ch.needLineOfSight = false;
		run(0.5f, ch);
		check(px(e) > 380, "Chase: sees through walls when line of sight is off");
		// flying chase (2 axes) + return home
		newStage(0, -50);
		target = phys(360, 1000, 30, 30, BodyDef.BodyType.StaticBody, false, 0f);
		e = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		ch = new Chase();
		ch.item = e;
		ch.stage = stage;
		ch.target = target;
		ch.horizontalOnly = false;
		ch.returnHome = true;
		ch.engineStart();
		run(1.5f, ch);
		check(py(e) > 900 && finite(py(e)), "Chase flying: reaches a target above despite gravity y=" + py(e));
		target.body.setTransform(2000 * W, 1000 * W, 0);
		run(4f, ch);
		check(Math.abs(py(e) - 780) < 6, "Chase: returns home y=" + py(e));
	}

	static void movingPlatform() {
		newStage(0, -50);
		Item plat = phys(360, 500, 200, 20, BodyDef.BodyType.StaticBody, false, 1f);
		Item rider = phys(360, 525, 30, 30, BodyDef.BodyType.DynamicBody, false, 0f);
		MovingPlatform mp = attach(new MovingPlatform(), plat);
		check(plat.body.getType() == BodyDef.BodyType.KinematicBody, "MovingPlatform: becomes kinematic (@Sets)");
		final int[] reachB = {0};
		mp.on("onReachB", counter(reachB));
		mp.pause = 0;
		mp.smooth = false;
		run(1f, mp);
		check(px(plat) - 360 > 70 && px(plat) - 360 < 90, "MovingPlatform: speed 80px/s dx=" + (px(plat) - 360));
		check(px(rider) - 360 > 60 && py(rider) > 515, "MovingPlatform: carries the rider (frictionless) dx=" + (px(rider) - 360) + " y=" + py(rider));
		run(2.5f, mp);
		check(reachB[0] == 1 && px(plat) > 360 && px(plat) <= 560.5f, "MovingPlatform: reached the other end once x=" + px(plat));
		run(1.5f, mp);
		check(px(plat) < 380, "MovingPlatform: comes back to the start x=" + px(plat));
		// points mode + vertical
		newStage(0, 0);
		plat = phys(360, 500, 100, 20, BodyDef.BodyType.StaticBody, false, 1f);
		Item bpt = ui(360, 800, 10, 10);
		mp = attach(new MovingPlatform(), plat);
		mp.pointB = bpt;
		mp.pause = 0;
		mp.speed = 150;
		run(2.05f, mp);
		check(py(plat) > 790 && py(plat) < 802 && Math.abs(px(plat) - 360) < 1, "MovingPlatform: points mode goes to B y=" + py(plat));
	}

	static void jumpPad() {
		newStage(0, 0);
		Item pad = phys(360, 300, 80, 20, BodyDef.BodyType.StaticBody, false, 0f);
		Item pl = phys(360, 330, 30, 30, BodyDef.BodyType.DynamicBody, false, 0f);
		JumpPad jp = attach(new JumpPad(), pad);
		final int[] launches = {0};
		jp.on("onLaunch", counter(launches));
		jp.onCollisionBegin(pl);
		check(Math.abs(vyPx(pl) - 420) < 1 && Math.abs(vxPx(pl)) < 1 && launches[0] == 1, "JumpPad: launches up vy=" + vyPx(pl));
		pl.body.setLinearVelocity(0, 0);
		jp.onCollisionBegin(pl);
		check(Math.abs(vyPx(pl)) < 1, "JumpPad: cooldown blocks a second launch");
		run(0.3f, jp);
		jp.onCollisionBegin(pl);
		check(vyPx(pl) > 400, "JumpPad: works again after the cooldown");
		// behind the pad (below): no launch
		pl.body.setTransform(360 * W, 280 * W, 0);
		pl.body.setLinearVelocity(0, 0);
		run(0.3f, jp);
		jp.onCollisionBegin(pl);
		check(Math.abs(vyPx(pl)) < 1, "JumpPad: not from behind");
		// filter
		Item other = phys(380, 330, 30, 30, BodyDef.BodyType.DynamicBody, false, 0f);
		run(0.3f, jp);
		jp.targets.add(pl);
		pl.body.setTransform(360 * W, 330 * W, 0);
		jp.onCollisionBegin(other);
		check(Math.abs(vyPx(other)) < 1, "JumpPad: the target list filters");
		// sensor option
		newStage(0, 0);
		pad = phys(360, 300, 80, 20, BodyDef.BodyType.StaticBody, false, 0f);
		jp = attach(new JumpPad(), pad);
		jp.sensor = true;
		jp.onDisable();
		jp.onEnable();
		check(pad.body.getFixtureList().get(0).isSensor(), "JumpPad: sensor mode");
		jp.onDisable();
		check(!pad.body.getFixtureList().get(0).isSensor(), "JumpPad: sensor restored on disable");
	}

	static void bounce() {
		newStage(0, -50);
		Item ball = phys(360, 780, 30, 30, BodyDef.BodyType.DynamicBody, true, 0.2f);
		Bounce b = attach(new Bounce(), ball);
		b.bounceOffView = true;
		check(ball.body.getGravityScale() == 0, "Bounce: gravity off (@Sets)");
		final int[] edge = {0};
		b.on("onBounceEdge", counter(edge));
		float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;
		for (int i = 0; i < 60 * 12; i++) {
			run(DT, b);
			minX = Math.min(minX, px(ball));
			maxX = Math.max(maxX, px(ball));
			minY = Math.min(minY, py(ball));
			maxY = Math.max(maxY, py(ball));
		}
		check(minX > 15 - 8 && maxX < 705 + 8 && minY > 15 - 8 && maxY < 1545 + 8, "Bounce: stays in the view [" + minX + "," + maxX + "] [" + minY + "," + maxY + "]");
		check(edge[0] >= 3, "Bounce: bounced off the edges " + edge[0]);
		double sp = Math.hypot(vxPx(ball), vyPx(ball));
		check(Math.abs(sp - 250) < 2, "Bounce: constant speed " + sp);
		check(maxX - minX > 300, "Bounce: moves");
		// UI element
		newStage(0, 0);
		Item u = ui(360, 780, 40, 40);
		b = attach(new Bounce(), u);
		minX = 1e9f;
		maxX = -1e9f;
		for (int i = 0; i < 60 * 10; i++) {
			run(DT, b);
			float c = u.actor.getX() + 20, d = u.actor.getY() + 20;
			minX = Math.min(minX, c);
			maxX = Math.max(maxX, c);
			check(finite(c, d) && d > 19.9f && d < 1540.1f, "Bounce UI: inside vertically");
			if (failures > 5) return;
		}
		check(minX >= 19.9f && maxX <= 700.1f && maxX - minX > 300, "Bounce UI: bounces off the screen [" + minX + "," + maxX + "]");
		// ball between two walls, physics only
		newStage(0, 0);
		phys(100, 780, 20, 4000, BodyDef.BodyType.StaticBody, false, 0f);
		phys(500, 780, 20, 4000, BodyDef.BodyType.StaticBody, false, 0f);
		ball = phys(300, 780, 30, 30, BodyDef.BodyType.DynamicBody, true, 0f);
		b = attach(new Bounce(), ball);
		b.startAngle = 10;
		b.minAngle = 20;
		maxX = -1e9f;
		minX = 1e9f;
		for (int i = 0; i < 60 * 6; i++) {
			run(DT, b);
			minX = Math.min(minX, px(ball));
			maxX = Math.max(maxX, px(ball));
		}
		check(minX > 100 && maxX < 500, "Bounce physics: stays between the walls [" + minX + "," + maxX + "]");
		double sp2 = Math.hypot(vxPx(ball), vyPx(ball));
		check(Math.abs(sp2 - 250) < 2 && Math.abs(vyPx(ball)) >= 250 * Math.sin(Math.toRadians(19.5)), "Bounce: minAngle keeps it off the horizontal " + vxPx(ball) + "," + vyPx(ball));
	}

	static void wrapAndLimit() {
		newStage(0, 0);
		Item u = ui(715, 100, 40, 40);
		WrapScreen w = attach(new WrapScreen(), u);
		final int[] wraps = {0};
		w.on("onWrap", counter(wraps));
		u.actor.setPosition(745 - 20, 80);
		run(DT, w);
		check(u.actor.getX() + 20 < 0 && wraps[0] == 1, "WrapScreen UI: reappears on the left x=" + (u.actor.getX() + 20));
		u.actor.setPosition(300, 1590);
		run(DT, w);
		check(u.actor.getY() + 20 < 0, "WrapScreen UI: bottom edge");
		// physical, camera view
		newStage(0, 0);
		Item p = phys(700, 300, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		w = attach(new WrapScreen(), p);
		p.body.setLinearVelocity(100 * W, 0);
		run(1f, w);
		check(px(p) < 100 && px(p) > -40, "WrapScreen physical: wrapped to the left x=" + px(p) + " vx=" + vxPx(p));
		check(Math.abs(vxPx(p) - 100) < 1, "WrapScreen: speed kept");

		newStage(0, 0);
		u = ui(-50, 100, 40, 40);
		LimitToScreen l = attach(new LimitToScreen(), u);
		final int[] hit = {0};
		l.on("onHitEdge", counter(hit));
		run(DT, l);
		check(Math.abs(u.actor.getX() + 20 - 20) < 0.01f && hit[0] == 1, "LimitToScreen UI: clamped to x=20, got " + (u.actor.getX() + 20));
		l.margin = 10;
		u.actor.setPosition(1000, 1700);
		run(DT, l);
		check(Math.abs(u.actor.getX() + 20 - 690) < 0.01f && Math.abs(u.actor.getY() + 20 - 1530) < 0.01f, "LimitToScreen UI: margin");
		newStage(0, 0);
		p = phys(100, 300, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		l = attach(new LimitToScreen(), p);
		p.body.setLinearVelocity(-200 * W, 0);
		run(1f, l);
		check(Math.abs(px(p) - 20) < 0.5f && Math.abs(vxPx(p)) < 1, "LimitToScreen physical: stops at the edge x=" + px(p) + " vx=" + vxPx(p));
		l.bounciness = 1;
		p.body.setLinearVelocity(-200 * W, 0);
		run(0.1f, l);
		check(vxPx(p) > 100, "LimitToScreen: bounciness reflects the speed " + vxPx(p));
	}

	static void orbitLookFollow() {
		newStage(0, 0);
		Item c = phys(360, 780, 30, 30, BodyDef.BodyType.StaticBody, false, 0f);
		Item o = phys(460, 780, 20, 20, BodyDef.BodyType.DynamicBody, true, 0f);
		Orbit ob = attach(new Orbit(), o);
		ob.center = c;
		ob.onEnable();
		final int[] laps = {0};
		ob.on("onLap", counter(laps));
		double worst = 0;
		for (int i = 0; i < 60 * 3; i++) {
			run(DT, ob);
			worst = Math.max(worst, Math.abs(Math.hypot(px(o) - 360, py(o) - 780) - 100));
		}
		check(worst < 3, "Orbit: keeps the radius, worst error " + worst);
		check(Math.abs(px(o) - 360) < 6 && Math.abs(py(o) - 680) < 6, "Orbit: after 270 degrees it is below the center (" + px(o) + "," + py(o) + ")");
		run(1.1f, ob);
		check(laps[0] == 1, "Orbit: onLap after a full turn " + laps[0]);
		// no center: the start position is on the circle
		newStage(0, 0);
		o = phys(460, 780, 20, 20, BodyDef.BodyType.DynamicBody, true, 0f);
		ob = new Orbit();
		ob.item = o;
		ob.stage = stage;
		ob.radius = 50;
		ob.clockwise = true;
		ob.engineStart();
		run(1f, ob);
		check(Math.abs(Math.hypot(px(o) - 410, py(o) - 780) - 50) < 3 && py(o) < 780, "Orbit: free circle, clockwise goes down first (" + px(o) + "," + py(o) + ")");
		// UI
		newStage(0, 0);
		Item uc = ui(360, 780, 10, 10), uo = ui(460, 780, 10, 10);
		ob = attach(new Orbit(), uo);
		ob.center = uc;
		ob.onEnable();
		ob.faceMovement = true;
		run(1f, ob);
		check(Math.abs(Math.hypot(uo.actor.getX() + 5 - 360, uo.actor.getY() + 5 - 780) - 100) < 1, "Orbit UI: radius");

		// look at
		newStage(0, 0);
		Item tg = phys(360, 880, 20, 20, BodyDef.BodyType.StaticBody, false, 0f);
		Item lk = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		LookAtTarget la = attach(new LookAtTarget(), lk);
		la.target = tg;
		final int[] al = {0};
		la.on("onAligned", counter(al));
		run(1f, la);
		check(Math.abs(deg(lk) - 90) < 1 && al[0] == 1 && la.isAligned(), "LookAtTarget: faces up deg=" + deg(lk));
		tg.body.setTransform(260 * W, 780 * W, 0);
		run(1f, la);
		check(Math.abs(Math.abs(deg(lk)) - 180) < 1, "LookAtTarget: faces left deg=" + deg(lk));
		la.faceOffset = 90;
		run(1f, la);
		check(Math.abs(deg(lk) - 90) < 1, "LookAtTarget: image offset deg=" + deg(lk));
		// finger
		newStage(0, 0);
		lk = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		la = attach(new LookAtTarget(), lk);
		la.source = LookAtTarget.Source.FINGER;
		touched = true;
		touchX = 360 + 100; // screen x -> stage px 460
		touchY = 1560 - 780; // same height as the element
		run(0.5f, la);
		check(Math.abs(deg(lk)) < 1, "LookAtTarget finger: faces right deg=" + deg(lk));
		touched = false;
		run(0.1f, la);
		check(Math.abs(deg(lk)) < 1, "LookAtTarget finger: keeps the last touch");

		// follow
		newStage(0, 0);
		Item tf = ui(600, 200, 20, 20), fu = ui(100, 100, 20, 20);
		Follow fo = attach(new Follow(), fu);
		fo.target = tf;
		fo.offsetY = 50;
		final int[] arr = {0};
		fo.on("onArrive", counter(arr));
		run(3f, fo);
		check(Math.abs(fu.actor.getX() + 10 - 600) < 3 && Math.abs(fu.actor.getY() + 10 - 250) < 3 && arr[0] >= 1, "Follow UI: reaches the offset spot (" + (fu.actor.getX() + 10) + "," + (fu.actor.getY() + 10) + ")");
		newStage(0, -50);
		Item pt = ui(500, 600, 20, 20);
		Item pf = phys(100, 300, 20, 20, BodyDef.BodyType.DynamicBody, false, 0f);
		fo = attach(new Follow(), pf);
		fo.target = pt;
		fo.maxSpeed = 200;
		run(4f, fo);
		check(Math.hypot(px(pf) - 500, py(pf) - 600) < 5, "Follow physical (gravity ignored): (" + px(pf) + "," + py(pf) + ")");
		fo.onDisable();
		check(pf.body.getGravityScale() == 1, "Follow: gravity restored on disable");
		// followX only keeps gravity
		newStage(0, -50);
		pt = ui(500, 600, 20, 20);
		pf = phys(100, 300, 20, 20, BodyDef.BodyType.DynamicBody, false, 0f);
		fo = new Follow();
		fo.item = pf;
		fo.stage = stage;
		fo.target = pt;
		fo.followY = false;
		fo.engineStart();
		run(1f, fo);
		check(py(pf) < 300 && px(pf) > 150, "Follow followX only: still falls (y=" + py(pf) + ")");
	}

	static void tapAndTilt() {
		// TAP on a UI element
		newStage(0, 0);
		Item u = ui(100, 100, 40, 40);
		TapToMove t = attach(new TapToMove(), u);
		final int[] arrived = {0};
		t.on("onArrive", counter(arrived));
		touched = true;
		touchX = 500;
		touchY = 1560 - 400;
		run(DT, t);
		touched = false;
		run(3f, t);
		check(Math.abs(u.actor.getX() + 20 - 500) < 5 && Math.abs(u.actor.getY() + 20 - 400) < 5 && arrived[0] >= 1, "TapToMove UI: goes to the tapped point (" + (u.actor.getX() + 20) + "," + (u.actor.getY() + 20) + ")");
		// an ignored element
		newStage(0, 0);
		u = ui(100, 100, 40, 40);
		Item btn = ui(500, 400, 100, 100);
		t = attach(new TapToMove(), u);
		t.ignoreTouchesOn.add(btn);
		touched = true;
		touchX = 500;
		touchY = 1560 - 400;
		run(DT, t);
		touched = false;
		run(1f, t);
		check(Math.abs(u.actor.getX() + 20 - 100) < 0.01f, "TapToMove: touches on the ignore list do nothing");
		// physical (world coordinates through the camera)
		newStage(0, 0);
		Item p = phys(100, 100, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		t = attach(new TapToMove(), p);
		touched = true;
		touchX = 500;
		touchY = 1560 - 400;
		run(DT, t);
		touched = false;
		run(3f, t);
		check(Math.abs(px(p) - 500) < 5 && Math.abs(py(p) - 400) < 5, "TapToMove physical: (" + px(p) + "," + py(p) + ")");
		check(p.body.getGravityScale() == 0, "TapToMove: 2D movement ignores gravity");
		// block API
		newStage(0, 0);
		p = phys(100, 100, 40, 40, BodyDef.BodyType.DynamicBody, false, 0f);
		t = attach(new TapToMove(), p);
		t.moveTo(300, 100);
		run(2f, t);
		check(Math.abs(px(p) - 300) < 5 && !t.hasDestination(), "TapToMove.moveTo block");
		// HOLD
		newStage(0, 0);
		u = ui(100, 100, 40, 40);
		t = attach(new TapToMove(), u);
		t.mode = TapToMove.Mode.HOLD;
		touched = true;
		touchX = 400;
		touchY = 1560 - 100;
		run(1f, t);
		float xh = u.actor.getX() + 20;
		touched = false;
		run(1f, t);
		check(xh > 250 && Math.abs(u.actor.getX() + 20 - xh) < 1, "TapToMove HOLD: stops when the finger is lifted");
		// DRAG
		newStage(0, 0);
		u = ui(100, 100, 40, 40);
		t = attach(new TapToMove(), u);
		t.mode = TapToMove.Mode.DRAG_ITEM;
		touched = true;
		touchX = 110;
		touchY = 1560 - 100;
		t.onTouchStart(null);
		touchX = 410;
		touchY = 1560 - 300;
		run(DT, t);
		check(Math.abs(u.actor.getX() + 20 - 400) < 1 && Math.abs(u.actor.getY() + 20 - 300) < 1, "TapToMove DRAG: keeps the grab offset (" + (u.actor.getX() + 20) + "," + (u.actor.getY() + 20) + ")");
		touched = false;

		// tilt
		newStage(0, 0);
		p = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, true, 0f);
		TiltMove tm = attach(new TiltMove(), p);
		tm.useKeyboard = false;
		accel = true;
		accX = -4; // right edge down in portrait
		accY = 0;
		run(1f, tm);
		check(px(p) - 360 > 200 && Math.abs(py(p) - 780) < 1, "TiltMove: tilting right moves right " + (px(p) - 360));
		accX = 0;
		accY = -4; // top edge down = up the screen
		run(1f, tm);
		check(py(p) - 780 > 150, "TiltMove: tilting the top down moves up " + (py(p) - 780));
		accY = 0;
		run(1f, tm);
		check(Math.abs(vxPx(p)) < 1 && Math.abs(vyPx(p)) < 1, "TiltMove: flat phone = stopped");
		// ROLL mode keeps rolling
		newStage(0, 0);
		p = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, true, 0f);
		tm = attach(new TiltMove(), p);
		tm.mode = TiltMove.Mode.ROLL;
		tm.useKeyboard = false;
		accel = true;
		accX = -4;
		run(0.5f, tm);
		accX = 0;
		float v1 = vxPx(p);
		run(0.3f, tm);
		check(v1 > 100 && vxPx(p) > 50, "TiltMove ROLL: keeps rolling after the tilt ends " + v1 + " -> " + vxPx(p));
		// no accelerometer: keyboard fallback
		newStage(0, 0);
		p = phys(360, 780, 40, 40, BodyDef.BodyType.DynamicBody, true, 0f);
		tm = attach(new TiltMove(), p);
		keys.add(Input.Keys.LEFT);
		run(1f, tm);
		check(px(p) < 200, "TiltMove: arrow keys simulate the tilt " + px(p));
		// the screen rotation is respected: with the phone turned 90 degrees the roles of the axes swap
		float[] out = new float[2];
		accel = true;
		accX = -4;
		accY = 0;
		TiltMove.read(TiltMove.Orientation.ROTATED_90, out);
		check(Math.abs(out[0]) < 0.01f && Math.abs(out[1] - 4) < 0.01f, "TiltMove.read rotated 90: " + out[0] + "," + out[1]);
	}
}
