package com.star4droid.star2d.editor.items;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.template.Utils.Pivot;

/** Sets the origin of an editor element from its Pivot properties (center when the element has none). */
final class EditorPivot {
    private EditorPivot() {}

    static void apply(Actor a, PropertySet<String, Object> ps) {
        String p = "center";
        float x = 0.5f, y = 0.5f;
        if (ps != null) {
            try {
                String s = ps.getString("Pivot");
                if (s != null && !s.isEmpty()) p = s;
                if ("custom".equals(p)) { x = ps.getFloat("Pivot X"); y = ps.getFloat("Pivot Y"); }
            } catch (Throwable ignored) {}
        }
        Pivot.apply(a, p, x, y);
    }
}
