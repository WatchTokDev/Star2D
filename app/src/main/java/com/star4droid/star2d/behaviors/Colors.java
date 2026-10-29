package com.star4droid.star2d.behaviors;

import java.util.HashMap;
import java.util.Locale;

/** Colors as "#RRGGBB" / "#RRGGBBAA" strings, plus parsing of Java color expressions from source. */
public final class Colors {
	private Colors() {}

	private static final HashMap<String, String> NAMED = new HashMap<String, String>();

	static {
		NAMED.put("WHITE", "#FFFFFF");
		NAMED.put("BLACK", "#000000");
		NAMED.put("RED", "#FF0000");
		NAMED.put("GREEN", "#00FF00");
		NAMED.put("BLUE", "#0000FF");
		NAMED.put("YELLOW", "#FFFF00");
		NAMED.put("CYAN", "#00FFFF");
		NAMED.put("MAGENTA", "#FF00FF");
		NAMED.put("ORANGE", "#FFC800");
		NAMED.put("PINK", "#FFAFAF");
		NAMED.put("PURPLE", "#A020F0");
		NAMED.put("BROWN", "#8B4513");
		NAMED.put("GRAY", "#7F7F7F");
		NAMED.put("LIGHT_GRAY", "#BFBFBF");
		NAMED.put("DARK_GRAY", "#3F3F3F");
		NAMED.put("GOLD", "#FFD700");
		NAMED.put("CLEAR", "#00000000");
	}

	/** Normalized "#RRGGBB" (or "#RRGGBBAA" when not opaque), upper case; null if not a color. */
	public static String normalize(String s) {
		if (s == null) return null;
		String t = s.trim();
		if (t.startsWith("#")) t = t.substring(1);
		if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
		if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
		if (t.length() != 6 && t.length() != 8) return null;
		for (int i = 0; i < t.length(); i++) if (Character.digit(t.charAt(i), 16) < 0) return null;
		t = t.toUpperCase(Locale.ROOT);
		if (t.length() == 8 && t.endsWith("FF")) t = t.substring(0, 6);
		return "#" + t;
	}

	/** Color.valueOf("FFD740"), Color.RED, new Color(r,g,b,a) or new Color(0xRRGGBBAA). */
	public static String parseJava(String expr) {
		String e = expr.trim();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:com\\.badlogic\\.gdx\\.graphics\\.)?Color\\.valueOf\\(\\s*\"([^\"]*)\"\\s*\\)$").matcher(e);
		if (m.find()) return normalize(m.group(1));
		m = java.util.regex.Pattern.compile("^(?:com\\.badlogic\\.gdx\\.graphics\\.)?Color\\.([A-Z_]+)$").matcher(e);
		if (m.find()) return NAMED.get(m.group(1));
		m = java.util.regex.Pattern.compile("^new\\s+(?:com\\.badlogic\\.gdx\\.graphics\\.)?Color\\((.*)\\)$").matcher(e);
		if (m.find()) {
			String[] parts = m.group(1).split(",");
			try {
				if (parts.length == 1) {
					String x = parts[0].trim();
					long v = x.startsWith("0x") || x.startsWith("0X") ? Long.parseLong(x.substring(2).replaceAll("[lL]$", ""), 16) : Long.parseLong(x);
					return normalize(String.format("%08X", v & 0xFFFFFFFFL));
				}
				if (parts.length == 3 || parts.length == 4) {
					int[] c = new int[4];
					c[3] = 255;
					for (int i = 0; i < parts.length; i++) {
						Double d = Expr.eval(parts[i]);
						if (d == null) return null;
						c[i] = (int) Math.round(Math.max(0, Math.min(1, d)) * 255);
					}
					return normalize(String.format("%02X%02X%02X%02X", c[0], c[1], c[2], c[3]));
				}
			} catch (NumberFormatException ex) {
				return null;
			}
		}
		return null;
	}

	/** Android Color int (ARGB) from a normalized string. */
	public static int toArgb(String hex, int fallback) {
		String n = normalize(hex);
		if (n == null) return fallback;
		String t = n.substring(1);
		int r = Integer.parseInt(t.substring(0, 2), 16), g = Integer.parseInt(t.substring(2, 4), 16), b = Integer.parseInt(t.substring(4, 6), 16);
		int a = t.length() == 8 ? Integer.parseInt(t.substring(6, 8), 16) : 255;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static String fromArgb(int argb) {
		int a = (argb >>> 24) & 0xFF;
		String rgb = String.format("%06X", argb & 0xFFFFFF);
		return a == 255 ? "#" + rgb : "#" + rgb + String.format("%02X", a);
	}
}
