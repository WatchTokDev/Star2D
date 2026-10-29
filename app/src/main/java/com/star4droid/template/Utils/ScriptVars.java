package com.star4droid.template.Utils;

import java.lang.reflect.Field;

/**
 * Sets the fields of a script that are not public (package-private, or private with {@link Expose}) from the generated scene code.
 * Plain reflection on the game's own classes (fine on Android); an unknown field or a value that does not fit is logged and skipped,
 * it never stops the scene from loading.
 */
public final class ScriptVars {
	private ScriptVars() {}

	/** {@code target.field = value} */
	public static void set(Object target, String field, Object value) {
		if (target == null) return;
		put(target.getClass(), target, field, value);
	}

	/** {@code cls.field = value} (static field) */
	public static void setStatic(Class<?> cls, String field, Object value) {
		put(cls, null, field, value);
	}

	private static void put(Class<?> cls, Object target, String name, Object value) {
		try {
			Field f = null;
			for (Class<?> c = cls; c != null && f == null; c = c.getSuperclass()) {
				try {
					f = c.getDeclaredField(name);
				} catch (NoSuchFieldException ignored) {
				}
			}
			if (f == null) throw new NoSuchFieldException(cls.getName() + "." + name);
			f.setAccessible(true);
			f.set(target, convert(f.getType(), value));
		} catch (Throwable t) {
			try {
				Utils.Log("script variable " + name, Utils.getStackTraceString(t));
			} catch (Throwable ignored) {
			}
		}
	}

	/** Numbers are converted to the field's number type (the editor stores every number as a double). */
	private static Object convert(Class<?> t, Object v) {
		if (!(v instanceof Number)) return v;
		Number n = (Number) v;
		if (t == int.class || t == Integer.class) return Integer.valueOf(n.intValue());
		if (t == long.class || t == Long.class) return Long.valueOf(n.longValue());
		if (t == float.class || t == Float.class) return Float.valueOf(n.floatValue());
		if (t == double.class || t == Double.class) return Double.valueOf(n.doubleValue());
		if (t == short.class || t == Short.class) return Short.valueOf(n.shortValue());
		if (t == byte.class || t == Byte.class) return Byte.valueOf(n.byteValue());
		return v;
	}
}
