import com.star4droid.star2d.behaviors.*;

import java.io.File;
import java.util.*;

/** Run by tools/test_behaviors.py (javac against the template classes + gdx + gson). Prints "FAIL ..." lines, exit code 1 on failure. */
public class BehaviorsTest {
	static int failures = 0, checks = 0;

	static void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			failures++;
			System.out.println("FAIL " + what);
		}
	}

	static String read(String p) throws Exception {
		return new String(java.nio.file.Files.readAllBytes(new File(p).toPath()), "UTF-8");
	}

	static VarSpec v(SourceScanner.Result r, String n) {
		for (VarSpec s : r.vars) if (s.name.equals(n)) return s;
		return null;
	}

	public static void main(String[] a) throws Exception {
		String root = a[0];
		sampleScan();
		libCrossCheck(root);
		codeGen(root);
		robustness();
		System.out.println(checks + " checks, " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}

	static void sampleScan() {
		String src = "package com.star4droid.Game.Scripts.main;\n"
				+ "import com.star4droid.template.Items.*;\n"
				+ "// public int commented = 5;\n"
				+ "/* public int block = 1; */\n"
				+ "public class PlayerScript extends mainItemRefs {\n"
				+ "  public enum Mode { WALK, RUN(2), FLY }\n"
				+ "  public int lives = 3, coins;\n"
				+ "  public float speed = 1.5f * 2, gravity = -9.8f;\n"
				+ "  public double ratio = 1.0/4;\n"
				+ "  public long big = 5_000L; public short sh = (short)7; public byte by = 3;\n"
				+ "  public boolean alive = true;\n"
				+ "  public String name = \"he said \\\"hi\\\"; { x = 1; }\";\n"
				+ "  public String bulletImage = \"/b.png\";\n"
				+ "  @Sound public String hit = \"hit.wav\";\n"
				+ "  @Image public String face;\n"
				+ "  public Color tint = Color.valueOf(\"#ff8800\");\n"
				+ "  public Color c2 = new Color(1f, 0f, 0f, 0.5f);\n"
				+ "  public Mode mode = Mode.RUN;\n"
				+ "  public int[] nums = {1, 2, 3};\n"
				+ "  public float[] fl = new float[]{0.5f, 2};\n"
				+ "  public List<String> names = new ArrayList<String>(Arrays.asList(\"a\", \"b,c\"));\n"
				+ "  public java.util.List<Integer> ids = new java.util.ArrayList<Integer>();\n"
				+ "  public String[] tags = new String[3];\n"
				+ "  public ProgressItem hpBar;\n"
				+ "  public List<PlayerItem> enemies = new ArrayList<PlayerItem>();\n"
				+ "  public Joystick stick;\n"
				+ "  @Hidden public int secret = 1;\n"
				+ "  public static int shared = 4;\n"
				+ "  public final int constant = 9;\n"
				+ "  private int hiddenField = 2;\n"
				+ "  int packagePrivate = 3;\n"
				+ "  @Header(\"Combat\") @Param(desc = \"Damage, per hit\", min = 0, max = 99, step = 0.5, unit = \"hp\", label = \"Hit damage\")\n"
				+ "  public int damage = 10;\n"
				+ "  public Object unsupported = null;\n"
				+ "  public Map<String,String> map;\n"
				+ "  public int computed = Helper.calc(3);\n"
				+ "  public void onClick() { int local = 5; if (a) { b(); } }\n"
				+ "  public PlayerScript() { super(); }\n"
				+ "  public int after = 42;\n"
				+ "}\n";
		SourceScanner.Result r = SourceScanner.scan(src);
		check(r.className.equals("PlayerScript"), "class name " + r.className);
		check(r.fqn().equals("com.star4droid.Game.Scripts.main.PlayerScript"), "fqn " + r.fqn());
		check(r.superName.equals("mainItemRefs"), "super " + r.superName);
		check(v(r, "commented") == null && v(r, "block") == null, "comments ignored");
		check(v(r, "lives") != null && v(r, "lives").kind == VarSpec.Kind.INT && ((Double) v(r, "lives").def) == 3, "lives=3");
		check(v(r, "coins") != null && v(r, "coins").def == null, "coins no default");
		check(v(r, "speed").def.equals(3.0), "speed 1.5f*2 -> " + v(r, "speed").def);
		check(v(r, "gravity").def.equals(-9.8), "gravity " + v(r, "gravity").def);
		check(v(r, "ratio").def.equals(0.25), "ratio");
		check(v(r, "big").kind == VarSpec.Kind.LONG && v(r, "big").def.equals(5000.0), "long 5_000L");
		check(v(r, "sh").kind == VarSpec.Kind.SHORT && v(r, "sh").def.equals(7.0), "short cast");
		check(v(r, "by").kind == VarSpec.Kind.BYTE, "byte");
		check(v(r, "alive").def.equals(Boolean.TRUE), "bool");
		check(v(r, "name").def.equals("he said \"hi\"; { x = 1; }"), "string with braces/quotes: " + v(r, "name").def);
		check(v(r, "bulletImage").kind == VarSpec.Kind.IMAGE, "image by name");
		check(v(r, "hit").kind == VarSpec.Kind.SOUND, "sound by annotation");
		check(v(r, "face").kind == VarSpec.Kind.IMAGE, "image by annotation");
		check("#FF8800".equals(v(r, "tint").def), "color valueOf " + v(r, "tint").def);
		check("#FF000080".equals(v(r, "c2").def), "color new Color " + v(r, "c2").def);
		VarSpec mode = v(r, "mode");
		check(mode.kind == VarSpec.Kind.ENUM && mode.options.equals(Arrays.asList("WALK", "RUN", "FLY")) && "RUN".equals(mode.def), "enum " + mode.options + " " + mode.def);
		check(mode.elemType.equals("com.star4droid.Game.Scripts.main.PlayerScript.Mode"), "enum type " + mode.elemType);
		check(v(r, "nums").list && v(r, "nums").array && v(r, "nums").def.equals(Arrays.asList(1.0, 2.0, 3.0)), "int[] " + v(r, "nums").def);
		check(v(r, "fl").def.equals(Arrays.asList(0.5, 2.0)), "float[] " + v(r, "fl").def);
		check(v(r, "names").list && !v(r, "names").array && v(r, "names").def.equals(Arrays.asList("a", "b,c")), "List<String> " + v(r, "names").def);
		check(v(r, "ids").def.equals(new ArrayList<Object>()), "empty list");
		check(v(r, "tags") != null && v(r, "tags").list && ((List<?>) v(r, "tags").def).isEmpty(), "new String[3]");
		check(v(r, "hpBar").kind == VarSpec.Kind.REF && v(r, "hpBar").elemType.equals("ProgressItem") && !v(r, "hpBar").list, "ref ProgressItem");
		check(v(r, "enemies").kind == VarSpec.Kind.REF && v(r, "enemies").list, "List<PlayerItem> ref list");
		check(v(r, "stick").kind == VarSpec.Kind.REF, "Joystick ref");
		check(v(r, "secret") == null, "@Hidden");
		check(v(r, "shared") == null && v(r, "constant") == null && v(r, "hiddenField") == null && v(r, "packagePrivate") == null, "static/final/non-public skipped");
		VarSpec dmg = v(r, "damage");
		check(dmg != null && dmg.header.equals("Combat") && dmg.desc.equals("Damage, per hit") && dmg.min == 0 && dmg.max == 99 && dmg.step == 0.5 && dmg.unit.equals("hp") && dmg.label.equals("Hit damage"), "Param/Header");
		check(v(r, "unsupported") == null && v(r, "map") == null, "unsupported types skipped");
		check(v(r, "computed") != null && v(r, "computed").def == null, "unparsable initializer -> field without default");
		check(v(r, "after") != null && v(r, "after").def.equals(42.0), "fields after methods are found");
		check(v(r, "local") == null, "locals in methods not fields");
		check(new VarSpec("jumpSpeed", VarSpec.Kind.INT).displayLabel().equals("Jump speed"), "humanize");
		// behavior header
		String b = "package p;\n@BehaviorInfo(name = \"Hover { x }\", icon=\"wave\", desc=\"a, b\", targets = BehaviorInfo.Target.UI, category=\"Motion\", events = {\"onHit(PlayerItem enemy, float damage)\", \"onDone()\"})\n@Exclusive(\"movement\")\n@Requires(body = true, type = \"DYNAMIC\", note=\"n\")\n@Sets({\"fixedRotation=true\", \"gravityScale=0\"})\npublic class Hover extends Behavior { public float amp = 3; }";
		BehaviorMeta m = SourceScanner.behavior(b, "x.java");
		check(m != null && m.id.equals("Hover") && m.name.equals("Hover { x }") && m.icon.equals("wave") && m.desc.equals("a, b") && m.targets.equals("UI") && m.category.equals("Motion"), "BehaviorInfo");
		check(m.events.size() == 2 && m.events.get(0).name.equals("onHit") && m.events.get(0).args.size() == 2 && m.events.get(0).args.get(1)[0].equals("float"), "events");
		check(m.exclusive.equals("movement") && m.reqBody && m.reqType.equals("DYNAMIC") && m.reqNote.equals("n") && "0".equals(m.sets.get("gravityScale")) && "true".equals(m.sets.get("fixedRotation")), "exclusive/requires/sets");
		check(m.misfit("UI") != null && m.misfit("STATIC") != null && m.misfit("DYNAMIC") == null, "misfit");
	}

	static void libCrossCheck(String root) throws Exception {
		File lib = new File(root, "app/src/main/java/com/star4droid/template/behaviors/lib");
		int n = 0;
		for (File f : lib.listFiles()) {
			if (!f.getName().endsWith(".java")) continue;
			String id = f.getName().replace(".java", "");
			BehaviorMeta src = SourceScanner.behavior(read(f.getPath()), f.getPath());
			BehaviorMeta ref = BehaviorCatalog.find(id, null);
			check(src != null, id + ": scanner found class");
			check(ref != null && ref.builtin, id + ": reflection found class");
			if (src == null || ref == null) continue;
			n++;
			check(src.name.equals(ref.name) && src.icon.equals(ref.icon) && src.desc.equals(ref.desc) && src.category.equals(ref.category) && src.targets.equals(ref.targets), id + ": info equal");
			check(src.exclusive.equals(ref.exclusive) && src.reqBody == ref.reqBody && src.reqType.equals(ref.reqType) && src.sets.equals(ref.sets), id + ": exclusive/requires/sets equal");
			check(src.events.size() == ref.events.size(), id + ": events equal");
			check(src.vars.size() == ref.vars.size(), id + ": var count " + src.vars.size() + " vs " + ref.vars.size() + " " + src.vars + " / " + ref.vars);
			for (int i = 0; i < Math.min(src.vars.size(), ref.vars.size()); i++) {
				VarSpec x = src.vars.get(i), y = ref.vars.get(i);
				boolean same = x.name.equals(y.name) && x.kind == y.kind && x.list == y.list && x.array == y.array && x.elemType.equals(y.elemType.replace('$', '.'))
						&& x.header.equals(y.header) && x.desc.equals(y.desc) && x.unit.equals(y.unit) && x.min == y.min && x.max == y.max && x.step == y.step;
				check(same, id + "." + x.name + ": spec equal  " + x + " / " + y);
				Object dx = x.def, dy = y.def;
				boolean sameDef = (dx == null && (dy == null || (dy instanceof List && ((List<?>) dy).isEmpty() && x.list))) || (dx != null && dx.equals(dy)) || (dx instanceof Number && dy instanceof Number && Math.abs(((Number) dx).doubleValue() - ((Number) dy).doubleValue()) < 1e-4);
				check(sameDef, id + "." + x.name + ": default equal " + dx + " / " + dy);
			}
		}
		check(n >= 6, "at least six built-ins checked: " + n);
		check(BehaviorCatalog.builtIns().size() == n, "catalog size " + BehaviorCatalog.builtIns().size());
	}

	static class FakeEnv implements BehaviorCodeGen.Env {
		String project;
		Map<String, String> events = new HashMap<String, String>();

		public String projectPath() {
			return project;
		}

		public String eventCode(String body, String event) {
			String c = events.get(body + ":" + event);
			return c == null ? "" : c;
		}

		public String readFile(String path) {
			try {
				return read(path);
			} catch (Exception e) {
				return "";
			}
		}
	}

	static void codeGen(String root) throws Exception {
		File proj = new File(System.getProperty("java.io.tmpdir"), "s2b_test_proj");
		File dir = BehaviorCatalog.projectDir(proj.getPath());
		dir.mkdirs();
		java.nio.file.Files.write(new File(dir, "Hover.java").toPath(), ("package com.star4droid.Game.Behaviors;\nimport com.star4droid.template.behaviors.*;\nimport com.star4droid.template.Items.*;\nimport com.star4droid.template.Utils.PlayerItem;\nimport com.badlogic.gdx.graphics.Color;\n@BehaviorInfo(name=\"Hover\", events={\"onHit(PlayerItem enemy, float damage)\", \"onDone()\"})\npublic class Hover extends Behavior {\n public float amp = 3;\n public int[] ids = {1};\n public java.util.List<Integer> more = new java.util.ArrayList<Integer>();\n public ProgressItem bar;\n public java.util.List<PlayerItem> targets;\n public Mode mode = Mode.A;\n public enum Mode { A, B }\n public Color tint = Color.valueOf(\"#112233\");\n}\n").getBytes("UTF-8"));
		FakeEnv env = new FakeEnv();
		env.project = proj.getPath();
		env.events.put("player/behaviors/Hover:onHit", "toast(\"hit \" + damage);\nenemy.destroy();");
		String behaviors = "[{\"type\":\"Hover\",\"params\":{\"amp\":7.5,\"ids\":[4,5],\"more\":[9],\"bar\":\"hp\",\"targets\":[\"e1\",\"e2\"],\"mode\":\"B\",\"tint\":\"#ff000080\"},\"enabled\":false},"
				+ "{\"type\":\"Shooter\",\"params\":{\"fireRate\":5,\"bulletImage\":\"/b\\\"x.png\",\"enemies\":[\"z1\"],\"aimJoystick\":\"joy\",\"bulletColor\":\"#00FF00\",\"bogus\":1}},{\"type\":\"Missing\"}]";
		String code = BehaviorCodeGen.forItem(env, "player", behaviors, "", null);
		System.out.println("----- generated -----\n" + code + "---------------------");
		check(code.contains("com.star4droid.Game.Behaviors.Hover __b = new com.star4droid.Game.Behaviors.Hover();"), "project behavior instance");
		check(code.contains("__b.amp = 7.5f;"), "float param");
		check(code.contains("__b.ids = new int[]{4, 5};"), "int array");
		check(code.contains("new java.util.ArrayList<Integer>(java.util.Arrays.asList(9))"), "list");
		check(code.contains("BehaviorRegistry.ref(this, \"hp\", com.star4droid.template.Items.ProgressItem.class)"), "ref");
		check(code.contains("BehaviorRegistry.refs(this, com.star4droid.template.Utils.PlayerItem.class, \"e1\", \"e2\")"), "refs");
		check(code.contains("__b.mode = com.star4droid.Game.Behaviors.Hover.Mode.B;"), "enum");
		check(code.contains("Color.valueOf(\"112233\")") == false && code.contains("Color.valueOf(\"FF000080\")"), "color");
		check(code.contains("__b.enabled = false;"), "disabled");
		check(code.contains("__b.on(\"onHit\""), "handler registered");
		check(!code.contains("__b.on(\"onDone\""), "no handler when no code");
		check(code.contains("float damage = ((Number) args[1]).floatValue();") && code.contains("com.star4droid.template.Utils.PlayerItem enemy = (com.star4droid.template.Utils.PlayerItem) args[0];"), "typed arg locals");
		check(code.contains("com.star4droid.template.behaviors.lib.Shooter __b = new"), "builtin fqn");
		check(code.contains("__b.bulletImage = \"/b\\\"x.png\";"), "string escape");
		check(code.contains("__b.fireRate = 5.0f;"), "fireRate");
		check(!code.contains("bogus"), "unknown param ignored");
		check(code.contains("// behavior Missing of player was not found"), "missing behavior comment");
		check(code.contains("__b.attach(player, this);"), "attach");
		// script vars
		File sf = new File(proj, "PlayerScript.java");
		java.nio.file.Files.write(sf.toPath(), "package com.star4droid.Game.Scripts.main;\npublic class PlayerScript extends ItemScript {\n public int lives = 3; public ProgressItem hpBar; public java.util.List<PlayerItem> foes; public String title;\n}\n".getBytes("UTF-8"));
		String vars = "{\"lives\":5,\"hpBar\":\"hp\",\"foes\":[\"a\"],\"title\":\"Hi\",\"gone\":1}";
		String sv = BehaviorCodeGen.forItem(env, "player", "", vars, sf.getPath());
		System.out.println(sv);
		check(sv.contains("((com.star4droid.Game.Scripts.main.PlayerScript) player.getScript()).lives = 5;"), "script var int");
		check(sv.contains(".hpBar = com.star4droid.template.behaviors.BehaviorRegistry.ref(this, \"hp\""), "script ref");
		check(sv.contains(".title = \"Hi\";") && !sv.contains("gone"), "script string / stale var");
		File sceneFile = new File(proj, "mainScript.java");
		java.nio.file.Files.write(sceneFile.toPath(), "package com.star4droid.game.SceneScript;\npublic class mainScript extends SceneScript {\n public int level = 1;\n}\n".getBytes("UTF-8"));
		String sc = BehaviorCodeGen.forSceneScript(env, sceneFile.getPath(), "{\"level\":2}");
		check(sc.contains("((com.star4droid.game.SceneScript.mainScript) getSceneScript()).level = 2;"), "scene script");
		// write the generated code where test_behaviors.py can compile it
		String out = System.getProperty("gen.out");
		if (out != null) {
			String all = code + sv + sc;
			java.nio.file.Files.write(new File(out).toPath(), all.getBytes("UTF-8"));
		}
	}

	static void robustness() {
		String[] bad = {"", "class", "public class { public int x = ; }", "public class A { public int x = (( ; ", "package;;;class A{ public String s = \"unterminated;\n public int y; }", "@BehaviorInfo(( class A {", "public class A { public int[] x = {1,2; public int z = 1; }"};
		for (String s : bad) {
			try {
				SourceScanner.scan(s);
				SourceScanner.behavior(s, "");
				check(true, "no crash");
			} catch (Throwable t) {
				check(false, "crash on '" + s + "': " + t);
			}
		}
		check(BehaviorData.parse("not json").isEmpty() && BehaviorData.parse("").isEmpty() && BehaviorData.parseVars("[1]").isEmpty(), "bad json tolerated");
		check(ValueEmit.quote("a\"b\\c\né").equals("\"a\\\"b\\\\c\\n\\u00e9\""), "quote");
	}
}
