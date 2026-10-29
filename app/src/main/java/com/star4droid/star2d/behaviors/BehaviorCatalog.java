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

	private static volatile List<BehaviorMeta> builtIns;
	private static volatile Map<String, BehaviorMeta> builtInIndex;
	private static final Object BUILD_LOCK = new Object();
	private static final Map<String, Object[]> projectCache = new HashMap<String, Object[]>(); // path -> {lastModified, meta}

	/** True once the built-in library has been read (reflection over every class takes a while the first time: do it off the UI thread). */
	public static boolean isWarm() {
		return builtIns != null;
	}

	/** Starts reading the built-in library on a low priority background thread (no-op when done or already running). */
	public static void prewarm() {
		if (builtIns != null) return;
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				builtIns();
			}
		}, "behavior-catalog");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY + 1);
		t.start();
	}

	public static List<BehaviorMeta> builtIns() {
		List<BehaviorMeta> r = builtIns;
		if (r != null) return r;
		synchronized (BUILD_LOCK) {
			if (builtIns == null) {
				List<BehaviorMeta> l = new ArrayList<BehaviorMeta>();
				for (Class<?> c : ReflectScanner.builtInClasses()) {
					try {
						BehaviorMeta m = ReflectScanner.behavior(c);
						if (m != null) l.add(m);
					} catch (Throwable ignored) {
					}
				}
				sort(l);
				Map<String, BehaviorMeta> idx = new HashMap<String, BehaviorMeta>();
				for (BehaviorMeta m : l) if (!idx.containsKey(m.id)) idx.put(m.id, m);
				builtInIndex = idx;
				builtIns = Collections.unmodifiableList(l);
			}
			return builtIns;
		}
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

	public static List<BehaviorMeta> project(String projectPath) {
		synchronized (projectCache) {
			return projectLocked(projectPath);
		}
	}

	private static List<BehaviorMeta> projectLocked(String projectPath) {
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
		if (id == null) return null;
		for (BehaviorMeta m : project(projectPath)) if (m.id.equals(id)) return m;
		builtIns();
		return builtInIndex.get(id);
	}

	/** The built-in behavior with this id (no project lookup, no file access), null if there is none. */
	public static BehaviorMeta builtIn(String id) {
		builtIns();
		return id == null ? null : builtInIndex.get(id);
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
