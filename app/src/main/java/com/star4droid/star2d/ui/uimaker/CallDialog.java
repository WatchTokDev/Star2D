package com.star4droid.star2d.ui.uimaker;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.NumberPad;
import com.star4droid.star2d.ui.TextDialog;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Editor for the "call a method" action of a button: scene -> element -> method (public methods of the element and of its
 * script) -> one field per parameter, by type. The result is the JSON spec read by {@code UICalls} at run time.
 */
public final class CallDialog {
    private CallDialog() {}

    public interface OnSpec { void on(String spec); }

    private static final class M {
        String name; String[] types; String[] names;
        String sig() {
            StringBuilder b = new StringBuilder(name).append('(');
            for (int i = 0; i < types.length; i++) b.append(i > 0 ? ", " : "").append(types[i]).append(' ').append(names[i]);
            return b.append(')').toString();
        }
    }

    /** Short text of a spec for the property row ("player.jump(400)"), "" for none. */
    public static String summary(String spec) {
        try {
            JsonObject o = JsonParser.parseString(spec).getAsJsonObject();
            StringBuilder b = new StringBuilder(o.get("item").getAsString()).append('.').append(o.get("method").getAsString()).append('(');
            JsonArray a = o.getAsJsonArray("args");
            for (int i = 0; i < a.size(); i++) b.append(i > 0 ? ", " : "").append(a.get(i).getAsString());
            return b.append(')').toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static void show(final Context c, final String projectPath, String current, final OnSpec done) {
        final Project project = new Project(projectPath);
        final String[] scene = {""}, item = {""}, itemType = {""}, method = {""};
        final ArrayList<String> args = new ArrayList<>();
        final ArrayList<String> types = new ArrayList<>();
        try {
            JsonObject o = JsonParser.parseString(current).getAsJsonObject();
            scene[0] = o.has("scene") ? o.get("scene").getAsString() : "";
            item[0] = o.get("item").getAsString();
            method[0] = o.get("method").getAsString();
            for (int i = 0; i < o.getAsJsonArray("args").size(); i++) args.add(o.getAsJsonArray("args").get(i).getAsString());
            for (int i = 0; i < o.getAsJsonArray("types").size(); i++) types.add(o.getAsJsonArray("types").get(i).getAsString());
        } catch (Throwable ignored) {}

        final LinearLayout body = Ui.column(c);
        int p = Ui.dp(c, 16);
        body.setPadding(p, p / 2, p, 0);
        final AlertDialog dlg = new AlertDialog.Builder(c).setTitle(Tr.t("call_title", "Call a method"))
                .setView(wrap(c, body)).setPositiveButton(Tr.t("save", "Save"), null)
                .setNeutralButton(Tr.t("call_clear", "Clear"), (d, w) -> done.on(""))
                .setNegativeButton(Tr.t("cancel", "Cancel"), null).create();
        final Runnable[] render = {null};
        render[0] = () -> {
            body.removeAllViews();
            body.addView(row(c, Tr.t("call_scene", "Scene"), scene[0].isEmpty() ? "—" : scene[0], v -> {
                final String[] sc = scenes(project);
                if (sc.length == 0) return;
                Dialogs.choose(c, Tr.t("call_scene", "Scene"), sc, i -> {
                    scene[0] = sc[i]; item[0] = ""; method[0] = ""; args.clear(); types.clear(); render[0].run();
                });
            }));
            body.addView(row(c, Tr.t("call_element", "Element"), item[0].isEmpty() ? "—" : item[0], v -> {
                if (scene[0].isEmpty()) return;
                final ArrayList<String> names = new ArrayList<>(), kinds = new ArrayList<>();
                try {
                    for (PropertySet<String, Object> ps : CodeGenerator.loadProperties(project, scene[0])) {
                        String n = ps.getString("name");
                        if (n == null || n.isEmpty() || "LIGHT".equals(ps.getString("TYPE"))) continue;
                        names.add(n); kinds.add(ps.getString("TYPE"));
                    }
                } catch (Throwable ignored) {}
                String[] labels = new String[names.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = names.get(i) + "  (" + kinds.get(i) + ")";
                Dialogs.choose(c, Tr.t("call_element", "Element"), labels, i -> {
                    item[0] = names.get(i); itemType[0] = kinds.get(i); method[0] = ""; args.clear(); types.clear(); render[0].run();
                });
            }));
            body.addView(row(c, Tr.t("call_method", "Method"), method[0].isEmpty() ? "—" : method[0], v -> {
                if (item[0].isEmpty()) return;
                final ArrayList<M> ms = methods(project, scene[0], item[0], itemType[0]);
                String[] labels = new String[ms.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = ms.get(i).sig();
                Dialogs.choose(c, Tr.t("call_method", "Method"), labels, i -> {
                    M m = ms.get(i);
                    method[0] = m.name; args.clear(); types.clear();
                    for (String t : m.types) { args.add("boolean".equals(t) ? "false" : "String".equals(t) ? "" : "0"); types.add(t); }
                    names2.clear(); names2.addAll(Arrays.asList(m.names));
                    render[0].run();
                });
            }));
            for (int i = 0; i < args.size(); i++) {
                final int idx = i;
                final String t = types.get(i);
                String nm = i < names2.size() ? names2.get(i) : "arg" + (i + 1);
                body.addView(row(c, nm + "  (" + t + ")", args.get(i).isEmpty() ? "\"\"" : args.get(i), v -> {
                    String title = nm;
                    if ("boolean".equals(t)) {
                        args.set(idx, String.valueOf(!Boolean.parseBoolean(args.get(idx)))); render[0].run();
                    } else if ("String".equals(t)) {
                        TextDialog.show(c, title, "", args.get(idx), nv -> { args.set(idx, nv); render[0].run(); });
                    } else {
                        NumberPad.show(c, title, args.get(idx), nv -> { args.set(idx, nv); render[0].run(); }, null);
                    }
                }));
            }
        };
        render[0].run();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (item[0].isEmpty() || method[0].isEmpty()) { Ui.toast(c, Tr.t("call_incomplete", "Choose an element and a method")); return; }
            JsonObject o = new JsonObject();
            o.addProperty("scene", scene[0]);
            o.addProperty("item", item[0]);
            o.addProperty("method", method[0]);
            JsonArray a = new JsonArray(), tt = new JsonArray();
            for (String s : args) a.add(s);
            for (String s : types) tt.add(s);
            o.add("args", a);
            o.add("types", tt);
            done.on(o.toString());
            dlg.dismiss();
        });
    }

    private static final ArrayList<String> names2 = new ArrayList<>();

    private static View wrap(Context c, View v) {
        ScrollView s = new ScrollView(c);
        s.addView(v);
        return s;
    }

    private static View row(Context c, String label, String value, View.OnClickListener l) {
        LinearLayout r = Ui.column(c);
        r.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        r.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 8)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(c, 8);
        r.setLayoutParams(lp);
        TextView a = Ui.text(c, label, 12, Ui.TEXT);
        a.setAlpha(0.7f);
        TextView b = Ui.text(c, value, 16, Ui.TEXT);
        b.setGravity(Gravity.START);
        r.addView(a);
        r.addView(b);
        r.setOnClickListener(l);
        return r;
    }

    private static String[] scenes(Project p) {
        ArrayList<String> out = new ArrayList<>();
        try {
            File dir = new File(p.getConfig("scene1")).getParentFile();
            File[] all = dir == null ? null : dir.listFiles();
            if (all != null) {
                Arrays.sort(all);
                for (File f : all) {
                    String n = f.getName();
                    if (f.isFile() && n.endsWith(".json") && !n.startsWith("__") && f.length() > 2) out.add(n.substring(0, n.length() - 5));
                }
            }
        } catch (Throwable ignored) {}
        return out.toArray(new String[0]);
    }

    private static Class<?> classOf(String type) {
        String pkg = "com.star4droid.template.Items.";
        String n;
        switch (type == null ? "" : type) {
            case "BOX": n = "BoxBody"; break;
            case "CIRCLE": n = "CircleItem"; break;
            case "CUSTOM": n = "CustomBody"; break;
            case "TEXT": n = "TextItem"; break;
            case "PROGRESS": n = "ProgressItem"; break;
            case "JOYSTICK": n = "Joystick"; break;
            case "CAMERA": n = "CameraItem"; break;
            case "MAP": n = "MapItem"; break;
            case "PARTICLE": n = "ParticleItem"; break;
            case "GROUP": n = "GroupItem"; break;
            case "UIHOLDER": n = "UIHolder"; break;
            default: return null;
        }
        try { return Class.forName(pkg + n); } catch (Throwable t) { return null; }
    }

    private static String typeName(Class<?> c) {
        if (c == int.class) return "int";
        if (c == long.class) return "long";
        if (c == float.class) return "float";
        if (c == double.class) return "double";
        if (c == boolean.class) return "boolean";
        if (c == String.class) return "String";
        return null;
    }

    private static ArrayList<M> methods(Project project, String scene, String item, String type) {
        LinkedHashMap<String, M> out = new LinkedHashMap<>();
        Class<?> k = classOf(type);
        if (k != null) {
            for (Method m : k.getMethods()) {
                if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
                String dc = m.getDeclaringClass().getName();
                if (!dc.startsWith("com.star4droid.template")) continue;
                Class<?>[] pt = m.getParameterTypes();
                String[] tn = new String[pt.length], an = new String[pt.length];
                boolean ok = true;
                for (int i = 0; i < pt.length; i++) {
                    tn[i] = typeName(pt[i]);
                    an[i] = "arg" + (i + 1);
                    if (tn[i] == null) ok = false;
                }
                if (!ok) continue;
                M mm = new M(); mm.name = m.getName(); mm.types = tn; mm.names = an;
                out.put(mm.sig(), mm);
            }
        }
        // public methods written in the element's script
        try {
            File f = new File(project.getBodyScriptPath(item, scene));
            if (f.isFile()) {
                String src = new String(java.nio.file.Files.readAllBytes(f.toPath()));
                Matcher mt = Pattern.compile("public\\s+(?:void|int|float|double|boolean|String|long)\\s+(\\w+)\\s*\\(([^)]*)\\)").matcher(src);
                while (mt.find()) {
                    String nm = mt.group(1);
                    if (nm.startsWith("body") || nm.equals("onCreate")) continue;
                    String pr = mt.group(2).trim();
                    String[] parts = pr.isEmpty() ? new String[0] : pr.split(",");
                    String[] tn = new String[parts.length], an = new String[parts.length];
                    boolean ok = true;
                    for (int i = 0; i < parts.length; i++) {
                        String[] w = parts[i].trim().split("\\s+");
                        if (w.length < 2) { ok = false; break; }
                        tn[i] = w[w.length - 2]; an[i] = w[w.length - 1];
                        if (!Arrays.asList("int", "long", "float", "double", "boolean", "String").contains(tn[i])) ok = false;
                    }
                    if (!ok) continue;
                    M mm = new M(); mm.name = nm; mm.types = tn; mm.names = an;
                    out.put(mm.sig(), mm);
                }
            }
        } catch (Throwable ignored) {}
        ArrayList<M> list = new ArrayList<>(out.values());
        java.util.Collections.sort(list, (a, b) -> a.sig().compareToIgnoreCase(b.sig()));
        return list;
    }
}
