package com.star4droid.star2d.remote;
import com.google.gson.*;
/** Harness stub: only the string helper BlockBuilder uses. */
final class CoreTools {
    static String s(JsonObject a, String k) {
        JsonElement e = a.get(k);
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }
}
