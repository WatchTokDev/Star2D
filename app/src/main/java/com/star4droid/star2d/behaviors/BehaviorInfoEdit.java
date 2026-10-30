package com.star4droid.star2d.behaviors;

/**
 * Rewrites attributes of the {@code @BehaviorInfo(...)} header of a project behavior's source (name, icon, desc, category,
 * targets) without touching anything else. Pure Java.
 */
public final class BehaviorInfoEdit {
	private BehaviorInfoEdit() {}

	/** Java string literal for the text. */
	public static String quote(String s) {
		StringBuilder b = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"' || c == '\\') b.append('\\').append(c);
			else if (c == '\n') b.append("\\n");
			else if (c == '\r') continue;
			else b.append(c);
		}
		return b.append('"').toString();
	}

	/**
	 * Sets {@code key = valueExpr} inside @BehaviorInfo (replacing the old value, or adding it before the closing parenthesis).
	 * Returns the new source, or null when the source has no @BehaviorInfo header.
	 */
	public static String set(String src, String key, String valueExpr) {
		int at = src.indexOf("@BehaviorInfo");
		if (at < 0) return null;
		int open = src.indexOf('(', at);
		if (open < 0) return null;
		int close = SourceScanner.matching(src, open);
		if (close < 0) return null;
		String body = src.substring(open + 1, close);
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])" + key + "\\s*=\\s*").matcher(body);
		while (m.find()) {
			// the value ends at the next top level comma / the end (strings and brackets skipped)
			int start = m.end(), i = start, depth = 0;
			boolean str = false;
			for (; i < body.length(); i++) {
				char c = body.charAt(i);
				if (str) {
					if (c == '\\') i++;
					else if (c == '"') str = false;
				} else if (c == '"') str = true;
				else if (c == '(' || c == '{' || c == '[') depth++;
				else if (c == ')' || c == '}' || c == ']') depth--;
				else if (c == ',' && depth == 0) break;
				else if (c == '/' && i + 1 < body.length() && body.charAt(i + 1) == '/' && depth == 0) break;
			}
			// keep trailing whitespace before the comma
			int end = i;
			while (end > start && Character.isWhitespace(body.charAt(end - 1))) end--;
			return src.substring(0, open + 1) + body.substring(0, start) + valueExpr + body.substring(end) + src.substring(close);
		}
		// a new attribute goes first (no comma problems with a trailing comment on the last line)
		boolean empty = body.trim().isEmpty();
		return src.substring(0, open + 1) + "\n    " + key + " = " + valueExpr + (empty ? "\n" : ",") + body + src.substring(close);
	}
}
