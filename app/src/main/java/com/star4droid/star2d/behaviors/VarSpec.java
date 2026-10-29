package com.star4droid.star2d.behaviors;

import java.util.ArrayList;
import java.util.List;

/**
 * One editable public variable of a behavior or script: what the inspector shows and what the code generator emits.
 * No Android classes here (also used by tools/test_behaviors.py through a small Java main).
 *
 * Value model (the same in the JSON stored in the scene and in {@link #def}):
 * numbers -> Double (integers are rounded on emit), boolean -> Boolean, String/Color("#RRGGBB[AA]")/enum constant/image/sound
 * -> String, element references -> element name String, lists / arrays -> List of those.
 */
public final class VarSpec {
	public enum Kind { INT, LONG, SHORT, BYTE, FLOAT, DOUBLE, BOOL, STRING, COLOR, ENUM, IMAGE, SOUND, REF }

	public String name;
	public Kind kind;
	/** true for List&lt;T&gt;, ArrayList&lt;T&gt; and T[]. */
	public boolean list;
	/** true for T[] (emitted as new T[]{...}). */
	public boolean array;
	/** REF: simple class name of the wanted element type (PlayerItem, ProgressItem...). ENUM: fully qualified enum name. */
	public String elemType = "";
	/** ENUM constants. */
	public List<String> options = new ArrayList<String>();
	public String label = "", desc = "", unit = "", header = "";
	public double min = -1e9, max = 1e9, step = 0;
	/** Default of the code (shown as placeholder / used by reset). null = unknown (no initializer or not understood). */
	public Object def;
	/** Scripts only: a static field (assigned on the class). */
	public boolean isStatic;
	/** Scripts only: "public", "package" (no modifier), or "private" / "protected" (only with @Expose). Not public = set through ScriptVars (reflection). */
	public String access = "public";
	/** Scripts only: the collection written in the code (List, ArrayList, LinkedList, Collection); "" for arrays and scalars. */
	public String container = "";
	/** true when found by scanning the user's own script (Properties shows the "auto" badge). */
	public boolean auto;

	public VarSpec(String name, Kind kind) {
		this.name = name;
		this.kind = kind;
	}

	public boolean isInteger() {
		return kind == Kind.INT || kind == Kind.LONG || kind == Kind.SHORT || kind == Kind.BYTE;
	}

	public boolean isNumber() {
		return isInteger() || kind == Kind.FLOAT || kind == Kind.DOUBLE;
	}

	/** Assigned with a plain field access from the generated code (public instance / public static)? */
	public boolean direct() {
		return "public".equals(access);
	}

	/** Type as written in the code ("float", "List&lt;PlayerItem&gt;", "int[]"): used to notice type changes. */
	public String signature() {
		String e = (kind == Kind.ENUM || kind == Kind.REF) ? elemType : javaType();
		return list ? (array ? e + "[]" : "List<" + e + ">") : e;
	}

	/** "speed" -> "Speed", "jumpSpeed" -> "Jump speed", "bullet_size" -> "Bullet size". */
	public String displayLabel() {
		if (label != null && label.length() > 0) return label;
		return humanize(name);
	}

	public static String humanize(String n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n.length(); i++) {
			char ch = n.charAt(i);
			if (ch == '_') {
				sb.append(' ');
				continue;
			}
			if (i > 0 && Character.isUpperCase(ch) && !Character.isUpperCase(n.charAt(i - 1)) && n.charAt(i - 1) != '_') sb.append(' ').append(Character.toLowerCase(ch));
			else sb.append(i == 0 ? Character.toUpperCase(ch) : ch);
		}
		return sb.toString();
	}

	/** Java type text for a generic argument (List&lt;Integer&gt;...), fully qualified where needed. */
	public String boxedJavaType() {
		switch (kind) {
			case INT: return "Integer";
			case LONG: return "Long";
			case SHORT: return "Short";
			case BYTE: return "Byte";
			case FLOAT: return "Float";
			case DOUBLE: return "Double";
			case BOOL: return "Boolean";
			case COLOR: return "com.badlogic.gdx.graphics.Color";
			case ENUM: return elemType;
			case REF: return ElementTypes.fqn(elemType);
			default: return "String";
		}
	}

	/** Same, unboxed for arrays (int[]...). */
	public String javaType() {
		switch (kind) {
			case INT: return "int";
			case LONG: return "long";
			case SHORT: return "short";
			case BYTE: return "byte";
			case FLOAT: return "float";
			case DOUBLE: return "double";
			case BOOL: return "boolean";
			default: return boxedJavaType();
		}
	}

	@Override
	public String toString() {
		return name + ":" + kind + (list ? (array ? "[]" : "<>") : "") + (def != null ? "=" + def : "");
	}
}
