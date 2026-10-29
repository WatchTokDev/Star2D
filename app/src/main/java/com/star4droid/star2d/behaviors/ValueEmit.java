package com.star4droid.star2d.behaviors;

import java.util.List;
import java.util.Locale;

/** Inspector value (see {@link VarSpec}) -> Java expression of the generated scene code. No lambdas: the device compiles Java 1.7. */
public final class ValueEmit {
	private ValueEmit() {}

	public static final String REGISTRY = "com.star4droid.template.behaviors.BehaviorRegistry";

	/** Java expression for the value, or null when the value does not fit the variable. */
	public static String expr(VarSpec v, Object val) {
		if (val == null) return null;
		try {
			if (v.list) {
				List<?> l = asList(val);
				if (l == null) return null;
				if (v.kind == VarSpec.Kind.REF) {
					StringBuilder sb = new StringBuilder(REGISTRY + ".refs(this, " + ElementTypes.fqn(v.elemType) + ".class");
					for (Object o : l) sb.append(", ").append(quote(String.valueOf(o)));
					sb.append(')');
					return v.array ? sb + ".toArray(new " + ElementTypes.fqn(v.elemType) + "[0])" : sb.toString();
				}
				StringBuilder items = new StringBuilder();
				for (int i = 0; i < l.size(); i++) {
					String e = scalar(v, l.get(i));
					if (e == null) return null;
					items.append(i > 0 ? ", " : "").append(e);
				}
				if (v.array) return "new " + v.javaType() + "[]{" + items + "}";
				if (l.isEmpty()) return "new java.util.ArrayList<" + v.boxedJavaType() + ">()";
				return "new java.util.ArrayList<" + v.boxedJavaType() + ">(java.util.Arrays.asList(" + items + "))";
			}
			return scalar(v, val);
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private static List<?> asList(Object o) {
		return o instanceof List ? (List<?>) o : null;
	}

	private static String scalar(VarSpec v, Object o) {
		if (o == null) return null;
		switch (v.kind) {
			case INT: return String.valueOf(clampLong(num(o), Integer.MIN_VALUE, Integer.MAX_VALUE));
			case LONG: return clampLong(num(o), Long.MIN_VALUE, Long.MAX_VALUE) + "L";
			case SHORT: return "(short)" + clampLong(num(o), Short.MIN_VALUE, Short.MAX_VALUE);
			case BYTE: return "(byte)" + clampLong(num(o), Byte.MIN_VALUE, Byte.MAX_VALUE);
			case FLOAT: {
				float f = (float) num(o);
				if (Float.isNaN(f) || Float.isInfinite(f)) return null;
				return Float.toString(f) + "f";
			}
			case DOUBLE: {
				double d = num(o);
				if (Double.isNaN(d) || Double.isInfinite(d)) return null;
				return Double.toString(d);
			}
			case BOOL:
				if (o instanceof Boolean) return o.toString();
				return "true".equalsIgnoreCase(String.valueOf(o)) ? "true" : "false";
			case STRING:
			case IMAGE:
			case SOUND:
				return quote(String.valueOf(o));
			case COLOR: {
				String c = Colors.normalize(String.valueOf(o));
				return c == null ? null : "com.badlogic.gdx.graphics.Color.valueOf(\"" + c.substring(1) + "\")";
			}
			case ENUM: {
				String c = String.valueOf(o);
				return v.options.contains(c) ? v.elemType + "." + c : null;
			}
			case REF:
				return REGISTRY + ".ref(this, " + quote(String.valueOf(o)) + ", " + ElementTypes.fqn(v.elemType) + ".class)";
			default:
				return null;
		}
	}

	private static double num(Object o) {
		if (o instanceof Number) return ((Number) o).doubleValue();
		return Double.parseDouble(String.valueOf(o).trim());
	}

	private static long clampLong(double d, long min, long max) {
		long r = Math.round(d);
		return Math.max(min, Math.min(max, r));
	}

	public static String quote(String s) {
		StringBuilder o = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"': o.append("\\\""); break;
				case '\\': o.append("\\\\"); break;
				case '\n': o.append("\\n"); break;
				case '\r': o.append("\\r"); break;
				case '\t': o.append("\\t"); break;
				default:
					if (c < 32 || c > 126) o.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					else o.append(c);
			}
		}
		return o.append('"').toString();
	}
}
