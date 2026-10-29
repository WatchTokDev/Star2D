package com.star4droid.star2d.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/** Recording stand-in of the real Tr (tools/i18n/dump_keys.py puts it first on the class path). */
public final class Tr {
    public static final Map<String, String> SEEN = new LinkedHashMap<String, String>();
    public static String lang() { return "en"; }
    public static String t(String key, String def) {
        if (key != null && def != null && !SEEN.containsKey(key)) SEEN.put(key, def);
        else if (key != null && def == null && !SEEN.containsKey(key)) SEEN.put(key, null);
        return def;
    }
    public static String t(String key) { return key; }
}
