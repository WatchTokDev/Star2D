package com.star4droid.star2d.behaviors;

/** Tiny numeric expression evaluator for Java literals: 5, -2.5f, 1.0f/60, (3+4)*2, 0x10, 1e3, Math.PI, Integer.MAX_VALUE. */
final class Expr {
	private final String s;
	private int p;

	private Expr(String s) {
		this.s = SourceScanner.unmask(s).replace(" ", "").replace("\t", "").replace("\n", "");
	}

	static Double eval(String text) {
		try {
			Expr e = new Expr(text);
			double v = e.sum();
			if (e.p != e.s.length() || Double.isNaN(v)) return null;
			return v;
		} catch (RuntimeException ex) {
			return null;
		}
	}

	private double sum() {
		double v = prod();
		while (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) {
			char op = s.charAt(p++);
			double r = prod();
			v = op == '+' ? v + r : v - r;
		}
		return v;
	}

	private double prod() {
		double v = unary();
		while (p < s.length() && (s.charAt(p) == '*' || s.charAt(p) == '/')) {
			char op = s.charAt(p++);
			double r = unary();
			v = op == '*' ? v * r : v / r;
		}
		return v;
	}

	private double unary() {
		if (p < s.length() && s.charAt(p) == '-') {
			p++;
			return -unary();
		}
		if (p < s.length() && s.charAt(p) == '+') {
			p++;
			return unary();
		}
		// casts like (float) 5 / (int)3
		if (p < s.length() && s.charAt(p) == '(') {
			int close = s.indexOf(')', p);
			if (close > 0) {
				String in = s.substring(p + 1, close);
				if (in.equals("int") || in.equals("float") || in.equals("double") || in.equals("long") || in.equals("short") || in.equals("byte")) {
					p = close + 1;
					return unary();
				}
			}
			p++;
			double v = sum();
			if (p >= s.length() || s.charAt(p) != ')') throw new IllegalStateException();
			p++;
			return v;
		}
		return atom();
	}

	private double atom() {
		int st = p;
		if (s.startsWith("0x", p) || s.startsWith("0X", p)) {
			p += 2;
			int h = p;
			while (p < s.length() && Character.digit(s.charAt(p), 16) >= 0) p++;
			double v = Long.parseLong(s.substring(h, p), 16);
			if (p < s.length() && (s.charAt(p) == 'L' || s.charAt(p) == 'l')) p++;
			return v;
		}
		while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.' || s.charAt(p) == '_')) p++;
		if (p < s.length() && (s.charAt(p) == 'e' || s.charAt(p) == 'E') && p > st) {
			p++;
			if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
			while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
		}
		if (p > st) {
			double v = Double.parseDouble(s.substring(st, p).replace("_", ""));
			if (p < s.length() && "fFdDlL".indexOf(s.charAt(p)) >= 0) p++;
			return v;
		}
		// named constants
		int e = p;
		while (e < s.length() && (Character.isJavaIdentifierPart(s.charAt(e)) || s.charAt(e) == '.')) e++;
		String name = s.substring(p, e);
		p = e;
		if (name.equals("Math.PI")) return Math.PI;
		if (name.equals("Integer.MAX_VALUE")) return Integer.MAX_VALUE;
		if (name.equals("Integer.MIN_VALUE")) return Integer.MIN_VALUE;
		if (name.equals("Float.MAX_VALUE")) return Float.MAX_VALUE;
		if (name.equals("Long.MAX_VALUE")) return Long.MAX_VALUE;
		throw new IllegalStateException(name);
	}
}
