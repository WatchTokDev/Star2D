package com.star4droid.star2d.behaviors;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Java statements of the generated scene class that apply an element's behaviors and public-variable overrides.
 * Pure text in / text out (no Android, no Gdx) so tools/test_behaviors.py can run it.
 */
public final class BehaviorCodeGen {
	private BehaviorCodeGen() {}

	/** What the generator needs from the editor. */
	public interface Env {
		String projectPath();

		/** Java code of the event file Events/&lt;scene&gt;/private/&lt;body&gt;/&lt;event&gt;(.java); "" when there is none. */
		String eventCode(String body, String event);

		String readFile(String path);
	}

	private static final String HANDLER = "com.star4droid.template.behaviors.Behavior.Handler";
	private static final String PI = "com.star4droid.template.Utils.PlayerItem";

	/** Folder name (relative to the element's event folder) of a behavior's event files: behaviors/&lt;Type&gt;. */
	public static String eventBody(String element, String behaviorId) {
		return element + "/behaviors/" + behaviorId;
	}

	/**
	 * @param scriptFile path of the element's body script (Script.java) or null / missing file when it has none
	 * @return statements, or "" when the element has neither behaviors nor overrides
	 */
	public static String forItem(Env env, String item, String behaviorsJson, String scriptVarsJson, String scriptFile) {
		StringBuilder out = new StringBuilder();
		Map<String, Object> vars = BehaviorData.parseVars(scriptVarsJson);
		if (!vars.isEmpty() && scriptFile != null) {
			SourceScanner.Result r = SourceScanner.scan(env.readFile(scriptFile));
			if (r.className.length() > 0)
				out.append(assignVars("((" + r.fqn() + ") " + item + ".getScript())", r.vars, vars));
		}
		List<BehaviorData.Entry> entries = BehaviorData.parse(behaviorsJson);
		int n = 0;
		for (BehaviorData.Entry e : entries) {
			BehaviorMeta m = BehaviorCatalog.find(e.type, env.projectPath());
			if (m == null) {
				out.append("// behavior ").append(e.type).append(" of ").append(item).append(" was not found (deleted?)\n");
				continue;
			}
			out.append("{\n");
			out.append(m.fqn).append(" __b = new ").append(m.fqn).append("();\n");
			if (!e.enabled) out.append("__b.enabled = false;\n");
			out.append(assignVars("__b", m.vars, e.params));
			for (BehaviorMeta.EventSpec ev : m.events) {
				String code = env.eventCode(eventBody(item, m.id), ev.name);
				if (code == null || code.trim().length() == 0) continue;
				out.append("__b.on(").append(ValueEmit.quote(ev.name)).append(", new ").append(HANDLER).append("() {\n");
				out.append("public void run(").append(PI).append(" current, Object[] args) {\n");
				for (int i = 0; i < ev.args.size(); i++) out.append(argLocal(ev.args.get(i)[0], ev.args.get(i)[1], i));
				out.append(code).append("\n}\n});\n");
			}
			out.append("__b.attach(").append(item).append(", this);\n}\n");
			n++;
		}
		return out.toString();
	}

	/** Scene script: {@code ((fqn) getSceneScript()).x = ...;}. */
	public static String forSceneScript(Env env, String scriptFile, String varsJson) {
		Map<String, Object> vars = BehaviorData.parseVars(varsJson);
		if (vars.isEmpty() || scriptFile == null) return "";
		SourceScanner.Result r = SourceScanner.scan(env.readFile(scriptFile));
		if (r.className.length() == 0) return "";
		return assignVars("((" + r.fqn() + ") getSceneScript())", r.vars, vars);
	}

	private static String assignVars(String target, List<VarSpec> specs, Map<String, Object> values) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Object> e : values.entrySet()) {
			VarSpec spec = null;
			for (VarSpec s : specs) if (s.name.equals(e.getKey())) spec = s;
			if (spec == null) continue;
			String expr = ValueEmit.expr(spec, e.getValue());
			if (expr == null) continue;
			sb.append(target).append('.').append(spec.name).append(" = ").append(expr).append(";\n");
		}
		return sb.toString();
	}

	/** Typed local for handler argument i: {@code float damage = ((Number) args[1]).floatValue();} */
	static String argLocal(String type, String name, int i) {
		String a = "args[" + i + "]";
		String t = type;
		if (t.equals("int")) return "int " + name + " = ((Number) " + a + ").intValue();\n";
		if (t.equals("long")) return "long " + name + " = ((Number) " + a + ").longValue();\n";
		if (t.equals("float")) return "float " + name + " = ((Number) " + a + ").floatValue();\n";
		if (t.equals("double")) return "double " + name + " = ((Number) " + a + ").doubleValue();\n";
		if (t.equals("boolean")) return "boolean " + name + " = ((Boolean) " + a + ").booleanValue();\n";
		if (t.equals("PlayerItem")) t = PI;
		else if (ElementTypes.isElementClass(t)) t = ElementTypes.fqn(t);
		return t + " " + name + " = (" + t + ") " + a + ";\n";
	}

}
