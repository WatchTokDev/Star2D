package com.star4droid.template.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.template.Items.StageImp;
import com.star4droid.template.Utils.PlayerItem;

import java.lang.reflect.Method;

/**
 * Runs the "call a method" action of a button: spec = {"item":"player","method":"jump","args":["400"],"types":["float"]}.
 * The method is looked for on the element, then on its script. Never throws (an error only goes to the log).
 */
public final class UICalls {
    private UICalls() {}

    public static void run(StageImp stage, String spec) {
        if (stage == null || spec == null || spec.trim().isEmpty()) return;
        try {
            JsonObject o = JsonParser.parseString(spec).getAsJsonObject();
            String item = o.has("item") ? o.get("item").getAsString() : "";
            String method = o.has("method") ? o.get("method").getAsString() : "";
            if (item.isEmpty() || method.isEmpty()) return;
            JsonArray args = o.has("args") ? o.getAsJsonArray("args") : new JsonArray();
            JsonArray types = o.has("types") ? o.getAsJsonArray("types") : new JsonArray();
            PlayerItem it = stage.findItem(item);
            if (it == null) return;
            Object[] targets = {it, it.getScript()};
            for (Object t : targets) {
                if (t != null && invoke(t, method, args, types)) return;
            }
        } catch (Throwable t) {
            try { System.err.println("UICalls: " + t); } catch (Throwable ignored) {}
        }
    }

    private static boolean invoke(Object target, String name, JsonArray args, JsonArray types) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != args.size()) continue;
            Class<?>[] pt = m.getParameterTypes();
            Object[] vals = new Object[pt.length];
            boolean ok = true;
            for (int i = 0; i < pt.length && ok; i++) {
                vals[i] = convert(pt[i], args.get(i).getAsString());
                ok = vals[i] != null;
            }
            if (!ok) continue;
            m.invoke(target, vals);
            return true;
        }
        return false;
    }

    private static Object convert(Class<?> c, String s) {
        try {
            if (c == String.class) return s;
            if (c == float.class || c == Float.class) return Float.parseFloat(s);
            if (c == double.class || c == Double.class) return Double.parseDouble(s);
            if (c == int.class || c == Integer.class) return (int) Double.parseDouble(s);
            if (c == long.class || c == Long.class) return (long) Double.parseDouble(s);
            if (c == boolean.class || c == Boolean.class) return Boolean.parseBoolean(s);
        } catch (Exception e) { return null; }
        return null;
    }
}
