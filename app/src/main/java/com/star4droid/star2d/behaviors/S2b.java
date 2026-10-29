package com.star4droid.star2d.behaviors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The .s2b file: a shareable project behavior. A zip with
 * <pre>
 *   Name.java        the behavior class (package com.star4droid.Game.Behaviors)
 *   behavior.json    {"id","name","icon","description","version","author","format":1}
 *   images/...       optional files the behavior uses (copied to the project's images/ folder)
 *   sounds/...       optional (copied to sounds/)
 * </pre>
 * No Android classes: runs in tools/test_behaviors.py too.
 */
public final class S2b {
	private S2b() {}

	public static final String EXT = "s2b";

	public static final class Info {
		public String id = "", name = "", icon = "puzzle", description = "", version = "1.0", author = "";
		public String javaEntry = "";
		public String source = "";
		public List<String> assets = new ArrayList<String>();
	}

	public static class S2bException extends IOException {
		public final String code;

		public S2bException(String code, String message) {
			super(message);
			this.code = code;
		}
	}

	// ------------------------------------------------------------------------------------------------ export

	/** Writes the behavior (project behaviors only) and the images / sounds its fields default to. */
	public static void export(BehaviorMeta m, String projectPath, File out) throws IOException {
		if (m.builtin || m.file.length() == 0) throw new S2bException("builtin", "Only project behaviors can be exported");
		File src = new File(m.file);
		String javaText = BehaviorCatalog.readFile(src);
		ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
		try {
			put(z, m.id + ".java", javaText.getBytes("UTF-8"));
			JsonObject j = new JsonObject();
			j.addProperty("format", 1);
			j.addProperty("id", m.id);
			j.addProperty("name", m.title());
			j.addProperty("icon", m.icon);
			j.addProperty("description", m.desc);
			j.addProperty("version", m.version);
			j.addProperty("author", m.author);
			put(z, "behavior.json", j.toString().getBytes("UTF-8"));
			for (VarSpec v : m.vars) {
				if (v.kind != VarSpec.Kind.IMAGE && v.kind != VarSpec.Kind.SOUND) continue;
				List<Object> vals = new ArrayList<Object>();
				if (v.def instanceof List) vals.addAll((List<?>) v.def);
				else if (v.def != null) vals.add(v.def);
				for (Object o : vals) {
					String rel = String.valueOf(o);
					if (rel.length() == 0) continue;
					String folder = v.kind == VarSpec.Kind.IMAGE ? "images" : "sounds";
					File f = new File(projectPath + "/" + folder + "/" + rel);
					if (f.isFile() && safeRel(rel)) put(z, folder + "/" + (rel.startsWith("/") ? rel.substring(1) : rel), readAll(f));
				}
			}
		} finally {
			z.close();
		}
	}

	private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
		try {
			z.putNextEntry(new ZipEntry(name));
		} catch (java.util.zip.ZipException dup) {
			return; // same asset listed twice
		}
		z.write(data);
		z.closeEntry();
	}

	// ------------------------------------------------------------------------------------------------ import

	/** Reads a .s2b without installing anything (validates the content). */
	public static Info inspect(File zip) throws IOException {
		Info info = new Info();
		ZipInputStream z = new ZipInputStream(new FileInputStream(zip));
		try {
			ZipEntry e;
			int count = 0;
			while ((e = z.getNextEntry()) != null) {
				if (++count > 500) throw new S2bException("big", "Too many files in the .s2b");
				String name = e.getName();
				if (e.isDirectory()) continue;
				if (!safeRel(name)) throw new S2bException("path", "Unsafe path in the .s2b: " + name);
				byte[] data = readAll(z, 8 * 1024 * 1024);
				if (name.equals("behavior.json")) {
					JsonObject j = JsonParser.parseString(new String(data, "UTF-8")).getAsJsonObject();
					info.id = str(j, "id");
					info.name = str(j, "name");
					if (str(j, "icon").length() > 0) info.icon = str(j, "icon");
					info.description = str(j, "description");
					if (str(j, "version").length() > 0) info.version = str(j, "version");
					info.author = str(j, "author");
				} else if (name.endsWith(".java") && name.indexOf('/') < 0) {
					info.javaEntry = name;
					info.source = new String(data, "UTF-8");
				} else if (name.startsWith("images/") || name.startsWith("sounds/")) {
					info.assets.add(name);
				}
			}
		} catch (IllegalStateException ex) {
			throw new S2bException("json", "behavior.json is not valid");
		} catch (com.google.gson.JsonParseException ex) {
			throw new S2bException("json", "behavior.json is not valid");
		} finally {
			z.close();
		}
		if (info.javaEntry.length() == 0) throw new S2bException("nojava", "No .java file inside");
		SourceScanner.Result r = SourceScanner.scan(info.source);
		if (r.className.length() == 0) throw new S2bException("noclass", "The Java file has no class");
		if (info.id.length() == 0) info.id = r.className;
		if (!info.id.equals(r.className)) info.id = r.className;
		if (info.name.length() == 0) info.name = r.info.containsKey("name") ? r.info.get("name") : VarSpec.humanize(r.className);
		return info;
	}

	/** Copies the behavior into the project; returns its id. Existing files are kept unless overwrite. */
	public static String install(File zip, String projectPath, boolean overwrite) throws IOException {
		Info info = inspect(zip);
		File dir = BehaviorCatalog.projectDir(projectPath);
		dir.mkdirs();
		File target = new File(dir, info.id + ".java");
		if (target.exists() && !overwrite) throw new S2bException("exists", info.id + " already exists");
		String src = fixPackage(info.source);
		write(target, src.getBytes("UTF-8"));
		ZipInputStream z = new ZipInputStream(new FileInputStream(zip));
		try {
			ZipEntry e;
			while ((e = z.getNextEntry()) != null) {
				String n = e.getName();
				if (e.isDirectory() || !(n.startsWith("images/") || n.startsWith("sounds/"))) continue;
				File out = new File(projectPath + "/" + n);
				if (out.exists() && !overwrite) continue;
				write(out, readAll(z, 32 * 1024 * 1024));
			}
		} finally {
			z.close();
		}
		return info.id;
	}

	/** The class must live in the project package (that is the folder the device compiler reads). */
	public static String fixPackage(String src) {
		String pkg = "package " + BehaviorCatalog.PROJECT_PACKAGE + ";";
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;").matcher(src);
		if (m.find()) return src.substring(0, m.start()) + pkg + src.substring(m.end());
		return pkg + "\n\n" + src;
	}

	// ------------------------------------------------------------------------------------------------ helpers

	/** "images/a/b.png" style relative path without .. and without absolute roots. */
	static boolean safeRel(String n) {
		if (n.length() == 0 || n.startsWith("/") && n.length() == 1) return false;
		String t = n.startsWith("/") ? n.substring(1) : n;
		if (t.indexOf('\\') >= 0 || t.indexOf(':') >= 0) return false;
		for (String part : t.split("/")) if (part.equals("..") || part.equals(".") || part.length() == 0) return false;
		return true;
	}

	private static String str(JsonObject j, String k) {
		return j.has(k) && !j.get(k).isJsonNull() ? j.get(k).getAsString() : "";
	}

	private static void write(File f, byte[] data) throws IOException {
		if (f.getParentFile() != null) f.getParentFile().mkdirs();
		FileOutputStream o = new FileOutputStream(f);
		try {
			o.write(data);
		} finally {
			o.close();
		}
	}

	private static byte[] readAll(File f) throws IOException {
		FileInputStream in = new FileInputStream(f);
		try {
			return readAll(in, Integer.MAX_VALUE);
		} finally {
			in.close();
		}
	}

	private static byte[] readAll(InputStream in, int max) throws IOException {
		ByteArrayOutputStream o = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int r, total = 0;
		while ((r = in.read(buf)) > 0) {
			total += r;
			if (total > max) throw new S2bException("big", "A file inside the .s2b is too large");
			o.write(buf, 0, r);
		}
		return o.toByteArray();
	}

	public static Map<String, String> describe(Info i) {
		Map<String, String> m = new LinkedHashMap<String, String>();
		m.put("id", i.id);
		m.put("name", i.name);
		m.put("version", i.version);
		m.put("author", i.author);
		m.put("assets", String.valueOf(i.assets.size()));
		return m;
	}
}
