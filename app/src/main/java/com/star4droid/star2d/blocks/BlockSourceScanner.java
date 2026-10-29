package com.star4droid.star2d.blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the {@code @Block} methods in the SOURCE text of a behavior (the project's own behaviors are not compiled while the
 * user edits them). Forgiving: anything it does not understand is skipped, it never throws.
 *
 * Understands: {@code @Block(label = "..", desc = ".." + "..", labelAr = "..", descAr = "..", category = "..", args = {"a", "b"})}
 * followed by {@code public [final] <type> name(<type> p, ...)}.
 */
public final class BlockSourceScanner {
	private BlockSourceScanner() {}

	/** @param defaultPackage package used when the file has no package line */
	public static List<BehaviorBlocks.Spec> scan(String source, String defaultPackage) {
		List<BehaviorBlocks.Spec> out = new ArrayList<BehaviorBlocks.Spec>();
		try {
			if (source == null || source.indexOf("@Block") < 0) return out;
			String s = stripComments(source);
			String pkg = defaultPackage == null ? "" : defaultPackage;
			int pi = indexOfWord(s, "package", 0);
			if (pi >= 0) {
				int e = s.indexOf(';', pi);
				if (e > pi) pkg = s.substring(pi + 7, e).trim();
			}
			String cls = className(s);
			if (cls == null) return out;
			String fqn = pkg.length() > 0 ? pkg + "." + cls : cls;
			int from = 0;
			while (true) {
				int at = s.indexOf("@Block", from);
				if (at < 0) break;
				from = at + 6;
				int p = skipWs(s, at + 6);
				// "@Block" must be the whole word and have an argument list (label is required)
				if (p >= s.length() || s.charAt(p) != '(') continue;
				if (at > 0 && Character.isJavaIdentifierPart(s.charAt(at - 1))) continue;
				int close = matching(s, p);
				if (close < 0) continue;
				String args = s.substring(p + 1, close);
				from = close + 1;
				BehaviorBlocks.Spec spec = method(s, close + 1, args, cls, fqn);
				if (spec != null) out.add(spec);
			}
			BehaviorBlocks.finish(out, cls, cls);
		} catch (Throwable ignored) {
		}
		return out;
	}

	/** Parses the signature that follows an annotation. */
	private static BehaviorBlocks.Spec method(String s, int pos, String annotationArgs, String cls, String fqn) {
		int p = skipAnnotations(s, pos);
		int brace = nextOf(s, p, "{;");
		if (brace < 0) return null;
		String head = s.substring(p, brace).trim();
		int lp = head.indexOf('(');
		int rp = head.lastIndexOf(')');
		if (lp < 0 || rp < lp) return null;
		String before = head.substring(0, lp).trim();
		String[] w = before.split("\\s+");
		if (w.length < 2) return null;
		String name = w[w.length - 1];
		String ret = w[w.length - 2];
		boolean isPublic = false;
		for (int i = 0; i < w.length - 2; i++) {
			if (w[i].equals("public")) isPublic = true;
			if (w[i].equals("static") || w[i].equals("abstract")) return null;
		}
		if (!isPublic || !name.matches("[A-Za-z_][A-Za-z0-9_]*") || ret.indexOf('<') >= 0) return null;
		String rk = BehaviorBlocks.returnKind(ret);
		if (rk == null) return null;
		List<BehaviorBlocks.Param> ps = new ArrayList<BehaviorBlocks.Param>();
		String inner = head.substring(lp + 1, rp).trim();
		if (inner.length() > 0) {
			for (String part : splitTop(inner)) {
				String[] t = part.trim().split("\\s+");
				if (t.length < 2) return null;
				String type = t[t.length - 2];
				if (type.equals("final")) return null;
				String kind = BehaviorBlocks.paramKind(type);
				if (kind == null) return null;
				BehaviorBlocks.Param bp = new BehaviorBlocks.Param();
				bp.kind = kind;
				bp.javaType = type;
				bp.name = t[t.length - 1];
				ps.add(bp);
			}
		}
		BehaviorBlocks.Spec spec = new BehaviorBlocks.Spec();
		spec.behaviorId = cls;
		spec.fqn = fqn;
		spec.method = name;
		spec.ret = rk;
		spec.retType = ret;
		spec.params = ps;
		String[] argNames = null;
		for (String[] kv : splitArgs(annotationArgs)) {
			String k = kv[0], v = kv[1];
			if (k.equals("label") || k.length() == 0) spec.label = literal(v);
			else if (k.equals("desc")) spec.desc = literal(v);
			else if (k.equals("labelAr")) spec.labelAr = literal(v);
			else if (k.equals("descAr")) spec.descAr = literal(v);
			else if (k.equals("category")) spec.category = literal(v);
			else if (k.equals("args")) argNames = literalArray(v);
		}
		if (spec.label.trim().length() == 0) return null;
		BehaviorBlocks.fixNames(ps, argNames);
		return spec;
	}

	// ------------------------------------------------------------------ annotation arguments

	/** key = value pairs split at top-level commas ({@code {..}} arrays and strings kept whole). A lone value gets the key "". */
	static List<String[]> splitArgs(String a) {
		List<String[]> out = new ArrayList<String[]>();
		for (String part : splitTop(a)) {
			int eq = topEquals(part);
			if (eq < 0) out.add(new String[] {"", part.trim()});
			else out.add(new String[] {part.substring(0, eq).trim(), part.substring(eq + 1).trim()});
		}
		return out;
	}

	private static int topEquals(String s) {
		boolean str = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (str) {
				if (c == '\\') i++;
				else if (c == '"') str = false;
			} else if (c == '"') str = true;
			else if (c == '=') return i;
			else if (c == '{' || c == '(') return -1;
		}
		return -1;
	}

	/** Splits at commas that are not inside strings or (), {}. */
	static List<String> splitTop(String s) {
		List<String> out = new ArrayList<String>();
		int depth = 0, last = 0;
		boolean str = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (str) {
				if (c == '\\') i++;
				else if (c == '"') str = false;
				continue;
			}
			if (c == '"') str = true;
			else if (c == '(' || c == '{' || c == '<') depth++;
			else if (c == ')' || c == '}' || c == '>') depth--;
			else if (c == ',' && depth == 0) {
				out.add(s.substring(last, i));
				last = i + 1;
			}
		}
		if (last < s.length()) out.add(s.substring(last));
		return out;
	}

	/** "a" + "b" -> ab (string literals joined with +). */
	static String literal(String v) {
		StringBuilder sb = new StringBuilder();
		int i = 0, n = v.length();
		while (i < n) {
			char c = v.charAt(i);
			if (c == '"') {
				i++;
				while (i < n && v.charAt(i) != '"') {
					char d = v.charAt(i);
					if (d == '\\' && i + 1 < n) {
						char e = v.charAt(i + 1);
						i += 2;
						switch (e) {
							case 'n': sb.append('\n'); break;
							case 't': sb.append('\t'); break;
							case 'r': break;
							case 'u':
								if (i + 4 <= n) {
									try {
										sb.append((char) Integer.parseInt(v.substring(i, i + 4), 16));
										i += 4;
									} catch (NumberFormatException ex) {
										sb.append('u');
									}
								}
								break;
							default: sb.append(e);
						}
						continue;
					}
					sb.append(d);
					i++;
				}
				i++;
			} else i++;
		}
		return sb.toString();
	}

	/** {"a", "b"} -> [a, b]; a single string works too. */
	static String[] literalArray(String v) {
		String t = v.trim();
		if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length() - 1);
		List<String> parts = splitTop(t);
		List<String> out = new ArrayList<String>();
		for (String p : parts) if (p.trim().length() > 0) out.add(literal(p));
		return out.toArray(new String[0]);
	}

	// ------------------------------------------------------------------ text helpers

	/** Comments -> spaces (string and char literals are kept). */
	static String stripComments(String s) {
		StringBuilder o = new StringBuilder(s.length());
		int n = s.length();
		for (int i = 0; i < n; i++) {
			char c = s.charAt(i);
			if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
				while (i < n && s.charAt(i) != '\n') {
					o.append(' ');
					i++;
				}
				if (i < n) o.append('\n');
				continue;
			}
			if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
				i += 2;
				o.append("  ");
				while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
					o.append(s.charAt(i) == '\n' ? '\n' : ' ');
					i++;
				}
				o.append("  ");
				i++;
				continue;
			}
			if (c == '"' || c == '\'') {
				char q = c;
				o.append(c);
				i++;
				while (i < n && s.charAt(i) != q && s.charAt(i) != '\n') {
					o.append(s.charAt(i));
					if (s.charAt(i) == '\\' && i + 1 < n) {
						i++;
						o.append(s.charAt(i));
					}
					i++;
				}
				if (i < n) o.append(s.charAt(i));
				continue;
			}
			o.append(c);
		}
		return o.toString();
	}

	private static String className(String s) {
		int from = 0;
		while (true) {
			int i = indexOfWord(s, "class", from);
			if (i < 0) return null;
			int p = skipWs(s, i + 5);
			int e = p;
			while (e < s.length() && Character.isJavaIdentifierPart(s.charAt(e))) e++;
			if (e > p) return s.substring(p, e);
			from = i + 5;
		}
	}

	private static int indexOfWord(String s, String w, int from) {
		int i = from;
		while ((i = s.indexOf(w, i)) >= 0) {
			boolean l = i == 0 || !Character.isJavaIdentifierPart(s.charAt(i - 1));
			boolean r = i + w.length() >= s.length() || !Character.isJavaIdentifierPart(s.charAt(i + w.length()));
			if (l && r) return i;
			i += w.length();
		}
		return -1;
	}

	private static int skipWs(String s, int p) {
		while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++;
		return p;
	}

	/** Skips further annotations (@Foo or @Foo(...)) in front of the method. */
	private static int skipAnnotations(String s, int p) {
		p = skipWs(s, p);
		while (p < s.length() && s.charAt(p) == '@') {
			int e = p + 1;
			while (e < s.length() && (Character.isJavaIdentifierPart(s.charAt(e)) || s.charAt(e) == '.')) e++;
			e = skipWs(s, e);
			if (e < s.length() && s.charAt(e) == '(') {
				int c = matching(s, e);
				if (c < 0) return s.length();
				e = c + 1;
			}
			p = skipWs(s, e);
		}
		return p;
	}

	/** Index of the ) that closes the ( at {@code open}, strings aware. */
	private static int matching(String s, int open) {
		int depth = 0;
		boolean str = false;
		for (int i = open; i < s.length(); i++) {
			char c = s.charAt(i);
			if (str) {
				if (c == '\\') i++;
				else if (c == '"') str = false;
				continue;
			}
			if (c == '"') str = true;
			else if (c == '(') depth++;
			else if (c == ')') {
				depth--;
				if (depth == 0) return i;
			}
		}
		return -1;
	}

	private static int nextOf(String s, int from, String chars) {
		for (int i = from; i < s.length(); i++) if (chars.indexOf(s.charAt(i)) >= 0) return i;
		return -1;
	}
}
