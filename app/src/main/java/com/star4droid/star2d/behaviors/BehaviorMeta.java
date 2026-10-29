package com.star4droid.star2d.behaviors;

import java.util.ArrayList;
import java.util.List;

/** What the editor knows about one behavior (built-in via reflection, project ones via the source scanner). */
public final class BehaviorMeta {
	public static final String UI = "UI", PHYSICS = "PHYSICS", BOTH = "BOTH";

	/** Class simple name = the id stored in the scene. */
	public String id;
	public String name = "", icon = "puzzle", desc = "", category = "General", targets = BOTH, version = "1.0", author = "";
	public boolean builtin;
	/** Fully qualified class name for the generated code. */
	public String fqn = "";
	/** Source file of a project behavior, "" for built-ins. */
	public String file = "";
	/** @Exclusive group ("movement"...), "" = none. */
	public String exclusive = "";
	/** @Requires: physical element (body) / UI element / body type ("DYNAMIC"...) / explanation. */
	public boolean reqBody, reqUi;
	public String reqType = "", reqNote = "";
	/** @Sets: physics settings applied at runtime start, key (gravityScale, fixedRotation, type, bullet, sensor, linearDamping, angularDamping) -> value. */
	public java.util.LinkedHashMap<String, String> sets = new java.util.LinkedHashMap<String, String>();
	public List<EventSpec> events = new ArrayList<EventSpec>();
	public List<VarSpec> vars = new ArrayList<VarSpec>();

	/** onHit(PlayerItem enemy, float damage) */
	public static final class EventSpec {
		public String name = "";
		/** {type, name} pairs, types as written in the signature. */
		public List<String[]> args = new ArrayList<String[]>();

		public static EventSpec parse(String sig) {
			EventSpec e = new EventSpec();
			sig = sig.trim();
			int p = sig.indexOf('(');
			if (p < 0) {
				e.name = sig;
				return e;
			}
			e.name = sig.substring(0, p).trim();
			int q = sig.lastIndexOf(')');
			String inner = q > p ? sig.substring(p + 1, q).trim() : "";
			if (inner.length() > 0)
				for (String part : inner.split(",")) {
					String[] w = part.trim().split("\\s+");
					if (w.length >= 2) e.args.add(new String[] {w[w.length - 2], w[w.length - 1]});
				}
			return e;
		}

		public String signature() {
			StringBuilder sb = new StringBuilder(name).append('(');
			for (int i = 0; i < args.size(); i++) sb.append(i > 0 ? ", " : "").append(args.get(i)[0]).append(' ').append(args.get(i)[1]);
			return sb.append(')').toString();
		}
	}

	/** Saved property name for a @Sets key (Box/Circle/Custom elements), null if it has no saved twin. */
	public static String propertyOf(String key) {
		if (key.equals("gravityScale")) return "Gravity Scale";
		if (key.equals("fixedRotation")) return "Fixed Rotation";
		if (key.equals("type")) return "type";
		if (key.equals("bullet")) return "Bullet";
		if (key.equals("sensor")) return "isSensor";
		return null;
	}

	/**
	 * Why this behavior does not fit an element, or null when it does.
	 * @param elementType the element's "type" property (DYNAMIC / KINEMATIC / STATIC / UI) or "" when unknown
	 */
	public String misfit(String elementType) {
		if (elementType == null || elementType.length() == 0) return null;
		boolean ui = elementType.equals("UI");
		if (reqUi && !ui) return "Needs a UI element";
		if (reqBody && ui) return "Needs a physical element (this one is UI)" + (reqNote.length() > 0 ? ". " + reqNote : "");
		if (reqBody && reqType.length() > 0 && !reqType.equalsIgnoreCase(elementType))
			return "Needs a " + reqType + " body (this one is " + elementType + ")" + (reqNote.length() > 0 ? ". " + reqNote : "");
		if (!reqBody && !reqUi) {
			if (targets.equals(PHYSICS) && ui) return "Made for physical elements";
			if (targets.equals(UI) && !ui) return "Made for UI elements";
		}
		return null;
	}

	/** One line for hints: "needs DYNAMIC body · sets fixedRotation=true". */
	public String requirementsText() {
		StringBuilder sb = new StringBuilder();
		if (reqUi) sb.append("UI element");
		if (reqBody) sb.append("physical element").append(reqType.length() > 0 ? " (" + reqType + ")" : "");
		if (!sets.isEmpty()) {
			if (sb.length() > 0) sb.append(" \u00b7 ");
			sb.append("sets ");
			boolean first = true;
			for (java.util.Map.Entry<String, String> e : sets.entrySet()) {
				sb.append(first ? "" : ", ").append(e.getKey()).append('=').append(e.getValue());
				first = false;
			}
		}
		return sb.toString();
	}

	public boolean fits(boolean isUi) {
		return BOTH.equals(targets) || (isUi ? UI.equals(targets) : PHYSICS.equals(targets));
	}

	public VarSpec var(String n) {
		for (VarSpec v : vars) if (v.name.equals(n)) return v;
		return null;
	}

	public String title() {
		return name != null && name.length() > 0 ? name : VarSpec.humanize(id);
	}
}
