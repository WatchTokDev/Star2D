package com.star4droid.star2d.behaviors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All behaviors the editor knows: the built-in library plus the project's own (java/com/star4droid/Game/Behaviors/*.java). */
public final class BehaviorCatalog {
	private BehaviorCatalog() {}

	/** Project behaviors live in this package / folder (the on-device compiler picks every .java under java/ up). */
	public static final String PROJECT_PACKAGE = "com.star4droid.Game.Behaviors";
	public static final String PROJECT_DIR = "java/com/star4droid/Game/Behaviors";

	private static List<BehaviorMeta> builtIns;
	private static final Map<String, Object[]> projectCache = new HashMap<String, Object[]>(); // path -> {lastModified, meta}

	public static synchronized List<BehaviorMeta> builtIns() {
		if (builtIns == null) {
			builtIns = new ArrayList<BehaviorMeta>();
			for (Class<?> c : ReflectScanner.builtInClasses()) {
				try {
					BehaviorMeta m = ReflectScanner.behavior(c);
					if (m != null) builtIns.add(m);
				} catch (Throwable ignored) {
				}
			}
			sort(builtIns);
		}
		return builtIns;
	}

	private static void sort(List<BehaviorMeta> l) {
		Collections.sort(l, new Comparator<BehaviorMeta>() {
			@Override
			public int compare(BehaviorMeta a, BehaviorMeta b) {
				int c = a.category.compareToIgnoreCase(b.category);
				return c != 0 ? c : a.title().compareToIgnoreCase(b.title());
			}
		});
	}

	public static File projectDir(String projectPath) {
		return new File(projectPath, PROJECT_DIR);
	}

	public static synchronized List<BehaviorMeta> project(String projectPath) {
		List<BehaviorMeta> out = new ArrayList<BehaviorMeta>();
		if (projectPath == null) return out;
		File[] files = projectDir(projectPath).listFiles();
		if (files == null) return out;
		for (File f : files) {
			if (!f.isFile() || !f.getName().endsWith(".java")) continue;
			Object[] c = projectCache.get(f.getPath());
			BehaviorMeta m;
			if (c != null && ((Long) c[0]) == f.lastModified() + f.length()) m = (BehaviorMeta) c[1];
			else {
				m = SourceScanner.behavior(readFile(f), f.getPath());
				projectCache.put(f.getPath(), new Object[] {f.lastModified() + f.length(), m});
			}
			if (m != null) out.add(m);
		}
		sort(out);
		return out;
	}

	public static List<BehaviorMeta> all(String projectPath) {
		List<BehaviorMeta> out = new ArrayList<BehaviorMeta>(builtIns());
		out.addAll(project(projectPath));
		return out;
	}

	/** Project behaviors win over built-ins with the same id (a copy the user edited). */
	public static BehaviorMeta find(String id, String projectPath) {
		for (BehaviorMeta m : project(projectPath)) if (m.id.equals(id)) return m;
		for (BehaviorMeta m : builtIns()) if (m.id.equals(id)) return m;
		return null;
	}

	public static String readFile(File f) {
		try {
			FileInputStream in = new FileInputStream(f);
			try {
				byte[] b = new byte[(int) f.length()];
				int off = 0;
				while (off < b.length) {
					int r = in.read(b, off, b.length - off);
					if (r < 0) break;
					off += r;
				}
				return new String(b, 0, off, "UTF-8");
			} finally {
				in.close();
			}
		} catch (IOException ex) {
			return "";
		}
	}
}
