import com.star4droid.star2d.behaviors.*;

import java.io.File;
import java.util.*;

/**
 * Run by tools/test_scriptvars.py: the scanner for user scripts (public / package-private / static / @Expose, lists), the sync of the
 * stored variables with the code, and the generated assignments (args: script source file, generated code output file).
 */
public class ScriptVarsTest {
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

	static Set<String> names(SourceScanner.Result r) {
		Set<String> s = new TreeSet<String>();
		for (VarSpec x : r.vars) s.add(x.name);
		return s;
	}

	public static void main(String[] a) throws Exception {
		String src = read(a[0]);
		scan(src);
		sync(src);
		codegen(src, a[0], a[1]);
		System.out.println(checks + " checks, " + failures + " failures");
		System.exit(failures == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------------------------------------ scanner

	static void scan(String src) {
		SourceScanner.Result r = SourceScanner.scanScript(src);
		check(r.complete && r.className.equals("HeroScript"), "script found and complete");
		Set<String> want = new TreeSet<String>(Arrays.asList("lives", "timer", "count", "gravity", "secret", "tag", "nums", "names", "weights", "target",
				"targets", "bars", "score", "labels", "tint", "mode", "title", "bigList"));
		check(names(r).equals(want), "script fields: " + names(r));
		check(!names(r).contains("hidden") && !names(r).contains("prot") && !names(r).contains("K") && !names(r).contains("MAX") && !names(r).contains("skip")
				&& !names(r).contains("nope") && !names(r).contains("unsupported") && !names(r).contains("map") && !names(r).contains("innerField")
				&& !names(r).contains("local") && !names(r).contains("fake") && !names(r).contains("commented"), "private / protected / final / transient / @Hidden / unsupported / nested / local / comment are not exposed");
		VarSpec lives = v(r, "lives"), timer = v(r, "timer"), count = v(r, "count"), gravity = v(r, "gravity"), secret = v(r, "secret"), tag = v(r, "tag");
		check(lives.direct() && !lives.isStatic && lives.kind == VarSpec.Kind.INT && Double.valueOf(3).equals(lives.def) && lives.auto, "public int");
		check(timer.access.equals("package") && !timer.direct() && timer.kind == VarSpec.Kind.FLOAT && Double.valueOf(1.5).equals(timer.def), "package-private float");
		check(count.isStatic && count.access.equals("package") && count.def == null, "static package-private int without initializer");
		check(gravity.isStatic && gravity.direct() && Double.valueOf(-9.8).equals(gravity.def), "public static float");
		check(secret.access.equals("private") && secret.kind == VarSpec.Kind.INT && Double.valueOf(7).equals(secret.def), "@Expose private int");
		check(tag.access.equals("protected") && tag.kind == VarSpec.Kind.STRING && "x".equals(tag.def), "@Expose protected String");
		check(v(r, "title").kind == VarSpec.Kind.STRING && "public int fake = 1;".equals(v(r, "title").def), "string literal containing code");
		VarSpec nums = v(r, "nums"), nm = v(r, "names"), w = v(r, "weights");
		check(nums.list && !nums.array && nums.container.equals("List") && nums.def.equals(Arrays.<Object>asList(1.0, 2.0, 3.0)), "List<Integer> + Arrays.asList: " + nums.def);
		check(nm.list && nm.container.equals("ArrayList") && nm.kind == VarSpec.Kind.STRING && nm.def.equals(new ArrayList<Object>()), "ArrayList<String> with diamond");
		check(w.list && w.array && w.kind == VarSpec.Kind.FLOAT && w.def.equals(Arrays.<Object>asList(0.5, 1.0)), "float[] initializer");
		check(v(r, "target").kind == VarSpec.Kind.REF && v(r, "target").elemType.equals("PlayerItem"), "PlayerItem reference");
		check(v(r, "targets").list && v(r, "targets").kind == VarSpec.Kind.REF && v(r, "targets").container.equals("List"), "List<PlayerItem>");
		check(v(r, "bars").list && v(r, "bars").array && v(r, "bars").elemType.equals("ProgressItem"), "ProgressItem[]");
		check(v(r, "score").kind == VarSpec.Kind.REF && v(r, "score").elemType.equals("UINodeRef"), "UINodeRef");
		check(v(r, "labels").list && v(r, "labels").elemType.equals("UINodeRef"), "List<UINodeRef>");
		check(v(r, "tint").kind == VarSpec.Kind.COLOR && "#FF0000".equalsIgnoreCase(String.valueOf(v(r, "tint").def).substring(0, 7)), "Color: " + v(r, "tint").def);
		check(v(r, "mode").kind == VarSpec.Kind.ENUM && v(r, "mode").options.equals(Arrays.asList("WALK", "FLY")) && "FLY".equals(v(r, "mode").def), "enum");
		check(v(r, "bigList").list && v(r, "bigList").container.equals("LinkedList"), "LinkedList");
		check(lives.signature().equals("int") && nums.signature().equals("List<int>"), "signature text");
		check(w.signature().equals("float[]") && v(r, "targets").signature().equals("List<PlayerItem>"), "signature text");

		SourceScanner.Result b = SourceScanner.scan(src);
		check(names(b).equals(new TreeSet<String>(Arrays.asList("lives", "nums", "target", "tint"))), "behavior mode keeps only public instance fields: " + names(b));
		check(!b.vars.get(0).auto, "behavior fields are not auto");
		check(!names(r).contains("notPublicEnum"), "a field of a non-public enum is not exposed (the generated code could not name it)");

		// half-typed files must not look complete
		check(!SourceScanner.scanScript("").complete, "empty file");
		check(!SourceScanner.scanScript("public class A { public int x = 1; ").complete, "missing brace");
		check(!SourceScanner.scanScript("public class A { void f( { } }").complete, "missing paren");
		check(SourceScanner.scanScript("public class A { String s = \"}{\"; char c = '{'; // {\n }").complete, "braces in strings / comments do not count");
		for (String bad : new String[] {"", "class", "public class { public int x = ; }", "public class A { public int x = (( ; ", "public class A { public int[] x = {1,2; public int z = 1; }", "@Expose class A { @Expose"}) {
			try {
				SourceScanner.scanScript(bad);
				check(true, "no crash");
			} catch (Throwable t) {
				check(false, "crash on '" + bad + "': " + t);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ sync

	static String json(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<String, Object>();
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
		return BehaviorData.varsToJson(m);
	}

	static void sync(String src) {
		List<VarSpec> f = SourceScanner.scanScript(src).vars;
		String s1 = ScriptVarSync.sync("", f);
		Map<String, Object> m1 = BehaviorData.parseVars(s1);
		check(m1.size() == 1 && m1.get("$auto") instanceof Map && ((Map<?, ?>) m1.get("$auto")).size() == f.size(), "first sync records every field: " + s1);
		check(((Map<?, ?>) m1.get("$auto")).get("weights").equals("float[]") && ((Map<?, ?>) m1.get("$auto")).get("targets").equals("List<PlayerItem>"), "types recorded");
		check(ScriptVarSync.sync(s1, f).equals(s1), "sync is idempotent");

		// the user edits values, adds a manual key and the AI sets a value
		String edited = json("lives", 5.0, "nums", Arrays.<Object>asList(4.0), "target", "e1", "manualKey", 3.0, "$auto", m1.get("$auto"));
		String s2 = ScriptVarSync.sync(edited, f);
		Map<String, Object> m2 = BehaviorData.parseVars(s2);
		check(Double.valueOf(5).equals(m2.get("lives")) && "e1".equals(m2.get("target")) && Double.valueOf(3).equals(m2.get("manualKey")) && m2.get("nums") != null, "edited values and manual keys are kept");
		check(ScriptVarSync.sync(s2, f).equals(s2), "idempotent with values");
		check(ScriptVarSync.visible(m2).size() == 4 && !ScriptVarSync.visible(m2).containsKey("$auto") && ScriptVarSync.hasValues(m2) && !ScriptVarSync.hasValues(m1), "visible / hasValues");

		// code changes: lives deleted, timer renamed to time2, nums becomes List<String>, lives2 added as float with a stale int value
		List<VarSpec> g = new ArrayList<VarSpec>();
		for (VarSpec x : f) {
			if (x.name.equals("lives") || x.name.equals("timer")) continue;
			if (x.name.equals("nums")) {
				VarSpec n = new VarSpec("nums", VarSpec.Kind.STRING);
				n.list = true;
				g.add(n);
				continue;
			}
			g.add(x);
		}
		g.add(new VarSpec("time2", VarSpec.Kind.FLOAT));
		String withTimer = json("timer", 2.0, "lives", 5.0, "nums", Arrays.<Object>asList(4.0), "target", "e1", "manualKey", 3.0, "$auto", m1.get("$auto"));
		Map<String, Object> m3 = BehaviorData.parseVars(ScriptVarSync.sync(withTimer, g));
		check(!m3.containsKey("lives") && !m3.containsKey("timer"), "deleted / renamed fields lose their value");
		check(!m3.containsKey("nums"), "value of a changed type is dropped");
		check("e1".equals(m3.get("target")) && Double.valueOf(3).equals(m3.get("manualKey")), "other values and manual keys stay");
		Map<?, ?> auto3 = (Map<?, ?>) m3.get("$auto");
		check(auto3.containsKey("time2") && !auto3.containsKey("lives") && !auto3.containsKey("timer") && "List<String>".equals(auto3.get("nums")), "auto list follows the code: " + auto3);

		// int -> float keeps the number
		VarSpec lf = new VarSpec("lives", VarSpec.Kind.FLOAT);
		Map<String, Object> m4 = BehaviorData.parseVars(ScriptVarSync.sync(edited, Arrays.asList(lf)));
		check(Double.valueOf(5).equals(m4.get("lives")) && m4.size() == 3, "int -> float keeps the value: " + m4);
		// enum constant removed
		VarSpec en = new VarSpec("mode", VarSpec.Kind.ENUM);
		en.options.addAll(Arrays.asList("A", "B"));
		check(!BehaviorData.parseVars(ScriptVarSync.sync(json("mode", "Z"), Arrays.asList(en))).containsKey("mode"), "value outside the enum is dropped");
		// nothing in the code, nothing stored
		check(ScriptVarSync.sync(s1, new ArrayList<VarSpec>()).isEmpty(), "no fields and no values -> empty");
		check(ScriptVarSync.sync("{\"manualKey\":3}", new ArrayList<VarSpec>()).contains("manualKey"), "manual key alone stays");
		check(ScriptVarSync.sync("not json", f).startsWith("{\"$auto\""), "broken json is replaced by a clean one");
		check(ScriptVarSync.canonical("{ \"a\" : 1 }").equals("{\"a\":1.0}"), "canonical form");
	}

	// ------------------------------------------------------------------------------------------------ generated code

	static class FakeEnv implements BehaviorCodeGen.Env {
		public String projectPath() {
			return "/tmp/none";
		}

		public String eventCode(String body, String event) {
			return "";
		}

		public String readFile(String path) {
			try {
				return read(path);
			} catch (Exception e) {
				return "";
			}
		}
	}

	static void codegen(String src, String file, String out) throws Exception {
		FakeEnv env = new FakeEnv();
		String cls = "com.star4droid.Game.Scripts.main.HeroScript";
		String target = "((" + cls + ") holder.getScript())";
		Map<String, Object> run = new LinkedHashMap<String, Object>();
		run.put("lives", 5.0);
		run.put("timer", 2.5);
		run.put("count", 3.0);
		run.put("gravity", -1.5);
		run.put("secret", 9.0);
		run.put("tag", "yo \"q\"");
		run.put("nums", Arrays.<Object>asList(7.0, 8.0));
		run.put("names", Arrays.<Object>asList("a", "b"));
		run.put("weights", Arrays.<Object>asList(1.0, 2.5));
		run.put("tint", "#FF0000");
		run.put("mode", "WALK");
		run.put("bigList", Arrays.<Object>asList(1.0));
		run.put("gone", 1.0);
		String code = BehaviorCodeGen.forItem(env, "holder", "", ScriptVarSync.sync(BehaviorData.varsToJson(run), SourceScanner.scanScript(src).vars), file);
		System.out.println(code);
		check(code.contains(target + ".lives = 5;"), "public: plain assignment");
		check(code.contains("com.star4droid.template.Utils.ScriptVars.set(" + target + ", \"timer\", 2.5f);"), "package-private: through ScriptVars");
		check(code.contains(cls + ".gravity = -1.5f;"), "public static: on the class");
		check(code.contains("com.star4droid.template.Utils.ScriptVars.setStatic(" + cls + ".class, \"count\", 3);"), "package-private static: through ScriptVars on the class");
		check(code.contains("ScriptVars.set(" + target + ", \"secret\", 9);") && code.contains("\"tag\", \"yo \\\"q\\\"\");"), "@Expose private / protected");
		check(code.contains(target + ".nums = new java.util.ArrayList<Integer>(java.util.Arrays.asList(7, 8));"), "public List<Integer>");
		check(code.contains("\"names\", new java.util.ArrayList<String>(java.util.Arrays.asList(\"a\", \"b\")))"), "package-private ArrayList<String>");
		check(code.contains("\"weights\", new float[]{1.0f, 2.5f})"), "float[]");
		check(code.contains("\"bigList\", new java.util.LinkedList<Integer>(java.util.Arrays.asList(1)))"), "LinkedList");
		check(!code.contains("gone") && !code.contains("$auto"), "unknown keys and bookkeeping are not emitted");
		Files_write(out, code);

		// references, including inner nodes of a UI holder
		Map<String, Object> refs = new LinkedHashMap<String, Object>();
		refs.put("target", "e1");
		refs.put("targets", Arrays.<Object>asList("a", "b"));
		refs.put("bars", Arrays.<Object>asList("h1"));
		refs.put("score", "hud/score");
		refs.put("labels", Arrays.<Object>asList("hud/a", "hud/b"));
		String rc = BehaviorCodeGen.forItem(env, "holder", "", BehaviorData.varsToJson(refs), file);
		check(rc.contains("\"score\", com.star4droid.template.behaviors.BehaviorRegistry.ref(this, \"hud/score\", com.star4droid.template.ui.UINodeRef.class));"), "UI node reference");
		check(rc.contains("\"labels\", com.star4droid.template.behaviors.BehaviorRegistry.refs(this, com.star4droid.template.ui.UINodeRef.class, \"hud/a\", \"hud/b\"));"), "UI node list");
		check(rc.contains(".target = com.star4droid.template.behaviors.BehaviorRegistry.ref(this, \"e1\", com.star4droid.template.Utils.PlayerItem.class);"), "public element reference");
		check(rc.contains("new com.star4droid.template.Items.ProgressItem[0]"), "array of references");
		check(rc.contains("ScriptVars.set(" + target + ", \"targets\", com.star4droid.template.behaviors.BehaviorRegistry.refs("), "reference list of a package-private field goes through ScriptVars");
		Files_write(out + ".refs", rc);
	}

	static void Files_write(String p, String s) throws Exception {
		java.nio.file.Files.write(new File(p).toPath(), s.getBytes("UTF-8"));
	}
}
